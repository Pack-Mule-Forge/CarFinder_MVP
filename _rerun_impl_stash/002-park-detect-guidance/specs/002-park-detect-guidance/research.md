# Phase 0 Research: Automatic Park Detection & Guidance

**Feature**: `specs/002-park-detect-guidance` | **Date**: 2026-09-30 | **Plan**: [plan.md](plan.md)

The spec's clarification session already resolved the behavioral questions. This document settles the
technical ones. Each entry records the **Decision**, the **Rationale** for it, and the **Alternatives
considered**.

---

## R1. Module split and the `expect`/`actual` boundary

**Decision**:
- There are two modules. `:shared` uses the `com.android.kotlin.multiplatform.library` plugin, which AGP 9
  requires for KMP, and `:app` is the Android application.
- Adapters are defined as **interfaces in `commonMain`**. The `expect`/`actual` boundary sits at a single
  factory:

  ```kotlin
  expect abstract class PlatformContext                // android: actual typealias = android.content.Context
  expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters
  ```

  `PlatformAdapters` is a plain common class that bundles `LocationSource`, `HeadingSource`,
  `ActivitySignalSource`, `ParkingStore`, `MonotonicClock`, `WallClock` and `PermissionController`.
- The engine (`ParkingEngine`) and the presenter (`HomeScreenPresenter`) also live in `commonMain`.

**Rationale**:
- Constitution V requires both an `expect`/`actual` boundary and fake-driven tests (Principle III).
  `expect class` per service would make fakes awkward, because an `expect class` cannot be replaced by a test
  double. Common interfaces plus one `expect` factory give the platform boundary the constitution mandates and
  keep every consumer testable against fakes.
- Putting orchestration in `commonMain` means iOS reuses the state-machine wiring and not just the math.

**Alternatives considered**:
- `expect class LocationSource` and similar, one per service. Rejected: tests would have to use real actuals or
  an extra interface layer anyway.
- Engine in `:app`. Rejected: iOS would need to re-implement the orchestration, which is the logic most likely to
  diverge.
- Adding `iosArm64`/`iosSimulatorArm64` targets now. Rejected for MVP: they cannot be compiled on the Windows
  development host and add CI surface with no deliverable. Portability is instead protected by R9 rules and by
  review.

## R2. Location sampling strategy, and reuse of one always-on location stream

**Decision**: Use **one** `FusedLocationProviderClient` subscription. The foreground service owns it (R5), and
every consumer shares it. The engine re-issues `requestLocationUpdates` on the same callback whenever the
required **sampling profile** changes. No second high-frequency client is ever started. Guidance and PARKING
sampling both read from this shared `SharedFlow<LocationReading>`.

| Profile | When | Priority | Interval (named constant) |
|---|---|---|---|
| `IDLE_WATCH` | FINDING, PARKED (guidance not visible, recovery window closed) | `PRIORITY_HIGH_ACCURACY` | `TuningConstants.IDLE_WATCH_SAMPLING_INTERVAL` = 20 s |
| `DRIVING` | DRIVING | `PRIORITY_HIGH_ACCURACY` | `TuningConstants.DRIVING_SAMPLING_INTERVAL` = 5 s |
| `PARKING` | PARKING, and PARKED while the recovery window is open and guidance is not visible (R13) | `PRIORITY_HIGH_ACCURACY` | `CarFinderConstants.PARKING_SAMPLING_INTERVAL` (FR-006) |
| `GUIDANCE` | PARKED **and** the guidance UI is at least STARTED | `PRIORITY_HIGH_ACCURACY` | `TuningConstants.GUIDANCE_SAMPLING_INTERVAL` = 1 s |

`minUpdateIntervalMillis` is set to the interval of the profile, and `maxUpdateDelayMillis` is set to 0 (no
batching), so that PARKING readings arrive one per interval as FR-006 requires.

The **Activity Recognition Transition API** (`IN_VEHICLE` ENTER) is used only as a **rate hint**. In
`IDLE_WATCH` it switches the profile to `DRIVING` early, which shortens the time it takes to detect drive-away
and the first drive. It never changes lifecycle state and never pauses sampling. The clarification "not gated by
motion" still holds, because every lifecycle transition is decided solely by the filtered location speed.

