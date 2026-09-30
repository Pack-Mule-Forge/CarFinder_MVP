# Contract: Platform Adapters (`expect`/`actual` boundary)

The interfaces live in `:shared` `commonMain` (`...shared.platform`). The Android actuals live in `:shared`
`androidMain`. The design decisions are covered in [../research.md](../research.md) R1–R6.

## Boundary

```kotlin
expect abstract class PlatformContext
expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters

class PlatformAdapters(
    val location: LocationSource,
    val heading: HeadingSource,
    val activity: ActivitySignalSource,
    val store: ParkingStore,
    val monotonicClock: MonotonicClock,
    val wallClock: WallClock,
    val permissions: PermissionController,
)
```

On Android, `actual typealias PlatformContext = android.content.Context`, and
`actual fun createPlatformAdapters(...)` wires the classes listed below.

## Interfaces

```kotlin
interface LocationSource {
    val readings: Flow<LocationReading>              // hot while started; shared by engine + guidance
    fun setProfile(profile: SamplingProfile)         // re-issues the single platform subscription
    fun start(); fun stop()
}

interface HeadingSource {
    val headings: Flow<HeadingReading?>              // null = unavailable (no sensor / unreliable / stale)
    fun start(); fun stop()                          // started only while guidance UI is STARTED
    fun updateDeclinationFrom(fix: LocationReading)  // true-north correction
}

interface ActivitySignalSource {
    val inVehicleEntered: Flow<Unit>                 // rate hint only — never a lifecycle input
    fun start(); fun stop()
}

interface MonotonicClock { fun elapsedRealtimeMillis(): Long }
interface WallClock      { fun epochMillis(): Long }

// PermissionController: see research R6 for full rationale
enum class Capability { LOCATION_FOREGROUND, LOCATION_BACKGROUND, MOTION_ACTIVITY, NOTIFICATIONS }
enum class RequestMode { EXPLICIT, IMPLICIT_ON_FIRST_USE, NOT_REQUIRED }
enum class CapabilityStatus { GRANTED, DENIED, NOT_DETERMINED, RESTRICTED, UNAVAILABLE }

interface PermissionController {
    val status: StateFlow<Map<Capability, CapabilityStatus>>
    fun requestMode(capability: Capability): RequestMode
    suspend fun request(capabilities: Set<Capability>): Map<Capability, CapabilityStatus>
}
```

**Behavioral contract, applying to every actual**:

| Adapter | MUST |
|---|---|
| `LocationSource` | Keep **one** platform subscription. Map a missing or invalid accuracy or speed to `null`, and never substitute a default value. Stamp every reading with the monotonic fix time. Never throw into the collector: a `SecurityException` from revoked permission becomes a completed-empty flow plus a log entry, and the app must not crash (spec Assumptions). |
| `HeadingSource` | Emit a true-north heading remapped to the display rotation. Emit `null` when the sensor is absent, when accuracy is `UNRELIABLE`, or when there has been no event for `CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS` (FR-031). |
| `ActivitySignalSource` | Emit only on `IN_VEHICLE` ENTER. On any failure, including a denied permission, emit nothing, because the engine does not depend on it. |
| `ParkingStore` | Write atomically. On a missing, corrupted or unknown-schema file, `read()` returns the default FINDING record and never throws. |
| `PermissionController` | Make `request()` prompt only for `EXPLICIT` capabilities that are not already `GRANTED`. Have `status` reflect the real platform state, and re-query it on resume. |

## Android actuals

| Class | Wraps | Test seam |
|---|---|---|
| `FusedLocationSource` | `FusedLocationProviderClient` (profiles are listed in research R2) | `FusedClientPort` interface (`request(LocationRequest, callback)`, `remove(callback)`). Tests use `FakeFusedClientPort`, which pushes Robolectric `Location` objects. |
| `RotationVectorHeadingSource` | `SensorManager` `TYPE_ROTATION_VECTOR`, falling back to `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, plus `GeomagneticField` | `SensorPort` interface and a pure `HeadingMath.azimuth(rotationVector, displayRotation, declination)` |
| `ActivityTransitionSource` | `ActivityRecognitionClient.requestActivityTransitionUpdates` with a `PendingIntent` receiver | `ActivityPort` interface |
| `DataStoreParkingStore` | `DataStoreFactory.create(serializer = ParkingRecordSerializer, corruptionHandler = ReplaceFileCorruptionHandler { default })` | Real DataStore on a JUnit `TemporaryFolder`. Corruption is tested by writing garbage bytes. |
| `AndroidPermissionController` | `ActivityResultContracts.RequestMultiplePermissions` through an `ActivityResultRegistry` | A test `ActivityResultRegistry` that returns scripted grant results. `ContextCompat.checkSelfPermission` is shadowed by Robolectric. |

The Android permission sequence inside a single `request()` call is:

1. `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
2. `POST_NOTIFICATIONS` (33+)
3. `ACTIVITY_RECOGNITION` (29+)
4. `ACCESS_BACKGROUND_LOCATION` (29+, as a separate request)

It stops early if foreground location is denied.

## App-side Android components (in `:app`, not adapters)

| Component | Contract |
|---|---|
| `ParkingDetectionService` | A foreground service with `foregroundServiceType="location"`. `onStartCommand` calls `startForeground(notification)` and then `engine.start()`, and returns `START_STICKY`. The notification text follows `EngineState.lifecycle`. It is started only when `LOCATION_FOREGROUND` is `GRANTED`. |
| `BootReceiver` | On `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED`, it starts the service if `LOCATION_FOREGROUND` and `LOCATION_BACKGROUND` are both `GRANTED` (research R5). |
| `MainActivity` | On create, it calls `permissions.request(all)`, starts the service if the grant succeeded, registers `GuidanceSessionObserver`, and calls `setContent { HomeScreen(uiState, …) }` (see [guidance-ui.md](guidance-ui.md)). On resume, it refreshes the permission status and starts the service if `LOCATION_FOREGROUND` is GRANTED, which is harmless if it is already running. That covers a permission granted later in system Settings. |
| `GuidanceSessionObserver` | A `DefaultLifecycleObserver`. `onStart` calls `engine.setGuidanceVisible(true)` and `heading.start()`, and `onStop` reverses both. |
| `CarFinderApplication` | The app-scoped graph. It exposes `open fun createAdapters(): PlatformAdapters = createPlatformAdapters(this)` so tests can substitute fakes. |

## Fakes (`:shared-testing` module, used by `:shared` `commonTest` and `:app` tests)

The fakes live in `shared-testing/src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/testing/`, because
test source sets are not visible to other modules. `:app` tests swap them in through `TestCarFinderApplication`,
which overrides `CarFinderApplication.createAdapters()`.

- `FakeLocationSource` exposes `emit(reading)` and records `setProfile` calls.
- `FakeHeadingSource` exposes `emit(heading or null)`.
- `FakeActivitySignalSource` exposes `emitInVehicle()`.
- `InMemoryParkingStore` supports `failNextRead` and `seed(record)`.
- `FakeMonotonicClock` exposes `advanceBy(ms)`, and `FakeWallClock` is a fixed or settable clock.
- `FakePermissionController` takes a scripted status map and per-capability `RequestMode`, which lets tests
  model iOS-style `IMPLICIT_ON_FIRST_USE`.

The fakes drive all engine and presenter tests (Constitution III). Adapter classes are tested against the port
fakes listed above.
