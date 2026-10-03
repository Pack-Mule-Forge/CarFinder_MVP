# Contract: Platform Adapters

Every adapter is a `commonMain` interface with an Android implementation class in `androidMain` and
a fake in `:shared-testing` (QR-003, research R2). None is an `expect class`.

```kotlin
interface LocationSource {                       // FR-029
    val readings: SharedFlow<LocationReading>
    fun setIntervalMillis(intervalMillis: Long)  // re-issues the single request — FR-027
    fun start()
    fun stop()
}
interface HeadingSource {                        // FR-033, FR-034, FR-041
    val heading: StateFlow<HeadingReading?>      // null: unreliable or no sensor
    fun start()
    fun stop()
}
interface ActivitySignalSource {                 // FR-028
    val isInVehicle: StateFlow<Boolean>
    fun start()
    fun stop()
}
interface ParkingStore {                         // FR-018, FR-020
    suspend fun read(): PersistedParkingRecord   // never throws; unreadable → default, logged
    suspend fun write(record: PersistedParkingRecord)
}
interface PermissionController {                 // FR-047 to FR-049, FR-056
    val status: StateFlow<PermissionState>
    suspend fun request(capability: Capability): Boolean   // the single suspend function (Principle V)
    fun refresh()                                           // re-read grants from the platform
}
interface MonotonicClock { fun elapsedRealtimeMillis(): Long }
interface WallClock { fun epochMillis(): Long }
interface DiagnosticLog { fun record(event: DiagnosticEvent) }   // FR-020; never coordinates

class PlatformAdapters(location, heading, activity, store, permissions, monotonicClock, wallClock, log)

expect class PlatformContext
expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters
```

## Behavior each Android implementation must have

| Adapter | Behavior | Requirement |
|---|---|---|
| `FusedLocationSource` | One subscription. High accuracy. Minimum update interval equals the requested interval; no batching. `setIntervalMillis` with the current value does nothing. | FR-027, FR-029 |
| `FusedLocationSource` | Accuracy is `null` unless the fix reports it and it is finite and > 0. Speed is `null` unless the fix reports it and it is finite and ≥ 0. Out-of-range coordinates drop the reading. Each reading is stamped with monotonic receipt time. | FR-009, FR-010 |
| `FusedLocationSource` | `start()` without fine-location permission does nothing and does not throw. | FR-049, FR-052 |
| `RotationVectorHeadingSource` | Rotation-vector sensor → remap for display rotation → azimuth → add declination → [0, 360). `null` on unreliable accuracy or no sensor. | FR-033, FR-034, FR-041 |
| `ActivityTransitionSource` | `true` from the in-vehicle enter transition to the exit transition. Without activity-recognition permission it stays `false` and does not throw. | FR-028, FR-049 |
| `DataStoreParkingStore` | Atomic single-record write. Corrupt or unknown-version data is replaced with the default and logged. | FR-018, FR-020 |
| `AndroidPermissionController` | `request` shows exactly one platform prompt for one capability, records that it was requested, and returns whether it is now granted. `FINE_LOCATION` is requested together with coarse location in that one prompt (Android 12+ offers precise or approximate there); it counts as granted only when fine location is granted, so approximate alone returns `false` and reports `DENIED`. Status is `GRANTED` when the platform says so or the capability has no runtime grant on this Android version; otherwise `DENIED` if the "requested" mark is set, else `NOT_REQUESTED`. Marks are kept in a private preferences file. `refresh()` re-reads grants. No call throws when no Activity is attached. The controller is application-scoped and holds the pending request itself. On every `attach` it registers its launcher under one fixed key, so a result for a request launched before the Activity was re-created (rotation) is delivered to the new registration and resumes the pending `request`. `detach` alone does not end a pending request; it ends only when a result arrives or its coroutine is cancelled. | FR-047, FR-048, FR-049, FR-056 |

Each implementation reaches its framework client through a thin internal seam (for example a
`FusedClientPort`) so host tests drive it with a test double.

## App components (`:app`)

| Component | Behavior | Requirement |
|---|---|---|
| `ParkingDetectionService` | Location-type foreground service, sticky. Every start command: check the required permissions (location and notifications); if either is missing, stop self without starting the engine; otherwise post the notification and call `engine.start()`. | FR-049, FR-050, FR-051, FR-052 |
| `DetectionNotification` | Title "Car Finder". Text "Monitoring for parking", or the "Allow all the time" request with a tap action to the app's permission settings when background location is not granted. | FR-051, FR-054 |
| `BootReceiver` | Starts the service only when background location and the required permissions are granted. | FR-053, FR-054 |
| `MainActivity` | On every start calls `refresh()` and then `presenter.runPermissionSequence()` (a no-op while one is running); when the required permissions are granted, starts the service; whenever `presenter.isClosePending` is `true` while it is started (including at a start after being stopped during the closing message), stops the service, calls `finishAndRemoveTask()`, then `presenter.onClosed()`; in `onDestroy`, when finishing and not changing configuration, calls `presenter.cancelPermissionSequence()`; calls `engine.restore()`; collects presenter state; reports visibility. Never calls `engine.start()`. | FR-043, FR-048, FR-049, FR-054, FR-056, QR-013 |

## Fakes (`:shared-testing`)

`FakeLocationSource` (records every requested interval), `FakeHeadingSource`,
`FakeActivitySignalSource`, `InMemoryParkingStore` (can be told to hold unreadable data),
`FakePermissionController` (settable status per capability, a queue of scripted answers per
capability, the list of requests made), `FakeMonotonicClock`, `FakeWallClock`, `RecordingDiagnosticLog`, and
`FakePlatform`, which wires them into a `PlatformAdapters`.