**Rationale**:
- FR-033 requires continuous sampling in every state, and FR-003/FR-015 require a usable **speed** reading in
  PARKED. A `Location.speed` comes from GNSS Doppler, which is far more reliable than a speed derived from
  positions. Balanced-power (Wi-Fi/cell) fixes often lack `hasSpeed()` or report noisy values. A noisy value
  here would be dangerous, because a false drive-away deletes the Parked Location. For that reason every profile
  uses high accuracy, and battery is managed through the **interval** instead of the accuracy class.
- A 20 s idle interval with a median of 3 bounds drive-away detection at about 60 s. The activity hint usually
  cuts that to about 15 s.
- The 1 s guidance profile satisfies SC-008 (update within 1 s) and keeps FR-034 staleness (30 s) far from
  triggering in normal use. It is active only while the screen is visible.
- A single client avoids duplicate GNSS sessions. This is the concrete answer to the brief's question about
  reusing an always-on location service.

**Alternatives considered**:
- Separate high-rate client for PARKING or guidance. Rejected: it duplicates GNSS work, and the two
  subscriptions race each other.
- `PRIORITY_BALANCED_POWER_ACCURACY` for idle-watch. Rejected: speed is unreliable, and there is a false
  drive-away risk.
- Gating sampling on Activity Recognition, so that no GPS runs until `IN_VEHICLE`. Rejected: this contradicts
  the clarification that detection is "not gated by motion". Missed or late activity events would also silently
  break drive-away detection.
- Raw `LocationManager` GPS provider. Rejected: it gives no fused power management and needs more code for the
  same accuracy.
- `setMinUpdateDistanceMeters` in idle-watch. Deferred as a tuning option. It suppresses updates while
  stationary, which is safe for speed detection, but it makes battery behavior harder to reason about during
  initial validation.

## R3. Heading source

