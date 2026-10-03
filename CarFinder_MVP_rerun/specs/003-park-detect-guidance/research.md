# Research: Automatic Park Detection & Guidance (Run 2)

**Feature**: `specs/003-park-detect-guidance` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Date**: 2026-10-02

Each section is a decision the spec leaves to planning. None changes spec behavior. Where a decision
needs a number or formula the spec does not contain, it is listed in
[Values this plan introduces](#values-this-plan-introduces), with where the spec now states it
(QR-007).

Starting point: the repository holds the Gradle wrapper, root build files and version catalog, and
no module source. `settings.gradle.kts` still includes `:app`, `:shared`, `:shared-testing` and
`:benchmark`, none of which exist on disk.

## R1. Modules

**Decision**: Three Gradle modules and one tools directory.

| Module | Holds |
|---|---|
| `:shared` | Kotlin Multiplatform. `commonMain`: all domain logic, the engine, the presenter, adapter interfaces. `androidMain`: Android adapter implementations. |
| `:shared-testing` | Kotlin Multiplatform, test-only. Fakes for every adapter interface, reading builders, the scripted-replay harness. |
| `:app` | Android application: Activity, foreground service, boot receiver, notification, Compose UI. |
| `tools/traceability/` | PowerShell report script and its Pester tests. Not a Gradle module, not shipped. |

Remove `include(":benchmark")` from `settings.gradle.kts`.

**Rationale**: `:shared-testing` exists because test source sets are not visible across modules, and
both `:shared` tests and `:app` tests need the same fakes (QR-003). The spec has no frame-time
number, so no benchmark module is justified (see R11).

**Alternatives considered**: Fakes inside `:shared` `commonTest` (not visible to `:app`). Fakes in
`:shared` `commonMain` (ships test code). A `:benchmark` module (nothing in the spec for it to
assert).

## R2. Adapter boundary

**Decision**: Every platform service is a `commonMain` **interface** with an Android implementation
class in `androidMain`: `LocationSource`, `HeadingSource`, `ActivitySignalSource`, `ParkingStore`,
`PermissionController`, `MonotonicClock`, `WallClock`, `DiagnosticLog`. The only `expect`/`actual`
declaration is one stateless factory function,
`expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters`, with
`expect class PlatformContext` satisfied on Android by `actual typealias PlatformContext = Context`.
No test replaces the factory; tests construct `PlatformAdapters` from fakes directly.

**Rationale**: An `expect class` compiles to a final class and cannot be faked, which QR-003
forbids for adapters. Constitution Principle V requires an `expect`/`actual` boundary; the factory
is that boundary. `PlatformContext` is an opaque handle that no test substitutes, so it is not an
adapter in QR-003's sense.

**Alternatives considered**: `expect class` adapters (cannot be faked). No `expect`/`actual` at all,
with `:app` building the adapters (leaves Principle V unmet on its literal wording).

## R3. Location source and sampling

**Decision**: One Fused Location Provider subscription owned by the foreground service, shared by
detection and guidance (FR-029). All requests use high accuracy. The request is re-issued whenever
the required interval changes, with the minimum update interval equal to the interval and no
batching.

The interval is chosen by a pure `commonMain` function, `SamplingPolicy.intervalFor(lifecycle,
isRecoveryOpen, isGuidanceVisible, isInVehicle)`, which is FR-027's table and FR-028's hint and
nothing else. The engine calls it after every input and re-issues the request only when the result
changes.

The recovery window closing changes the interval (FR-027 row 2 to row 3 or 4). The engine does not
wait for the next reading to notice: on entering PARKED, on restore, and after each correction it
schedules one timer for the moment the window closes, and re-evaluates the interval when it fires.

The in-vehicle hint is true from the platform's "entered vehicle" transition until its "exited
vehicle" transition. It is an input to `SamplingPolicy` only.

**Rationale**: Speed from satellite Doppler is far more reliable than speed inferred from positions
or from network fixes, and a wrong speed can delete the Parked Location, so accuracy class is not
where battery is saved; the interval is. A pure policy function makes SC-012 ("the interval in
effect matches FR-027 in 100% of combinations") a table-driven unit test. FR-027 says "at all
times", so a timer is needed for the window end.

**Alternatives considered**: A second high-rate subscription for guidance (FR-029 forbids it). A
fixed rate (FR-027 forbids it). Noticing the window end on the next reading (up to one interval
late, and never if readings stop). Gating sampling on activity recognition (FR-028: the hint never
pauses sampling).

## R4. Reading validation at the adapter

**Decision**: The Android location adapter maps each platform fix to `LocationReading` and decides
field presence explicitly: accuracy is `null` unless the platform says the fix has accuracy and the
value is finite and greater than zero; speed is `null` unless the platform says the fix has speed
and the value is finite and not negative. Latitude or longitude outside their valid ranges drops the
reading and logs a count, without coordinates. Each reading is stamped with the monotonic time at
which the adapter received it.

**Rationale**: The platform returns `0.0` for an absent field. FR-009 and FR-010 forbid reading that
as "stationary" or "perfectly accurate", and the adapter is the only place that can tell absent from
zero. This is input validation at the system boundary.

## R5. Speed filter, convergence and recovery

**Decision**:
- **Speed filter**: holds the last speed-filter-window-size speeds from readings that carry one.
  Reports no smoothed speed until full (FR-008). Not cleared on lifecycle transitions; empty after a
  process start.
- **Per-reading gate**: a speed-based transition is evaluated only on a reading that itself carries
  a speed, so a reading without speed never fires one from an older smoothed value (FR-009).
- **Order within one reading**: drive check first (FR-004, FR-026), then DRIVING → PARKING, then
  PARKING convergence, then PARKED recovery.
- **PARKING window**: emptied on entering PARKING. The reading that causes the entry is not added.
  Readings without accuracy are skipped and do not break "consecutive".
- **Recovery** (FR-021 to FR-025): in PARKED, if `declaredAt ≤ now ≤ declaredAt + window` on the
  wall clock, a reading with accuracy is accepted when the recovery window is empty or the reading
  was received at least one parking-sampling interval after the last accepted one, on the monotonic
  clock. When the accepted readings converge and the centroid is more than the convergence radius
  from the stored location, the location is replaced and keeps `declaredAt`, and the window is
  emptied. When they converge within the radius, nothing is stored and the window keeps sliding.
  When the window is closed, the recovery readings are discarded.
- **Window clock**: the wall clock, because the declaration time must survive a restart (FR-018) and
  the monotonic clock does not. A wall clock earlier than `declaredAt` counts as closed.
- **Thinning clock**: the monotonic receipt time, which a wall-clock change cannot disturb.

**Rationale**: Keeping `declaredAt` on a corrected location makes FR-023 hold by construction with
no extra stored field. Failing closed on a backwards clock keeps a premature location rather than
risking a good one.

**Risk to verify on a device**: FR-025 says "at least one parking-sampling interval apart". If the
platform delivers a reading a few milliseconds early, that reading is thinned and recovery runs at
half pace. The spec has no tolerance and this plan does not invent one (QR-007). Quickstart check V9
measures the delivered spacing; if early deliveries occur, raise it against the spec.

**Alternatives considered**: Resetting the window on each correction (FR-023 forbids it). A separate
stored "window opened at" field (redundant with `declaredAt`). The monotonic clock for the window
(lost on reboot).

## R6. Heading

**Decision**: The Android heading adapter uses the fused rotation-vector sensor (FR-034), remaps the
rotation matrix for the current display rotation (FR-033), takes the azimuth, and adds the magnetic
declination for the latest fix position to get true north (FR-033), normalized to [0, 360). It emits
`null` while the sensor reports unreliable accuracy and when the device has no such sensor (FR-041).
The sensor is registered only while guidance is visible.

Staleness is not the adapter's job. `HeadingReading` carries its monotonic receipt time, and the
presenter treats a heading older than the heading-staleness timeout as absent (FR-041), re-checked
on a tick (R9).

The heading math (remap choice per rotation, declination sum, wrap-around) is a pure function in
`androidMain`, unit-tested for all four display rotations.

**Rationale**: The rotation vector is gyroscope-smoothed, which is what FR-034 asks for. Keeping
staleness in shared code means one clock-driven rule covers fix and heading alike and is tested once.

**Alternatives considered**: Raw magnetometer plus accelerometer (jitter, CR-17). The deprecated
orientation sensor. Staleness inside the adapter (platform-specific timer, harder to test).

## R7. Persistence

**Decision**: One typed DataStore file holding a single `PersistedParkingRecord` (schema version,
lifecycle state, optional Parked Location with `declaredAtEpochMillis`), serialized as JSON with
`kotlinx.serialization`. The record type and its normalization rules live in `commonMain`; the
DataStore and serializer live in `androidMain`. State and location are written together in one
atomic update on every change (FR-018, FR-019).

Unreadable data (FR-020): the DataStore corruption handler replaces the file with the default record
(FINDING, no location) and calls `DiagnosticLog`. An unknown schema version is treated the same way.
A readable but inconsistent record (PARKED without a location, or a location without PARKED) is
normalized by shared code, logged, and written back.

`DiagnosticLog` is an adapter interface so "MUST log" is asserted with a fake. Log entries never
contain coordinates.

**Rationale**: A single small record read at start and written on a handful of transitions fits
DataStore, which gives atomic replace and a corruption hook. A future parking-history table is a
separate store behind its own interface and does not disturb this one.

**Alternatives considered**: SharedPreferences (no atomic multi-field write, no corruption hook). A
database (heavy for one record). Protocol buffers (a second schema toolchain for no gain here).

## R8. Background execution and permissions

**Decision**:
- **Service**: a location-type foreground service hosts the engine. It is sticky. On every start
  command it checks the required permissions (location and notifications, FR-049) itself; without
  either, it stops without starting the engine (FR-050, FR-052). It is the only caller of the
  engine's `start()`.
- **Notification** (FR-051): title "Car Finder", text "Monitoring for parking". When background
  location is not granted, the text asks for "Allow all the time" and tapping it opens the app's
  permission settings (FR-054).
- **Boot** (FR-053, FR-054): the boot receiver starts the service only if background location and
  the required permissions are granted. With foreground-only permission it does nothing.
- **App open** (FR-054): when the Activity starts with the required permissions granted, it starts
  the service. The Activity never starts the engine; it calls a read-only `restore()` to show stored
  state.
- **`PermissionController`** (FR-047, FR-048, FR-056): the interface has exactly one suspend
  function, `request(capability)`, which is what constitution Principle V asks for, plus an
  observable status and `refresh()`. The launch order and the FR-056 confirm-or-ask-again loop are
  not on the interface: `requestPermissionsInOrder(controller, confirmDenial)` is a plain shared
  function (contracts/shared-domain-api.md). It asks for a required capability again until it is
  granted or the user confirms closing, asks for the two optional capabilities only once, and skips
  background location while location is not granted.
- **Remembering answers** (FR-048): the platform reports only granted or not granted, so the
  Android implementation keeps a "requested" mark per capability in a small private preferences
  file and derives `NOT_REQUESTED` or `DENIED` from it. A capability that needs no runtime grant on
  the running Android version reports `GRANTED` (background location and activity recognition below
  Android 10, notifications below Android 13). Status is re-read whenever the app returns to the
  foreground, and the sequence runs again then, so a required permission turned off in system
  settings is asked for again (FR-048).
- **Approximate location** (FR-056, owner decision 2026-10-03): location is requested as fine and
  coarse together in one prompt, as Android 12+ requires for its precise/approximate choice. Only a
  fine-location grant counts; approximate alone is a denial and gets the confirmation.
- **Required-permission denial** (FR-056, owner decision 2026-10-03, ledger OD-1): the presenter
  holds a transient `DenialConfirmation` and shows `PermissionRequired` (FR-042 rule 1). "Allow",
  back or a tap outside asks for the same permission again. "Close" shows `Closing` for
  `SHUTDOWN_NOTICE_DURATION_MILLIS` and then sets `isClosePending`. The Activity, whenever it is
  started with `isClosePending` true, stops the service, calls `finishAndRemoveTask()` and then
  `onClosed()`. The close is state rather than a one-off event, so it is not lost if the Activity
  was stopped at that moment. Because the presenter is application-scoped and
  `finishAndRemoveTask()` does not end the process, `onClosed()` (and
  `cancelPermissionSequence()`, called when the screen is finished by other means) clears all
  denial state, so nothing carries over to the next launch. Closing does not kill the process, so
  nothing is reported as a crash and the store is untouched. Detection does not run without
  notification permission (FR-049), although the platform itself would allow the service to run
  with its notification hidden.
- **Re-creation during a prompt**: the permission controller is application-scoped, holds the
  pending request, and re-registers its result launcher under one fixed key on every attach. A
  result for a prompt opened before a rotation therefore resumes the waiting `request`.
- **Repeated denials**: after repeated denials Android answers a request as denied without
  showing a prompt, so "Allow" leads straight back to the confirmation. The owner accepted this
  as-is (OD-1). The user can still close the app, or grant the permission in system settings.
- **Verification**: service, receiver and permission behavior are tested on the JVM with fakes, and
  must also be exercised on an emulator or device (QR-016, quickstart §5).

**Rationale**: Android does not let a boot receiver start a location-type foreground service with
only while-in-use location, and it can restart a sticky service with no app code on the stack; both
were confirmed on a device (seed REQ-PERM-07, REQ-PERM-04). Making the service the sole owner of
`start()` keeps the UI a read-only observer (QR-013).

**Alternatives considered**: Scheduled background work instead of a service (cannot hold continuous
location). Letting the Activity start the engine (two owners of sensing; breaks FR-052's guarantee
when permission is missing).

## R9. Engine and presenter

**Decision**:
- `ParkingStateMachine.reduce(snapshot, event)` is a pure function: no clock, no I/O, input never
  modified. Events carry their own times.
- `ParkingEngine` is a single-consumer actor. Inputs (restore, reading, in-vehicle change, guidance
  visibility, recovery-window timer) are queued and handled one at a time. For each: reduce, persist
  if the record changed, then publish state, then apply the sampling interval. A correction is
  published and persisted but is not emitted on the lifecycle-transition stream (FR-022).
- `HomeScreenPresenter` combines engine state, the latest heading, location-permission status and a
  monotonic tick into `StateFlow<HomeScreenState>` using FR-042's order. The tick re-checks fix and
  heading age so staleness appears without a new event (FR-040, FR-041, SC-010).
- The arrival prompt is a three-state tracker in the presenter: armed, prompting, dismissed. It
  returns to armed only when a guidance state with the half-angle below the arrival half-angle is
  computed, so it survives rotation and an unavailable spell (FR-039).

**Rationale**: A pure reducer makes every lifecycle rule a plain unit test and replay deterministic
(QR-016). One consumer removes races between readings, timers and UI visibility.

## R10. Guidance math and geometry

**Decision**: Haversine distance and forward-azimuth initial bearing, written in `commonMain` and
pinned by tests against published reference pairs. Half-angle is `atan2(uncertainty, distance)`,
which gives 90° at distance zero without a special case (FR-031). Display bearing per FR-032,
normalized to [0, 360). Distance text is built without platform formatting functions.

`ConeGeometry` is normalized: origin at the screen center, 1.0 equals the minimum display dimension
(FR-036). It carries the apex (person icon), the far anchor (car icon), the sweep start and sweep
angle. No centerline is included (FR-035). The Composable only scales and translates.

## R11. Compose UI and its tests

**Decision**: `HomeScreen(state, onArrivalAnswered)` and its children are stateless. `MainActivity`
collects the presenter's `StateFlow` with lifecycle awareness and passes the value down. Guidance is
drawn on a Compose Canvas; the arrival prompt is a Compose dialog (QR-012). Status texts are Android
string resources chosen by the state type.

Tests run on the JVM with Robolectric and the Compose semantics test API (QR-004). The guidance
Composable exposes half-angle, display bearing, the two anchors and "centerline drawn = false" as
semantics properties so tests assert what is drawn (QR-006). A source-scan test fails the build if
any file under the UI package imports the Android View system (QR-012).

FR-046 ("without visible stutter") has no number in the spec. It is verified by (a) a recomposition
test showing that a guidance update recomposes only the guidance display and that no geometry is
computed in a Composable, and (b) device check V3. No frame-time budget is asserted.

## R12. Traceability tooling and the findings ledger

**Decision**: `tools/traceability/Get-TraceabilityReport.ps1` reads `**FR-###**` and `**QR-###**`
definitions from spec.md, scans Kotlin KDoc and PowerShell comment lines for
`@requirement <ID>[, <ID>]`, classifies each tag as implementation or test by path, and writes
`traceability.md`. With `-FailOnGaps` it exits 1 on any of QR-010's three conditions. Pester tests
run it against small fixture trees and against the real spec.md (QR-009).

Requirements that have no code by nature (QR-001, QR-002, QR-004, QR-005, QR-006, QR-007, QR-011) are listed
with a reason in `tools/traceability/no-code-requirements.psd1`. The report prints that list, and
QR-010's "no code annotation" check skips only those IDs. They still need a test tag. QR-016 is not
on the list: `ReplayScript` and `ReplayRunner` are its code and carry its tag.

The script also reads `// @requirement` comment lines in `*.gradle.kts` files, so that QR-015
(minimum Android version), which is met by build configuration, has an implementation tag.

`specs/003-park-detect-guidance/analysis-findings.md` is the append-only findings ledger (QR-011),
created with this plan. A Pester test checks that finding IDs are unique and that every finding has
a status.

**Rationale**: QR-010 allows a declared, printed list of requirements that have no code by nature.
Keeping it in one file that the report prints keeps every exemption visible.

## R13. Scripted replay

**Decision**: `:shared-testing` provides `ReplayScript`, a list of timed steps (location reading,
heading, guidance visibility, in-vehicle change, permission change, clock advance) with optional
ground truth (true user position, true heading, true car position). `ReplayRunner` plays it through
the real `ParkingEngine` and `HomeScreenPresenter` with fakes on a virtual clock and records every
published `HomeScreenState` with its time (QR-016).

- **FR-044**: replay checks that the first guidance state is published on the visibility step
  itself, with no wait added by shared logic. The 2 s target is measured on a device (quickstart V2).
- **FR-045**: computed exactly as FR-045 defines it. Scripts displace each reported position by a
  seeded random error no larger than that reading's reported accuracy, as QR-016 requires.

## R14. Build and tooling

**Decision**: Keep the existing catalog: Kotlin 2.2.10, AGP 9.3.3, Gradle 9.5 wrapper, Compose BOM
2026.02.01, coroutines 1.10.2, serialization 1.9.0, DataStore 1.2.1, Play Services Location 21.1.0,
Robolectric 4.17. Remove the benchmark and UI Automator entries. `minSdk = 26` (QR-015), with
`compileSdk` and `targetSdk` at the latest level AGP 9.3 supports. Shared `commonTest` runs as
Android host tests. Gradle needs `JAVA_HOME` set; Android Studio's bundled runtime works.

## Values this plan introduces

All five were settled on 2026-10-02 after `/speckit-analyze`; see `analysis-findings.md`. None now
lives only in this document.

| # | Value | Now specified in |
|---|---|---|
| P1 | Availability re-check interval, 500 ms | spec FR-055, used by FR-040 |
| P2 | Cone-length fraction, 0.65 | spec FR-055, used by FR-036 |
| P3 | Cone-containment measure | spec FR-045 |
| P4 | Replay reading-error model | spec QR-016 |
| P5 | No-code requirement list | spec QR-010; entries in R12 |