**Decision**:
- Read `Sensor.TYPE_ROTATION_VECTOR`, falling back to `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, at
  `SENSOR_DELAY_UI`.
- Convert the reading to an azimuth with `SensorManager.getRotationMatrixFromVector`, then
  `remapCoordinateSystem` for the current display rotation, then `getOrientation`.
- Add the magnetic declination from `android.hardware.GeomagneticField` for the latest fix, which gives a
  **true-north** heading. The bearing to the car is also true north.
- The heading is **unavailable** in three cases: neither sensor exists, the last accuracy callback reported
  `SENSOR_STATUS_UNRELIABLE`, or no sensor event has arrived within
  `CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS` = 2 s (FR-030, FR-031).
- The heading adapter registers only while the guidance UI is STARTED. The pure conversion lives in a
  `HeadingMath` object so it can be tested without a sensor.

**Rationale**:
- Rotation vector is magnetometer-based and gyro-smoothed, which removes the jitter that a raw
  accelerometer-plus-magnetometer pipeline shows. It is still the "device's magnetic compass" that the brief
  asks for.
- Declination correction matters because `bearing_to_car` from GeoMath is true north, and uncorrected magnetic
  headings would misalign the cone by up to about 20° in parts of North America.
- Remapping for display rotation makes FR-021's `device_heading` mean "the direction the top of the screen
  points". That is the property FR-024 depends on for orientation-independent rendering.

**Alternatives considered**:
- `TYPE_ACCELEROMETER` plus `TYPE_MAGNETIC_FIELD` directly. Kept only as a documented future fallback: it is
  noisier.
- Using the GNSS course (`Location.bearing`). Rejected: it is meaningless while walking slowly or standing.

## R4. Persistence mechanism

**Decision**:
- **Jetpack DataStore** (typed `DataStore<PersistedParkingRecord>`) on Android, stored in
  `filesDir/datastore/parking_state.json`.
- The serializer uses `kotlinx.serialization` JSON. The schema class lives in `commonMain`.
- A `ReplaceFileCorruptionHandler` returns the FINDING default record, which satisfies FR-018 for corrupted
  storage.
- Each transition writes the whole record in one `updateData` call, so state and location can never disagree on
  disk.
- `ParkingStore` exposes `suspend fun read(): PersistedParkingRecord` and
  `suspend fun write(record: PersistedParkingRecord)`.

**Rationale**:
- The data is a single small record that is written a few times per trip. DataStore gives atomic, transactional
  file replacement, a coroutine API, and a first-class corruption hook, which is the exact FR-018 fallback
  path. It survives process death and reboot (FR-014).
- A JSON schema in `commonMain` means the iOS actual can reuse the same serializer with DataStore's KMP/okio
  storage or with a file. `schemaVersion` allows migrations.
- **History later**: parking history is naturally a table (Room KMP or SQLDelight) behind a *new*
  `ParkingHistoryRepository`. The single-record store stays the source of "current" state, and the engine
  appends to history on the PARKING→PARKED transition. Nothing in this design has to change to add that.

**Alternatives considered**:
- Room or SQLDelight now. Rejected: a schema, DAO and migrations for one row is over-engineered today. It remains
  the planned path for history.
- SharedPreferences. Rejected: two keys cannot be written atomically, and there is no corruption hook.
- Proto DataStore, which the existing catalog already pins. Rejected: the protobuf Gradle plugin needs AGP-9
  workarounds (already visible in the catalog comments), and a `.proto` schema cannot be shared with iOS as
  easily as a `@Serializable` class. The unused protobuf entries are removed from the catalog.

## R5. Background execution, reboot restart, and required permissions

**Decision**:
- `ParkingDetectionService` is a foreground service with `android:foregroundServiceType="location"`. It is
  started from `MainActivity` once location permission is granted, returns `START_STICKY`, and shows a
  persistent low-importance notification (`DetectionNotification`) whose text reflects the lifecycle state.
- `BootReceiver` (`BOOT_COMPLETED`, plus `MY_PACKAGE_REPLACED`) restarts the service if the permissions are still
  granted.
- Manifest permissions:
  - `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
  - `ACCESS_BACKGROUND_LOCATION`
  - `ACTIVITY_RECOGNITION` (API 29+)
  - `POST_NOTIFICATIONS` (API 33+)
  - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION` (API 34+)
  - `RECEIVE_BOOT_COMPLETED`
- The app requests "Allow all the time" background location as a second step after foreground location. This is
  required on API 30+.

**Rationale**:
- FR-033 requires always-on detection with a persistent notification that restarts after reboot. A
  location-type foreground service is the only sanctioned mechanism for continuous location on modern Android.
- A location foreground service started from the background (boot, or a sticky restart after process death)
  does not get "while-in-use" location access unless the app holds `ACCESS_BACKGROUND_LOCATION`. Requesting it is
  therefore what makes the reboot requirement actually work. Two exemption lists are easy to conflate here:
  - `BOOT_COMPLETED` **is** an exemption from the background-*start* restrictions, and `location` is **not** on
    Android 15's list of types a boot receiver may not launch. The receiver may therefore start the service.
  - The separate *while-in-use permission* check (API 34+) lists no boot exemption. A location foreground
    service created from the background throws `SecurityException` unless the app holds
    `ACCESS_BACKGROUND_LOCATION`.

  Sources: developer.android.com, "Restrictions on starting a foreground service from the background", and
  "Android 15 behavior changes", both checked 2026-09-30.

  With foreground-only permission, the spec's Edge Case applies: the notification explains the need for
  "Allow all the time", and detection resumes on the next app open. **Confirm on a physical API 34+ device**
  in quickstart step V6, and record the gap against FR-033 if the platform behaves differently.
- Doze and app standby do not suspend a running foreground service's location updates.

**Alternatives considered**:
- WorkManager periodic work. Rejected: the 15-minute minimum period cannot provide 5 s PARKING sampling or timely
  drive-away detection.
- Geofence exit on the Parked Location to detect drive-away. This is attractive for battery, but it is rejected
  as the *sole* mechanism: geofence exit does not tell walking apart from driving, and FR-015 is defined by speed.
  It is noted as a future optimization that could lengthen the idle-watch interval.
- Starting the service only after the first drive. Rejected: FR-033 requires it in every state, FINDING
  included.

## R6. `PermissionController` shape (Android now, iOS later)

**Decision**: The common API is:

```kotlin
enum class Capability { LOCATION_FOREGROUND, LOCATION_BACKGROUND, MOTION_ACTIVITY, NOTIFICATIONS }
enum class RequestMode { EXPLICIT, IMPLICIT_ON_FIRST_USE, NOT_REQUIRED }
enum class CapabilityStatus { GRANTED, DENIED, NOT_DETERMINED, RESTRICTED, UNAVAILABLE }

interface PermissionController {
    val status: StateFlow<Map<Capability, CapabilityStatus>>
    fun requestMode(capability: Capability): RequestMode
    suspend fun request(capabilities: Set<Capability>): Map<Capability, CapabilityStatus>
}
```

- `request()` prompts only for capabilities whose mode is `EXPLICIT`. For all other capabilities it returns the
  current status without prompting.
- **Android** (`AndroidPermissionController`):
  - Every capability has mode `EXPLICIT`, except `NOTIFICATIONS` below API 33, `MOTION_ACTIVITY` below API 29,
    and `LOCATION_BACKGROUND` below API 29, which are `NOT_REQUIRED` and `GRANTED`.
  - `request()` bridges `ActivityResultContracts.RequestMultiplePermissions` into a suspend call.
  - It sequences the prompts as foreground location, then notifications, then activity recognition, then
    background location. Background location must be a separate request on API 30+.
  - It is bound to the current `ComponentActivity` through its `ActivityResultRegistry`.
- **iOS (planned, not built)**:
  - `LOCATION_*` maps to `CLLocationManager` authorization, with mode `EXPLICIT` via
    `requestWhenInUse` / `requestAlwaysAuthorization`.
  - `MOTION_ACTIVITY` has mode `IMPLICIT_ON_FIRST_USE`. `CMMotionActivityManager` and `CMPedometer` are gated by
    `NSMotionUsageDescription` and prompt implicitly on first query. `status` mirrors
    `CMMotionActivityManager.authorizationStatus()`.
  - Raw accelerometer and gyroscope data through `CMMotionManager` needs no permission. If an accelerometer
    capability is added later it is `NOT_REQUIRED`.

**Rationale**: The design gives one suspend function per platform, as Constitution V requires. Observable status
plus an honest `RequestMode` stops iOS motion from pretending to have an explicit request step it does not have.

**Alternatives considered**:
- `suspend fun requestMotion(): Boolean` on both platforms. Rejected: on iOS this would have to fire a dummy
  activity query just to trigger the prompt, which is exactly the false symmetry the constitution forbids.
- A third-party KMP permissions library. Rejected: it adds a dependency whose model assumes symmetric explicit
  requests.

## R7. Engine concurrency and process model

**Decision**:
- `ParkingEngine` is a single-consumer actor: one coroutine reads a `Channel<EngineInput>`. Inputs are location
  readings, activity hints, `Restore`, and `GuidanceVisibility(Boolean)`.
- For each input it runs the pure `ParkingStateMachine.reduce(snapshot, event)`, persists the record when the
  lifecycle or location changed, and updates `StateFlow<EngineState>` and the requested `SamplingProfile`.
- It persists **before** publishing, so the UI never shows a PARKED state that has not been saved.
- One engine instance per process is held by `CarFinderApplication`, and the Activity and the service resolve to
  that same instance (verified by `EngineSingletonTest`). Only the service starts the engine; the Activity is a
  read-only observer that calls `restore()` to show the persisted state and never starts sampling, subscriptions
  or the actor (verified by `MainActivityReadOnlyObserverTest` and `EngineStartOwnershipScanTest`). An early
  implementation had the Activity call `start()`; it was corrected during implementation (ledger RR1-010).
- On process start, `Restore` loads the record. If the record says PARKED but has no location, the engine falls
  back to FINDING and rewrites the record (FR-018). The speed filter and convergence window always start empty
  after a restore.

**Rationale**: Fused callbacks, activity broadcasts and UI visibility changes arrive on different threads. An
actor removes races without locks, and the pure reducer stays trivially unit-testable. Keeping the filter and
window in memory only is safe: at worst a restart delays a transition by N samples.

**Alternatives considered**:
- `Mutex` around shared mutable state. It works, but interleavings are harder to test.
- Persisting the speed window. Rejected: it adds write churn for no user-visible benefit.

## R8. Filtering, convergence, and geo math specifics

**Decision**:
- **Speed units**: fixes carry m/s, and the engine converts with `CarFinderConstants.METERS_PER_SECOND_TO_MPH`.
  Thresholds stay in mph, as the spec states them.
- **Speed filter**:
  - Only readings with `hasSpeed()` enter the median window.
  - Until the window holds `SPEED_FILTER_WINDOW_SIZE` samples, the filtered speed is `null`, and no speed
    transition fires.
  - The window is **not** reset on transitions, so drive-away detection is not delayed.
  - For an even window size, the median is the mean of the two middle values.
- **Convergence** (FR-007):
  - The window holds the most recent `CONVERGENCE_SAMPLE_COUNT` readings that have an accuracy radius, taken
    in PARKING.
  - It is converged iff `max over pairs haversine(a, b) ≤ CONVERGENCE_RADIUS_METERS`.
  - The window is cleared on PARKING entry and on exit. While PARKED it is reused for recovery (R13).
- **Centroid**: the arithmetic mean of latitudes and longitudes. This is exact enough at ≤ 10 m separation.
  Wrap across the antimeridian is handled by averaging longitude deltas relative to the first reading.
- **Distance / bearing**: haversine distance, and the forward-azimuth initial bearing normalized to [0, 360).
  The spec assumes these reuse the prior Car Finder implementation. The prior code is not present in this
  working tree, so the standard formulas are written fresh with the same signatures and pinned by tests against
  published reference pairs.
- **Half-angle**: `atan2(uncertainty, distance)`. When distance is 0 this yields 90°, which matches the spec
  edge case without a special branch.

**Rationale**: Each rule closes a gap that FR-032 and FR-007 leave to implementation, and none of them changes
spec behavior. Using `atan2` removes the division-by-zero risk structurally.

## R9. Keeping `commonMain` portable

**Decision**:
- Use no `java.*`, `android.*`, or `String.format` in `commonMain`.
- Distance text formatting is implemented with integer arithmetic: whole feet, and miles rounded half-up to two
  decimals.
- Time comes from the injected `MonotonicClock` (`SystemClock.elapsedRealtime` on Android) for fix currency, and
  from `WallClock` for `capturedAtEpochMillis`.
- Fix age uses the fix's own `elapsedRealtimeMillis` (from `Location.elapsedRealtimeNanos`), not its UTC time.

**Rationale**: Portability is only proven once an iOS target compiles, so these rules keep that future step
cheap. A monotonic clock makes FR-034 immune to wall-clock changes.

## R10. Compose rendering and UI testing approach

**Decision**:
- `GuidanceDisplay` draws the cone as a circular sector on a `Canvas`, with the person and car icons as
  `Icon`s placed at shared-computed anchors.
- `ConeGeometry` is computed in shared, in **normalized units**: the origin is the screen center and 1.0 equals
  the minimum display dimension. The Composable multiplies by `min(maxWidth, maxHeight)` and translates to the
  center. That transform is the only arithmetic in the UI.
- The pivot is the screen center. The cone runs from the apex (person) at `-L/2` to the far end (car) at `+L/2`
  along `display_bearing`. `L` is `TuningConstants.CONE_LENGTH_FRACTION` = 0.65. The sector's farthest point from center is
  `sqrt(1.25 - cos(halfAngle)) x L`, about 0.737L at the 45° arrival angle, so L must be at most 0.678 for the drawing
  to fit in the minimum-dimension square. (Changed from 0.8 during implementation, when a test showed 0.8 clips wide cones.) That gives identical proportions in portrait and landscape
  (FR-024). The distance text sits unrotated at the center. No centerline is drawn (FR-023).
- **UI tests** run as Robolectric host tests with `createComposeRule()`:
  - Custom `SemanticsPropertyKey`s (`ConeHalfAngleDegrees`, `ConeDisplayBearingDegrees`) and test tags expose
    what was rendered.
  - Tests set `HomeScreenState` directly and assert on semantics: text nodes, `assertDoesNotExist`, and
    property values.
  - Rotation is covered by rendering the same state at portrait and landscape sizes and asserting identical
    normalized geometry.
- Smoothness (FR-027) comes from three things: stable/immutable state classes, drawing inside `drawBehind` /
  `Canvas` so updates invalidate only the draw phase, and no allocation in the draw lambda.
- **FR-027 verification** has two automated layers:
  - A host recomposition-count test checks that a guidance update recomposes only the guidance display.
  - A `:benchmark` Macrobenchmark (`FrameTimingMetric`) runs a 60 s synthetic guidance session on a connected
    device and asserts that at least 95% of frames render within the frame budget. It is automated but outside
    CI at MVP.
- **State hoisting boundary (Constitution IV)**: no Composable collects flows or runs side effects.
  `MainActivity` copies `presenter.state` into an Activity-held Compose `State` inside
  `repeatOnLifecycle(STARTED)`. A `GuidanceSessionObserver` (`DefaultLifecycleObserver`) toggles guidance
  visibility and the heading sensor. `setContent` only reads the state and calls the pure `HomeScreen`.

**Rationale**:
- Robolectric keeps UI tests in the fast JVM loop, so they run with `gradlew test` and need no emulator.
- Semantics properties let tests verify geometry numerically instead of by screenshot.
- Normalized geometry is what keeps the Composable free of geometry (Constitution IV).

**Alternatives considered**:
- Instrumented `androidTest` only. Rejected as the primary tier because it is slow and needs a device. The same
  test classes can be moved to `androidTest` if Robolectric Compose fidelity proves insufficient.
- Screenshot testing. Deferred.

## R11. Traceability tooling

**Decision**: Extend the existing `tools/traceability/Get-TraceabilityReport.ps1`, which was inherited with the
baseline tree and targets `specs/001` with FR-only parsing. The changes are:

1. The default `-SpecPath` becomes `specs/002-park-detect-guidance/spec.md`, and the default output becomes
   `specs/002-park-detect-guidance/traceability.md`.
2. It parses both `**FR-###**` and `**QR-###**` IDs.
3. It recognizes KDoc `@requirement <ID>[, <ID>…]` tags in `*.kt`.
4. It classifies a tag as a **test** when the file path contains `/src/*Test/` or `/src/test/`, and otherwise as
   **implementation**.
5. It reports per-ID implementing `file:declaration`, verifying `file:test`, untraced IDs, and orphaned tags.
6. It supports `-FailOnGaps`.
7. It gets Pester tests with fixture trees.

The full grammar and output format are in [contracts/traceability.md](contracts/traceability.md).

**Rationale**:
- KDoc tags are comments, so the tool is fully decoupled from app runtime code, with no annotation class and no
  dependency. The constitution's own example uses the KDoc form.
- Classifying by path means one tag grammar serves both code and tests.
- PowerShell matches the development host and the existing script.

**Alternatives considered**:
- A Kotlin `@Requirement` annotation with SOURCE retention. Rejected: it adds a runtime-module type for a
  development-time concern.
- A KSP processor. Rejected: it is heavier than an MVP script and couples the report to the Gradle build.

## R12. Plan-level interpretations that touch spec wording

These started as decisions the spec left open. After the first `/speckit.analyze` pass, (a), (b), (c) and (f)
were written into spec.md as fixes at the source: FR-012, FR-025/FR-028, FR-030/FR-031, and the Assumptions and
Edge Cases. (d) and (e) remain plan-level details that do not change spec behavior.

| # | Topic | Decision | Spec anchor |
|---|---|---|---|
| a | **Parked Location accuracy radius**: the spec says "centroid … together with its accuracy" but gives no formula | `accuracy = max_i(accuracy_i + distance(centroid, reading_i))`. This is the smallest circle around the centroid that contains every reading's own accuracy circle. It is conservative, never drops uncertainty, and is consistent with FR-019's "without dropping or approximating". | FR-012, FR-019 |
| b | **Arrival view content** | The cone and both icons are replaced by "You have arrived" and the prompt. The distance text is **hidden** in the arrival view, because it is within the uncertainty and would imply more walking, which is Story 3's rationale. | FR-025, FR-028 |
| c | **"Heading unavailable" definition** | No rotation-vector sensor, or accuracy `UNRELIABLE`, or no event within the heading-staleness timeout (`CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS`, 2 s). | FR-030, FR-031 |
| d | **Readings without speed** | They are excluded from the speed filter but still used for position and convergence, if they have accuracy. | FR-032 |
| e | **Background sampling rate outside PARKING** (the spec defers it to planning) | The R2 profile table. | Assumptions |
| f | **Permission denied** | No extra screen. The default-view rules still apply (FINDING → "Parked location unavailable."), and the service does not start. | Assumptions, FR-016 |

## R13. Bounded re-convergence recovery while PARKED

Added 2026-10-02 for FR-035, after field check V8 reproduced CR-18 from the 001 ledger.

**Decision**:
- **Where**: in the pure reducer. `ParkingStateMachine` handles a reading in PARKED by feeding the existing
  `ConvergenceWindow`, which PARKED otherwise leaves empty. No new lifecycle state is added, so FR-001, FR-009
  and the default-view rules are untouched.
- **The bound**: `ParkedLocation.isWithinRecoveryWindow(now)`, which is
  `0 ≤ now − capturedAtEpochMillis ≤ CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS`. `now` is the wall-clock
  time the event already carries.
- **Anchor**: a correction builds the new location with the **original** `capturedAtEpochMillis`. The window is
  therefore measured from the PARKED declaration, cannot be extended by a chain of corrections, and needs no new
  persisted field or schema change: it survives a process restart through the existing record.
- **Fail closed**: a wall clock earlier than the declaration counts as outside the window. When the window
  closes, partial recovery readings are discarded.
- **Thinning**: a reading is used for recovery only if it is at least
  `TuningConstants.RECOVERY_MIN_SAMPLE_SPACING_MILLIS` (4/5 of the parking-sampling interval) after the last one
  in the window, by the monotonic fix time.
- **Sampling**: while the window is open and guidance is not visible, the engine requests the `PARKING` profile.
  The engine notices the window closing on the next input, which is at most one parking-sampling interval late.
- **Value**: 120 s. The CR-18 decision names the constant but the requirements document that would give its
  value (`car-finder-mvp-requirements-v4.md`) is not in this repository, so this is a plan-level choice to
  confirm in the field.
- **Silent**: `from == to == PARKED`, so nothing is emitted on `ParkingEngine.transitions`. The record is
  persisted because the location changed, and the guidance display follows the new location.

**Rationale**:
- The time bound is the safety property. A driver who parks, walks away and settles produces the same pattern
  as a correction, and an unbounded recovery would replace a correct location with a confidently wrong one.
  Keeping the declaration time on a corrected location makes the bound hold by construction.
- Thinning is needed because pairwise convergence does not look at time. At the 1 s guidance rate three
  consecutive readings span under 3 m at walking pace and would converge on every step. The spacing is slightly
  below the parking-sampling interval because Fused delivers no faster than `minUpdateIntervalMillis` but fix
  timestamps jitter, and an exact-interval rule would drop every second reading.
- Without the `PARKING` profile, the 20 s idle rate would need the car to sit at the real spot for 40 s before
  the driver walks off, and recovery would rarely complete.
- 120 s covers creeping through an ordinary lot. A longer window widens the remaining exposure: inside the
  window, a user who stands still for about 10 s within sight of the car (a pay station) moves the location
  there.

**Limits, stated plainly**:
- This does not prevent the early PARKED declaration. Between the brief stop and the correction the app shows
  PARKED at the brief-stop position.
- A stoplight in ordinary driving (the V8 observation) is cleared by drive-away when speed next passes the
  driving threshold, as before. Recovery only matters when the driver never reaches that speed again.
- A real spot reached after the window closes is not corrected.

**Alternatives considered**:
- Lowering `DRIVING_SPEED_THRESHOLD_MPH`. Rejected by the owner: it shrinks the dead zone that protects against
  stop-and-go traffic, cycling and jogging.
- Unbounded recovery. Rejected: the walk-away overwrite described above.
- Gating recovery on `ActivitySignalSource` staying in-vehicle. Deferred to production by the owner decision;
  the engine's recovery path does not read the activity signal.
- A separate persisted `parkedDeclaredAt` field. Rejected: `capturedAtEpochMillis` already means "time of the
  PARKED transition", and reusing it avoids a schema change.
- Restarting the window on each correction. Rejected: a driver sitting in the car would keep it open
  indefinitely, which is the unbounded case again.
- Correcting only when the new centroid is some minimum distance from the old one. Not needed: a
  re-convergence at the same place replaces the location with an equivalent one.
