---

description: "Task list for Automatic Park Detection & Guidance Back to the Vehicle"
---

# Tasks: Automatic Park Detection & Guidance Back to the Vehicle

**Input**: The design documents in `specs/002-park-detect-guidance/`, which are [plan.md](plan.md),
[spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) and
[quickstart.md](quickstart.md).

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are **required**. The spec's QR-001 to QR-007 and constitution Principles I, II and III make
automated tests mandatory, so every story phase writes its tests first. Each test must fail before the matching
implementation task starts.

**Organization**: Tasks are grouped by user story, so that each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: The task can run in parallel, because it touches a different file and depends on no incomplete task.
- **[Story]**: The user story the task belongs to (US1 to US5).

## Path Conventions (aliases used in every task below)

| Alias | Expands to |
|---|---|
| `SC/` | `shared/src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/` |
| `ST/` | `shared/src/commonTest/kotlin/com/packmuleforge/carfindermvp/shared/` |
| `SA/` | `shared/src/androidMain/kotlin/com/packmuleforge/carfindermvp/shared/platform/android/` |
| `SAT/` | `shared/src/androidHostTest/kotlin/com/packmuleforge/carfindermvp/shared/platform/android/` |
| `AM/` | `app/src/main/kotlin/com/packmuleforge/carfindermvp/` |
| `AT/` | `app/src/test/kotlin/com/packmuleforge/carfindermvp/` |
| `TT/` | `tools/traceability/` |

## Rules that apply to every task

These are not repeated in each task.

- **Traceability tags**: Every production declaration that implements a requirement carries KDoc
  `@requirement <ID>[, <ID>]` naming the FR or QR IDs in its task. Every test function carries KDoc
  `@requirement <ID>` for what it verifies (grammar: [contracts/traceability.md](contracts/traceability.md)).
- **Named constants in tests**: Tests reference `CarFinderConstants.*` and `TuningConstants.*` by name and derive
  fixtures from them, for example `DRIVING_SPEED_THRESHOLD_MPH + 1.0`. A test must never repeat a constant's
  literal value (Constitution I, QR-002).
- **Portable `commonMain`**: Code under `SC/` must not import `android.*` or `java.*`, and must not call
  `String.format` (research R9).
- **Class-level tags**: Every unit-test class for the state machine, convergence, uncertainty, guidance math
  and default-view selection carries `@requirement QR-001` on the class. Every adapter test class carries
  `@requirement QR-004`.
- **Expand ranges**: Where a task cites a range such as "FR-001 to FR-008", the `@requirement` tags must list
  each ID individually, because the traceability script does not read ranges.
- **Test names**: Test names follow `given…_when…_then…` or describe the behavior. They may begin with the ID,
  such as `fr007_…`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Set up the Gradle modules `:shared` (KMP) and `:app` (Android) so that the project builds, with the
dependency set from plan.md's Technical Context.

- [ ] T001 Update `gradle/libs.versions.toml`:
  - Bump `kotlinxCoroutines` to the current stable 1.10.x.
  - Bump `datastoreVersion` to the current stable 1.1.x.
  - Bump `lifecycleRuntimeKtx` and `activityCompose` to their current stable versions.
  - Add `kotlinx-serialization-json` (current stable), `androidx-datastore` (`androidx.datastore:datastore`),
    `robolectric` (current stable 4.x), `androidx-test-core`, and `androidx-compose-foundation`.
  - Add the plugin `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref =
    "kotlin" }`.
  - Remove the `protobuf` plugin and the `protobuf-kotlin` and `androidx-datastore-proto` libraries (research
    R4).
  - Keep the existing explanatory comments style.
- [ ] T002 Update the root `build.gradle.kts`. Remove `alias(libs.plugins.protobuf) apply false`, and add
  `alias(libs.plugins.kotlin.serialization) apply false`. Keep the KMP-before-Android plugin ordering comment.
- [ ] T003 Create `shared/build.gradle.kts`:
  - Apply the plugins `kotlin.multiplatform`, `android.kotlin.multiplatform.library` and `kotlin.serialization`.
  - In `kotlin { androidLibrary { namespace = "com.packmuleforge.carfindermvp.shared"; compileSdk = <latest
    stable supported by AGP 9.3>; minSdk = 26; withHostTestBuilder {} } }`, set `commonMain` dependencies to
    `kotlinx-coroutines-core` and `kotlinx-serialization-json`.
  - Set `commonTest` dependencies to `kotlin-test` and `kotlinx-coroutines-test`.
  - Set `androidMain` dependencies to `play-services-location`, `androidx-datastore`, `androidx-core-ktx`,
    `androidx-activity-compose` (for ActivityResultRegistry) and `androidx-fragment-ktx`.
  - Set `androidHostTest` dependencies to `kotlin-test-junit`, `junit`, `robolectric`, `androidx-test-core` and
    `kotlinx-coroutines-test`.
  - Enable `isIncludeAndroidResources = true` for host tests.
- [ ] T004 Create `app/build.gradle.kts`:
  - Apply the plugins `android.application` and `kotlin.compose`.
  - Set `namespace` and `applicationId` to `com.packmuleforge.carfindermvp`, `minSdk = 26`, and
    `compileSdk`/`targetSdk` equal to the `:shared` value.
  - Enable `buildFeatures { compose = true }` and `testOptions { unitTests.isIncludeAndroidResources = true }`.
  - Add the dependency `implementation(project(":shared"))`.
  - Add the Compose BOM with ui, ui-graphics, foundation, material3 and ui-tooling-preview.
  - Add `activity-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `core-ktx`,
    `fragment-ktx` and `play-services-location`.
  - Add the test dependencies `junit`, `kotlin-test-junit`, `robolectric`, `androidx-test-core`, the Compose BOM
    with `ui-test-junit4`, and `kotlinx-coroutines-test`.
  - Add `debugImplementation` of `ui-test-manifest` and `ui-tooling`.
- [ ] T005 Create the `:shared-testing` KMP module in `shared-testing/build.gradle.kts`. It uses the same plugins
  and the same `androidLibrary` settings as `:shared`, with `namespace =
  "com.packmuleforge.carfindermvp.shared.testing"`:
  - Its `commonMain` depends on `project(":shared")`, `kotlinx-coroutines-test` and `kotlin-test`.
  - Add `include(":shared-testing")` to `settings.gradle.kts`.
  - Add `implementation(project(":shared-testing"))` to `:shared` `commonTest` and `testImplementation(project(
    ":shared-testing"))` to `:app`.

  It exists because test source sets are not visible to other modules, and `:app` tests need the fakes.
- [ ] T006 [P] Create a minimal `app/src/main/AndroidManifest.xml` with an `<application>` element
  (`android:name=".CarFinderApplication"`, label and theme) and an exported `MainActivity` with the
  MAIN/LAUNCHER intent filter. Permissions, the service and the receiver are added in US1 (T046).
- [ ] T007 [P] Create the placeholder `AM/CarFinderApplication.kt` (an empty `Application` subclass) and
  `AM/MainActivity.kt` (a `ComponentActivity` calling `setContent { }` with an empty theme), plus
  `AM/ui/theme/Theme.kt` holding a Material3 `CarFinderTheme`. Also add
  `app/src/main/res/values/strings.xml` with `app_name`.
- [ ] T008 [P] Ensure the repository root `.gitignore` covers `build/`, `.gradle/`, `local.properties`, `.idea/`,
  `*.iml`, `.env` and `*.local.*`. Create the file if it is missing.
- [ ] T009 Run `.\gradlew.bat :shared:assemble :app:assembleDebug`, and fix the build configuration until it
  succeeds. The exact host-test task names from `gradlew tasks --all` are needed later. Confirm that Gradle
  resolves the `:shared` commonTest → `:shared-testing` → `:shared` main dependency. If KMP rejects that cycle,
  keep `:shared-testing` for `:app` tests only and keep a copy of the fakes in `:shared` `commonTest`.

**Checkpoint**: Both modules compile, and an empty app launches.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The constants, domain value types, persistence model, adapter interfaces, test fakes, geo math and
traceability tooling that every story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests for Foundational

- [ ] T010 [P] Write `ST/guidance/GeoMathTest.kt` (FR-020, FR-021 and FR-025 support):
  - The haversine distance for published reference pairs is within 0.5 %.
  - Two identical points are 0 m apart.
  - The initial bearing for due north, east, south and west is 0, 90, 180 and 270 respectively.
  - The bearing is always in `[0, 360)`.
  - A centroid of points that straddle the antimeridian is correct.
- [ ] T011 [P] Write `ST/persistence/PersistedParkingRecordTest.kt` (FR-018, FR-011):
  - The default record is `{schemaVersion: 1, state: FINDING, parkedLocation: null}`.
  - `normalized()` turns "`state == PARKED && parkedLocation == null` becomes FINDING" and drops the location for
    "`state != PARKED && parkedLocation != null`".
  - A JSON round-trip preserves every field.
  - The record holds exactly one `parkedLocation: ParkedLocation?` field and no collection of locations
    (FR-013).
- [ ] T012 [P] Write `SAT/ConstantsLiteralScanTest.kt` (QR-002, FR-030). This JVM host test walks
  `shared/src/*Test/` and `app/src/test/`, resolving paths from the repository root (the module directory's
  parent), and fails if any test file contains an FR-030 value as a standalone numeric token. The values checked
  are `5.0`, `25.0`, `10.0`, `45.0`, `500.0`, `2_000`/`2000`, `5_000`/`5000` and `30_000`/`30000`, matched with
  the regex `(?<![\w.])(5\.0|25\.0|10\.0|45\.0|500\.0|2_?000L?|5_?000L?|30_?000L?)(?![\w.])`. The integer count constants
  (`CONVERGENCE_SAMPLE_COUNT`, `SPEED_FILTER_WINDOW_SIZE` = 3) cannot be scanned reliably, so they are covered
  by review in T101. The test must also assert that each FR-030 constant name is declared exactly once, in
  `SC/domain/CarFinderConstants.kt`.
- [ ] T013 [P] Write `SAT/CommonMainPortabilityScanTest.kt` (QR-010, Constitution V, research R9). This JVM
  host test walks `shared/src/commonMain/` and fails, listing `file:line`, on any `import android.`,
  `import java.`, `String.format(` or `System.` token.
- [ ] T014 [P] Write `TT/tests/Get-TraceabilityReport.Tests.ps1` (Pester 5, QR-005, QR-006, QR-007) together
  with fixture trees under `TT/tests/fixtures/`. The fixtures are a mini `spec.md` with FR-001, FR-002 and
  QR-001, and `src/commonMain` and `src/commonTest` `.kt` files. The tests cover:
  - The FR and QR ID counts.
  - Classifying a tag as implementation or test by path (`src/test/`, `src/androidTest/`, `src/<name>Test/`).
  - `TRACED`, `UNTESTED`, `UNIMPLEMENTED` and `UNTRACED` statuses.
  - Orphaned-tag detection with its `file:line`.
  - `-FailOnGaps` exits 1 when there are gaps and 0 when the fixture is clean.

### Implementation for Foundational

- [ ] T015 [P] Create `SC/domain/CarFinderConstants.kt` (FR-030) as an `object` with exactly these names and
  values:
  - `PARKING_SPEED_THRESHOLD_MPH = 5.0`
  - `DRIVING_SPEED_THRESHOLD_MPH = 25.0`
  - `CONVERGENCE_RADIUS_METERS = 10.0`
  - `CONVERGENCE_SAMPLE_COUNT = 3`
  - `PARKING_SAMPLING_INTERVAL_MILLIS = 5_000L`
  - `ARRIVAL_HALF_ANGLE_DEGREES = 45.0`
  - `DISTANCE_UNIT_THRESHOLD_FEET = 500.0`
  - `SPEED_FILTER_WINDOW_SIZE = 3`
  - `FIX_STALENESS_TIMEOUT_MILLIS = 30_000L`
  - `HEADING_STALENESS_TIMEOUT_MILLIS = 2_000L`

  It also holds the conversion factors `METERS_PER_SECOND_TO_MPH`, `METERS_TO_FEET` and `FEET_PER_MILE`. Give
  each constant a KDoc line explaining its purpose and `@requirement FR-030`.
- [ ] T016 [P] Create `SC/domain/TuningConstants.kt` as an `object` with:
  - `IDLE_WATCH_SAMPLING_INTERVAL_MILLIS = 20_000L`
  - `DRIVING_SAMPLING_INTERVAL_MILLIS = 5_000L`
  - `GUIDANCE_SAMPLING_INTERVAL_MILLIS = 1_000L`
  - `AVAILABILITY_RECHECK_INTERVAL_MILLIS = 500L`
  - `CONE_LENGTH_FRACTION = 0.8`

  Each gets a KDoc citing its research section (R2 or R10).
- [ ] T017 [P] Create `SC/domain/LifecycleState.kt`, containing `@Serializable enum class LifecycleState {
  FINDING, DRIVING, PARKING, PARKED }` (FR-001, FR-011). Document the invariant "`state == PARKED` ⇔
  `parkedLocation != null` (FR-018)".
- [ ] T018 [P] Create `SC/domain/LocationReading.kt` as a data class with the fields `latitude: Double`,
  `longitude: Double`, `accuracyMeters: Double?` (null if the provider gave no horizontal accuracy),
  `speedMetersPerSecond: Double?` (null if the provider gave no speed), `elapsedRealtimeMillis: Long` and
  `epochMillis: Long`. Add the computed properties `hasAccuracy` and `speedMph: Double?`, which uses
  `CarFinderConstants.METERS_PER_SECOND_TO_MPH`.
- [ ] T019 [P] Create `SC/domain/ParkedLocation.kt` as an `@Serializable data class` with `latitude: Double`,
  `longitude: Double`, `accuracyMeters: Double` ("always non-null and > 0", enforced with `require`) and
  `capturedAtEpochMillis: Long` (FR-012, FR-013).
- [ ] T020 [P] Create `SC/domain/HeadingReading.kt` as a data class with `trueHeadingDegrees: Double`
  ("[0, 360), true north, remapped to screen-up", enforced with `require`) and `elapsedRealtimeMillis: Long`.
- [ ] T021 [P] Create `SC/domain/SamplingProfile.kt`, containing `enum class SamplingProfile { IDLE_WATCH,
  DRIVING, PARKING, GUIDANCE }` with an `intervalMillis` property that maps to
  `TuningConstants.IDLE_WATCH_SAMPLING_INTERVAL_MILLIS`, `TuningConstants.DRIVING_SAMPLING_INTERVAL_MILLIS`,
  `CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS` and
  `TuningConstants.GUIDANCE_SAMPLING_INTERVAL_MILLIS` (FR-006, research R2).
- [ ] T022 Create `SC/persistence/PersistedParkingRecord.kt`, an `@Serializable data class` with `schemaVersion:
  Int = 1`, `state: LifecycleState = FINDING` and `parkedLocation: ParkedLocation? = null`. Add
  `fun normalized()` per data-model, and a `companion val DEFAULT` (FR-011, FR-014, FR-018). This depends on
  T017 and T019, and makes T011 pass.
- [ ] T023 [P] Create `SC/persistence/ParkingStore.kt`, the interface with
  `suspend fun read(): PersistedParkingRecord` ("never throws; corrupted → default record") and
  `suspend fun write(record: PersistedParkingRecord)` ("atomic") (FR-014, QR-010).
- [ ] T024 [P] Create the platform interfaces in `SC/platform/` exactly as in
  [contracts/platform-adapters.md](contracts/platform-adapters.md):
  - `LocationSource.kt`
  - `HeadingSource.kt`
  - `ActivitySignalSource.kt`
  - `Clocks.kt` (`MonotonicClock`, `WallClock`)
  - `PermissionController.kt` (`Capability`, `RequestMode`, `CapabilityStatus`, interface)

  These implement QR-010, with KDoc stating each adapter's "MUST" behavior from the contract table.
- [ ] T025 [P] Create `SC/guidance/GeoMath.kt`, an `object GeoMath` with `distanceMeters` (haversine, WGS-84
  mean radius 6_371_008.8 m), `initialBearingDegrees` (forward azimuth normalized to `[0, 360)`) and
  `centroid(points)`. The centroid is the mean latitude, and the mean longitude computed from deltas relative to
  the first point to handle the antimeridian (research R8). This makes T010 pass.
- [ ] T026 Create the test fakes in
  `shared-testing/src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/testing/`, as described in the
  Fakes section of the platform-adapters contract:
  - `FakeLocationSource.kt`: `emit(reading)`, and records `setProfile` calls in `profileHistory`.
  - `FakeHeadingSource.kt`: `emit(HeadingReading?)`, and records `started`.
  - `FakeActivitySignalSource.kt`: `emitInVehicle()`.
  - `InMemoryParkingStore.kt`: `seed(record)`, `failNextRead`, and a `writes` list.
  - `FakeClocks.kt`: `FakeMonotonicClock.advanceBy(ms)` and a settable `FakeWallClock`.
  - `FakePermissionController.kt`: a scripted status map and a per-capability `RequestMode`.
  - `Readings.kt`: builders such as `readingAt(lat, lon, accuracy = …, speedMph = …)` that take their defaults
    from constants.

  This depends on T018 to T024.
- [ ] T027 Create `SC/platform/PlatformAdapters.kt`, containing only the common `class PlatformAdapters(location,
  heading, activity, store, monotonicClock, wallClock, permissions)`. Then:
  - In `AM/CarFinderApplication.kt`, add `open fun createAdapters(): PlatformAdapters` with the body `TODO("Wired
    to createPlatformAdapters(this) in T046")` and a `// TODO:` comment giving the reason: it is a deliberate
    stub so that the US1 tests can compile against `TestCarFinderApplication` first.
  - Create `AT/TestCarFinderApplication.kt`, which overrides `createAdapters()` to return fakes from
    `:shared-testing` with scriptable permission status.

  T037, T038, T088 and every `:app` Activity test use it through `@Config(application =
  TestCarFinderApplication::class)`. This depends on T026.
- [ ] T028 Extend `TT/Get-TraceabilityReport.ps1` per [contracts/traceability.md](contracts/traceability.md):
  - The default `-SpecPath` is `specs/002-park-detect-guidance/spec.md` and the default `-OutputPath` is
    `specs/002-park-detect-guidance/traceability.md`.
  - Parse `\*\*(FR|QR)-(\d{3})\*\*`.
  - Scan `*.kt` for KDoc `@requirement` lines holding a comma-separated ID list, and scan `TT/*.ps1` for
    `# @requirement`.
  - Classify tags as test or implementation by path, and exclude `TT/tests/fixtures/` by default.
  - Emit the report in the markdown format from the contract: a summary line, a table of Requirement,
    Implementation, Tests and Status, then the Untraced and Orphaned sections.
  - Support `-FailOnGaps` with exit code 1.
  - Add a `# @requirement QR-005, QR-006, QR-007` header comment.

  This makes T014 pass.
- [ ] T029 Run `.\gradlew.bat :shared:allTests` (or the host-test task found in T009) and `Invoke-Pester
  tools\traceability\tests`. T010 to T014 must all pass.

**Checkpoint**: The foundation is ready. User story work can start.

---

## Phase 3: User Story 1 - Parking is detected and remembered automatically (Priority: P1) 🎯 MVP

**Goal**: Always-on background detection moves FINDING → DRIVING → PARKING → PARKED using the filtered speed and
pairwise convergence. It stores the centroid and its accuracy, and that record survives termination, process
death and reboot.

**Independent Test**: Feed a scripted sequence of readings through `ParkingEngine` with fakes: above the driving
threshold, then at or below the parking threshold, then 3 readings clustered within the convergence radius.
Assert that the state is PARKED and the stored Parked Location equals the centroid. Re-create the engine over the
same store and assert that it is still PARKED with the location present. On a device, follow quickstart §5.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [ ] T030 [P] [US1] Write `ST/domain/SpeedMedianFilterTest.kt` (FR-032). It covers:
  - `null` until `SPEED_FILTER_WINDOW_SIZE` samples have been added.
  - The median of `[slow, spike, slow]` is slow.
  - The window drops its oldest sample FIFO.
  - The even-window median is the mean of the two middle values.
  - The filter is a median, not a consecutive count: `[fast, slow, fast]` gives fast.
  - `add` returns a new filter and leaves the receiver unchanged (immutability).
- [ ] T031 [P] [US1] Write `ST/domain/ConvergenceWindowTest.kt` (FR-007, FR-008, FR-012). It covers:
  - Three readings `CONVERGENCE_RADIUS_METERS * 0.9` apart in a line do **not** converge, even though each is
    within the radius of the centroid.
  - Three readings pairwise ≤ the radius converge.
  - The window slides and drops its oldest reading.
  - A reading with a null accuracy is ignored.
  - The window is not converged before `CONVERGENCE_SAMPLE_COUNT` readings.
  - `toParkedLocation` returns the centroid, with `accuracyMeters = max_i(accuracy_i + distance(centroid,
    reading_i))` (FR-012).
  - `empty()` gives an unconverged window.
  - `add` returns a new window and leaves the receiver unchanged (immutability).
- [ ] T032 [P] [US1] Write `ST/domain/ParkingStateMachineParkTest.kt` (FR-001 to FR-008, FR-010, FR-011 and
  FR-032). It covers:
  - FINDING plus a filtered speed above `DRIVING_SPEED_THRESHOLD_MPH` goes to DRIVING.
  - DRIVING plus a filtered speed ≤ `PARKING_SPEED_THRESHOLD_MPH` goes to PARKING.
  - Dead-zone speeds in DRIVING or PARKING cause no change.
  - FINDING with slow or stationary speeds stays FINDING, and PARKING is never entered from FINDING.
  - PARKING plus converged readings goes to PARKED, with the location set.
  - PARKING plus fast readings goes to DRIVING, with the window cleared and nothing stored.
  - A single raw spike does not trigger DRIVING entry, and a single slow raw reading at speed does not trigger
    PARKING entry.
  - A `null` speed causes no speed transition.
  - After every reduce, `(to == PARKED) == (parkedLocation != null)`.
  - Reducing the same snapshot and event twice gives equal `Transition`s, and the input snapshot is unchanged.
- [ ] T033 [P] [US1] Write `ST/engine/ParkingEngineParkTest.kt` (FR-002, FR-006, FR-012, FR-014, FR-018,
  FR-033). It uses `runTest` with fakes and covers:
  - `start()` restores from `InMemoryParkingStore`.
  - The PARKING transition sets the `LocationSource` profile to `PARKING`, and the rate is
    `PARKING_SAMPLING_INTERVAL_MILLIS`.
  - The DRIVING profile is `DRIVING`, and FINDING is `IDLE_WATCH`.
  - The record is persisted **before** `state` is published, which is asserted by the ordering of store writes
    against emissions.
  - A restored `PARKED` record with no location becomes FINDING, and the normalized record is rewritten.
  - Re-creating the engine over the same store restores PARKED with the same location.
  - `start()` is idempotent.
  - The `transitions` SharedFlow emits each transition.
- [ ] T034 [P] [US1] Write `SAT/DataStoreParkingStoreTest.kt` (FR-014, FR-018, QR-004). It uses a real DataStore
  on a JUnit `TemporaryFolder` and covers:
  - A write/read round-trip.
  - A missing file reads as the default FINDING record.
  - A file of garbage bytes reads as default FINDING and does not throw.
  - An unknown `schemaVersion` reads as the default.
  - A write replaces the whole record atomically, so state and location match after the write.
- [ ] T035 [P] [US1] Write `SAT/FusedLocationSourceTest.kt` (FR-006, FR-033, QR-004). It uses Robolectric with
  `FakeFusedClientPort` and covers:
  - A `Location` without accuracy maps to `accuracyMeters == null`.
  - A `Location` without speed maps to `speedMetersPerSecond == null`.
  - Negative or NaN accuracy or speed maps to null.
  - Out-of-range latitude or longitude is dropped.
  - `elapsedRealtimeNanos` maps to `elapsedRealtimeMillis`.
  - `setProfile(PARKING)` re-issues exactly one request with `PRIORITY_HIGH_ACCURACY`, interval =
    `PARKING_SAMPLING_INTERVAL_MILLIS`, `minUpdateIntervalMillis` equal to the interval, and
    `maxUpdateDelayMillis == 0`. The previous callback is removed, so at most one subscription is active.
  - A `SecurityException` from the port completes the flow without crashing.
- [ ] T036 [P] [US1] Write `SAT/AndroidPermissionControllerTest.kt` (QR-004, FR-033, research R6). It uses a
  test `ActivityResultRegistry` and covers:
  - The request order is foreground location, then `POST_NOTIFICATIONS` (API 33+), then `ACTIVITY_RECOGNITION`
    (API 29+), then background location (a separate request).
  - A denied foreground location stops early and returns DENIED for it.
  - A capability that is already GRANTED is not re-prompted.
  - `NOTIFICATIONS` below API 33 is `NOT_REQUIRED` and `GRANTED`.
  - `status` updates after a request.

  Use `@Config(sdk = [...])` to cover the API levels.
- [ ] T037 [P] [US1] Write `AT/service/ParkingDetectionServiceTest.kt` (FR-033). It uses a Robolectric
  `ServiceController` and covers:
  - `onStartCommand` calls `startForeground` with a notification on the detection channel.
  - It returns `START_STICKY`.
  - The engine is started.
  - The notification text reflects `EngineState.lifecycle`.
  - When `LOCATION_FOREGROUND` is GRANTED but `LOCATION_BACKGROUND` is not, the notification text is the
    `notification_needs_background` string, and its content intent opens the app's permission settings
    (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for this package). When both are granted, it shows the
    lifecycle text (spec Edge Cases, FR-033).
- [ ] T038 [P] [US1] Write `AT/service/BootReceiverTest.kt` (FR-033). It covers:
  - `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` start `ParkingDetectionService` only when `LOCATION_FOREGROUND`
    and `LOCATION_BACKGROUND` are both GRANTED.
  - The service is not started otherwise.
  - Other actions are ignored.

### Implementation for User Story 1

- [ ] T039 [P] [US1] Implement `SC/domain/SpeedMedianFilter.kt` (FR-032) per
  [contracts/shared-domain-api.md](contracts/shared-domain-api.md). It is an immutable, list-backed FIFO of the
  last `windowSize` speeds in mph. `add()` returns a new filter, and `filtered` is the median once the window is
  full and `null` before that. It is created with `empty()` and is not reset on transitions. This makes T030
  pass.
- [ ] T040 [P] [US1] Implement `SC/domain/ConvergenceWindow.kt` (FR-007, FR-008, FR-012) as an immutable,
  list-backed value, where `add()` returns a new window. It ignores readings with `accuracyMeters == null`. `isConverged` is true iff the window is full and the maximum pairwise
  `GeoMath.distanceMeters` is ≤ the radius. `toParkedLocation()` uses `GeoMath.centroid` and the FR-012 accuracy
  formula. `empty()` creates a fresh window. There is no `clear()`. This makes T031 pass.
- [ ] T041 [US1] Implement `SC/domain/ParkingStateMachine.kt`, containing `MachineSnapshot`, `MachineEvent`
  (`Reading`, `Restored`), `Transition` and `object ParkingStateMachine { fun reduce(...) }` for the park path
  (FR-001 to FR-008, FR-010, FR-011, FR-013, FR-018, FR-032). The guard order within one reading is:
  1. Any filtered speed `v` above `DRIVING_SPEED_THRESHOLD_MPH` → DRIVING. From PARKING, the window resets to
     `ConvergenceWindow.empty()`.
  2. DRIVING with `v ≤ PARKING_SPEED_THRESHOLD_MPH` → PARKING, and the window resets to `empty()`.
  3. PARKING → add the reading to the window; if it has converged → PARKED with `toParkedLocation(nowEpochMillis)`
     and an `empty()` window.

  `Transition` carries the complete next `MachineSnapshot`, as in the contract.

  `Restored` applies `record.normalized()`. Leave a clearly marked branch where PARKED + `v > DRIVING` is handled
  in US5 (T094). Until then PARKED ignores speed. `reduce` is pure, with no clock and no I/O. This makes T032
  pass. It depends on T039 and T040.
- [ ] T042 [US1] Implement `SC/engine/ParkingEngine.kt` (FR-002, FR-006, FR-012, FR-014, FR-018, FR-033):
  - It is a single-consumer actor over a `Channel<EngineInput>` (`LocationReceived`, `ActivityInVehicle`,
    `GuidanceVisibility`, `Restore`) (research R7).
  - `start()` is idempotent. It launches the actor, sends `Restore` (read the store, normalize, and rewrite if
    normalization changed it), starts `LocationSource`, and collects readings.
  - It holds `var snapshot: MachineSnapshot`, which only the actor touches, and replaces it with
    `transition.snapshot` after each `reduce`.
  - For each input it runs `ParkingStateMachine.reduce`, calls `store.write(...)` **before** updating `state` when
    `persist == true`, emits on `transitions`, and calls `location.setProfile(profileFor(state,
    guidanceVisible))` per the data-model SamplingProfile rules.
  - `setGuidanceVisible` and the `ActivityInVehicle` hint are accepted but only recorded in this story (used in
    US2 and US5).
  - It exposes `state: StateFlow<EngineState>`, where `EngineState(lifecycle, parkedLocation, samplingProfile,
    latestFix)`.

  This makes T033 pass.
- [ ] T043 [P] [US1] Implement `SA/DataStoreParkingStore.kt` and `SA/ParkingRecordSerializer.kt` (FR-014,
  FR-018):
  - Use `DataStoreFactory.create(serializer = ParkingRecordSerializer, corruptionHandler =
    ReplaceFileCorruptionHandler { PersistedParkingRecord.DEFAULT }, produceFile = { filesDir/datastore/
    parking_state.json })`.
  - The serializer uses `Json { ignoreUnknownKeys = true }`, and throws `CorruptionException` on a parse failure
    or an unknown `schemaVersion`.
  - `read()` returns `data.first().normalized()` and wraps `IOException` into the default record, so it never
    throws.
  - `write()` uses `updateData { record }`.
  - The store is a process singleton, because DataStore forbids two instances per file.

  This makes T034 pass.
- [ ] T044 [P] [US1] Implement `SA/FusedLocationSource.kt` and `SA/FusedClientPort.kt` (FR-006, FR-033):
  - The port wraps `FusedLocationProviderClient.requestLocationUpdates` and `removeLocationUpdates`.
  - The source maps `Location` to `LocationReading` using the validation rules from data-model: "Latitude must be
    in [-90, 90] and longitude in [-180, 180]. Readings outside these ranges are dropped and logged"; "A negative
    or non-finite accuracy maps to `null`"; "A negative or non-finite speed maps to `null`".
  - `setProfile` removes the old callback and requests again with a `LocationRequest.Builder(PRIORITY_HIGH_ACCURACY,
    profile.intervalMillis).setMinUpdateIntervalMillis(profile.intervalMillis).setMaxUpdateDelayMillis(0)`.
  - `readings` is a hot `SharedFlow`.
  - It catches `SecurityException`, logs it, and completes empty.

  This makes T035 pass.
- [ ] T045 [P] [US1] Implement `SA/AndroidClocks.kt` (`AndroidMonotonicClock` using
  `SystemClock.elapsedRealtime()`, and `AndroidWallClock` using `System.currentTimeMillis()`), and
  `SA/AndroidPermissionController.kt` (research R6):
  - It is bound to a `ComponentActivity`'s `ActivityResultRegistry` through `attach(activity)` and `detach()`.
  - `request()` bridges `RequestMultiplePermissions` into a suspend call with `suspendCancellableCoroutine`, in
    the sequence tested in T036.
  - `requestMode` follows the API-level rules.
  - `status` is a `StateFlow` refreshed by `refresh()`, which is called on resume.

  This makes T036 pass.
- [ ] T046 [US1] Add to `SC/platform/PlatformAdapters.kt` the `expect abstract class PlatformContext` and
  `expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters` (the class itself exists from
  T027). Create `SA/PlatformAdapters.android.kt` (`actual typealias PlatformContext = android.content.Context`,
  and an `actual` factory wiring `FusedLocationSource`, `DataStoreParkingStore`, the clocks and
  `AndroidPermissionController`). Replace the `TODO()` body of `CarFinderApplication.createAdapters()` with
  `createPlatformAdapters(this)`, and remove its `// TODO:` comment.
  Until US2 and US5 replace them, `heading` is a `NoHeadingSource` that always emits `null` and `activity` is a
  `NoActivitySignalSource` that never emits (QR-010, Constitution V).
- [ ] T047 [US1] Implement `AM/service/DetectionNotification.kt`. It creates a low-importance channel
  `parking_detection` and builds an ongoing notification whose text maps each `LifecycleState` to a short status
  from `strings.xml`. If `permissions.status` shows `LOCATION_BACKGROUND` not GRANTED, it instead shows
  `notification_needs_background` ("Background detection needs location access set to 'Allow all the time'.
  Tap to change."), with a content intent that opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for this
  package (spec Edge Cases). Then implement `AM/service/ParkingDetectionService.kt`: a foreground service that calls
  `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)`, then `engine.start()`, collects
  `engine.state` to update the notification, and returns `START_STICKY` (FR-033). This makes T037 pass.
- [ ] T048 [US1] Implement `AM/service/BootReceiver.kt` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. It starts
  the service with `ContextCompat.startForegroundService` only if foreground and background location are both
  granted (FR-033, research R5). This makes T038 pass.
- [ ] T049 [US1] Update `app/src/main/AndroidManifest.xml`:
  - Add the permissions `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
    `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` and
    `RECEIVE_BOOT_COMPLETED`.
  - Add `<service android:name=".service.ParkingDetectionService" android:exported="false"
    android:foregroundServiceType="location"/>`.
  - Add `<receiver android:name=".service.BootReceiver" android:exported="true">` with intent filters for
    `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- [ ] T050 [US1] Implement `AM/CarFinderApplication.kt` as the app-scoped graph. It holds a process-wide
  `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, `adapters = createAdapters()` (the seam from T027, wired
  in T046) and a single `ParkingEngine`. Then:
  - Update `AM/MainActivity.kt` so that `onCreate` attaches the permission controller, launches
    `permissions.request(Capability.entries.toSet())`, and starts `ParkingDetectionService` if
    `LOCATION_FOREGROUND` is GRANTED.
  - When permission is denied, the Activity does not start the service and does not crash (spec Assumptions).
  - `onResume` calls `permissions.refresh()`, then starts the service if `LOCATION_FOREGROUND` is GRANTED. This
    covers a permission granted later in system Settings (spec Edge Cases), and is harmless if the service is
    already running.
- [ ] T051 [US1] Write `ST/engine/ParkingReplayTest.kt` (FR-002, FR-011, SC-001, SC-002). It replays scripted
  sessions through `ParkingEngine` with fakes, covering four sessions:
  - drive, stop, converge → PARKED, with the stored location equal to the centroid;
  - dead-zone only → FINDING throughout;
  - fresh install with slow readings → no location stored;
  - a long red light (PARKING, then fast) → DRIVING with nothing stored.
- [ ] T052 [US1] Run `.\gradlew.bat :shared:allTests :app:testDebugUnitTest` and
  `.\tools\traceability\Get-TraceabilityReport.ps1`. Every US1 test must pass. Then confirm that FR-001 to
  FR-008, FR-010 to FR-014, FR-018, FR-032 and FR-033 each show at least one implementation tag and one test tag.

**Checkpoint**: Parking is detected and persisted in the background. The engine replay tests and quickstart §5
can be run.

---

## Phase 4: User Story 2 - Guided back to the car with honest direction and distance (Priority: P1)

**Goal**: While PARKED, opening the app shows an uncertainty cone that rotates with the true-north compass
heading, with the person and car icons and the distance text in ft or mi. If the fix is stale or missing accuracy,
or the heading is lost, it shows "Parked location unavailable." instead.

**Independent Test**: With a known Parked Location, fix, accuracies and heading, verify the display bearing, the
half-angle and the distance text against FR-019 to FR-026 in `GuidanceCalculatorTest`, and through the Compose
semantics in `GuidanceDisplayTest`. Advance the fake clock past the staleness timeout and verify that the view is
`Unavailable`.

### Tests for User Story 2 ⚠️ (write first, must fail)

- [ ] T053 [P] [US2] Write `ST/guidance/GuidanceCalculatorTest.kt` (FR-019, FR-020, FR-021, FR-025, FR-026). It
  covers:
  - Uncertainty is the parked accuracy plus the fix accuracy.
  - The half-angle is `atan2(u, d)` in degrees, and 90° at `d == 0`.
  - The display bearing is `(360 − heading + bearing) mod 360` for wraparound cases, such as a heading of 350 and
    a bearing of 10 giving 20, with the result always in `[0, 360)`.
  - Unit selection at `DISTANCE_UNIT_THRESHOLD_FEET − 1`, exactly at it, and `+ 1` gives FEET, FEET and MILES.
  - The text is whole feet ("412 ft") or miles to 2 decimals rounded half-up ("0.37 mi"). Build the expected
    strings from constants and conversion factors, not literals.
- [ ] T054 [P] [US2] Write `ST/guidance/ConeGeometryCalculatorTest.kt` (FR-022, FR-023, FR-024). It covers:
  - The apex is at `-CONE_LENGTH_FRACTION/2` and the car anchor at `+CONE_LENGTH_FRACTION/2` along the display
    bearing, with +y up.
  - Sweep start is `bearing − halfAngle`, and the sweep is `2·halfAngle`.
  - Every point lies within a radius of 0.5, so it fits inside the min-dimension square.
  - The half-angle and bearing are echoed back.
  - No centerline field exists. Assert this by reflection over the property names, or by the documented field
    list.
- [ ] T055 [P] [US2] Write `ST/guidance/FixCurrencyTest.kt` (FR-034). A fix is current at an age of exactly
  `FIX_STALENESS_TIMEOUT_MILLIS` and not current at timeout + 1. It uses the monotonic time, not the epoch time.
- [ ] T056 [P] [US2] Write `ST/guidance/DefaultViewSelectorTest.kt` (FR-016, FR-031). It checks the priority
  order in all state combinations:
  - DRIVING → Driving
  - FINDING → Unavailable
  - PARKED with guidance not available → Unavailable, for each unavailable reason (stale fix, fix without
    accuracy, null heading, no fix)
  - PARKING → Parking
  - PARKED with guidance available → Guidance
- [ ] T057 [P] [US2] Write `ST/engine/HomeScreenPresenterGuidanceTest.kt` (FR-017, FR-019, FR-031,
  FR-034, QR-009, SC-010). It uses fakes and a `TestScope` and covers:
  - PARKED with a current fix and a heading gives `Guidance` with the expected cone and distance.
  - Heading `null` gives `Unavailable`, and the lifecycle stays PARKED with the location kept (assert on
    engine state and on the store).
  - With no new fix, advancing the clock past `FIX_STALENESS_TIMEOUT_MILLIS` gives `Unavailable` within one
    `AVAILABILITY_RECHECK_INTERVAL_MILLIS` tick.
  - With no new heading event, advancing the clock past `HEADING_STALENESS_TIMEOUT_MILLIS` gives `Unavailable`
    within one `AVAILABILITY_RECHECK_INTERVAL_MILLIS` tick (SC-010).
  - A fresh fix restores `Guidance`.
  - A fix with null accuracy gives `Unavailable`.
  - Equal consecutive states are not re-emitted.
- [ ] T058 [P] [US2] Write `ST/engine/ParkingEngineGuidanceProfileTest.kt` (research R2). It covers:
  - `setGuidanceVisible(true)` in PARKED switches the profile to `GUIDANCE`, and `false` switches it back to
    `IDLE_WATCH`.
  - Guidance visibility is ignored for the profile in the other states.
  - Only one profile is active at a time, checked through `FakeLocationSource.profileHistory`.
- [ ] T059 [P] [US2] Write `SAT/HeadingMathTest.kt` (FR-021, QR-004, research R3). It uses the pure
  `HeadingMath.azimuth(rotationVector, displayRotation, declinationDegrees)` with synthetic rotation vectors for a
  device facing N, E, S and W in portrait, with display rotations 0, 90, 180 and 270. It checks that the
  declination is added and that the output is normalized to `[0, 360)`.
- [ ] T060 [P] [US2] Write `SAT/RotationVectorHeadingSourceTest.kt` (FR-031, QR-004). It uses Robolectric with a
  `FakeSensorPort` and covers:
  - No rotation-vector sensor falls back to the geomagnetic one, and with neither it emits `null`.
  - Accuracy `SENSOR_STATUS_UNRELIABLE` emits `null`.
  - No event for `CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS` emits `null` (FR-030, FR-031).
  - `start()` and `stop()` register and unregister the listener.
  - `updateDeclinationFrom(fix)` changes the output heading.
- [ ] T061 [P] [US2] Write `AT/ui/GuidanceDisplayTest.kt` (QR-003, FR-020 to FR-026). It uses Robolectric with
  `createComposeRule()` and covers:
  - **(1) Cone geometry from inputs**: build `HomeScreenState.Guidance` by running an uncertainty, distance and
    heading fixture, derived from constants, through `GuidanceCalculator.compute` and
    `ConeGeometryCalculator.compute` (Constitution I: "for a given uncertainty/distance input"). Assert that the
    `GUIDANCE_CONE` node's `ConeHalfAngleDegrees` equals `atan2(uncertainty, distance)` in degrees, and that
    `ConeDisplayBearingDegrees` equals the FR-021 display bearing. Render at a portrait size and at a landscape size, and assert
    that `ConeDrawSize` is equal in both (FR-024).
  - **(2) Feet↔miles**: `DISTANCE_TEXT` ends with "ft" just below and at the threshold, and with "mi" just above
    it.
  - The `PERSON_ICON` and `CAR_ICON` nodes exist, with the content descriptions "You" and "Your car".
  - The cone node has no centerline child (FR-023).
- [ ] T062 [P] [US2] Write `AT/ui/GuidanceRecompositionTest.kt` (FR-027). It renders `HomeScreen` and pushes
  100 successive `Guidance` states that differ only in the display bearing. Using `SideEffect` counters in
  test-only wrappers, it asserts that the cone recomposes exactly once per state, and that the status and arrival
  composables never recompose.
- [ ] T063 [P] [US2] Write `AT/GuidanceSessionObserverTest.kt` (FR-017, Constitution IV, research R2). It uses
  Robolectric `ActivityScenario` under `TestCarFinderApplication`, and covers:
  - Moving `MainActivity` to STARTED calls `engine.setGuidanceVisible(true)` and `FakeHeadingSource.start()`.
  - Moving it to STOPPED calls `setGuidanceVisible(false)` and `stop()`.
  - A new presenter state reaches `HomeScreen` while STARTED.
- [ ] T064 [P] [US2] Write `AT/ui/ComposeOnlySourceScanTest.kt` (QR-008). It asserts that `app/src/main` has no
  `AndroidView(`, no `setContentView(`, no `import android.widget.`, no `import android.view.View` in `ui/`, and
  that `app/src/main/res/layout/` does not exist. It also asserts that `app/src/main` contains no `synthetic`,
  no `InMemoryParkingStore`, and no `shared.testing` imports, so benchmark and test fakes never ship in release.

### Implementation for User Story 2

- [ ] T065 [P] [US2] Implement `SC/guidance/Uncertainty.kt` (FR-019) and `SC/guidance/GuidanceCalculator.kt`
  (FR-020, FR-021, FR-025, FR-026) per the shared-domain-api contract. It includes `DistanceDisplay(value: Double,
  unit: DistanceUnit, text: String)`, with `enum DistanceUnit { FEET, MILES }`. Formatting uses integer
  arithmetic only (research R9). `compute()` returns a `GuidanceState` with every data-model field except
  `isArrived`, which is added in US3 (T082). This makes T053 pass.
- [ ] T066 [P] [US2] Implement `SC/guidance/ConeGeometry.kt`, which holds `Point(x, y)`, `ConeGeometry(apex,
  carAnchor, sweepStartDegrees, sweepDegrees, halfAngleDegrees, displayBearingDegrees)` and `object
  ConeGeometryCalculator` (FR-022, FR-023, FR-024). It uses normalized coordinates: "The origin is the screen
  center, +y points up the screen, and 1.0 equals the minimum display dimension". This makes T054 pass.
- [ ] T067 [P] [US2] Implement `SC/guidance/FixCurrency.kt` (FR-034). A fix is current iff
  `nowElapsedMillis − fix.elapsedRealtimeMillis ≤ FIX_STALENESS_TIMEOUT_MILLIS`. This makes T055 pass.
- [ ] T068 [P] [US2] Implement `SC/guidance/DefaultViewSelector.kt`, which holds `enum ViewKind { DRIVING,
  UNAVAILABLE, PARKING, GUIDANCE }` and `select(lifecycle, guidanceAvailable)` in exactly FR-016's priority order
  (FR-016, FR-031). This makes T056 pass.
- [ ] T069 [US2] Create `SC/engine/HomeScreenState.kt`, the sealed interface from data-model (`Driving`,
  `Unavailable`, `Parking`, and `Guidance(cone, distance, isArrived, isArrivalPromptVisible)`), all
  `@Immutable`-friendly data classes and objects. Then implement `SC/engine/HomeScreenPresenter.kt` (FR-016,
  FR-017, FR-027, FR-031, FR-034, QR-009):
  - It combines `engine.state`, `heading.headings` and a ticker of `AVAILABILITY_RECHECK_INTERVAL_MILLIS`.
  - It computes availability: "the state is PARKED, a Parked Location exists, the fix is current … and has
    accuracy, and the heading is present, reliable and not stale".
  - It calls `GuidanceCalculator` and `ConeGeometryCalculator`, and maps the result through
    `DefaultViewSelector`.
  - It exposes `state: StateFlow<HomeScreenState>` with `distinctUntilChanged`.
  - It forwards the latest fix to `heading.updateDeclinationFrom`.
  - For now `isArrived` and `isArrivalPromptVisible` are `false`, until US3.

  This makes T057 pass. It depends on T065 to T068.
- [ ] T070 [US2] Update `SC/engine/ParkingEngine.kt` so that `GuidanceVisibility` inputs select the `GUIDANCE`
  profile while PARKED and visible, and `IDLE_WATCH` otherwise (research R2). This makes T058 pass.
- [ ] T071 [P] [US2] Implement `SA/HeadingMath.kt`, a pure object built on `SensorManager.getRotationMatrixFromVector`,
  `remapCoordinateSystem` per display rotation, `getOrientation`, the declination added, and normalization to
  `[0, 360)`. Also implement `SA/SensorPort.kt` and `SA/RotationVectorHeadingSource.kt`:
  - It uses `TYPE_ROTATION_VECTOR`, falling back to `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, at `SENSOR_DELAY_UI`.
  - It emits `null` when there is no sensor, when accuracy is UNRELIABLE, or on staleness via a
    `CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS` watchdog.
  - The declination comes from `GeomagneticField(lat, lon, 0f, epochMillis)`.
  - The display rotation comes from `Context.display.rotation`.

  This covers FR-021 and FR-031 and makes T059 and T060 pass.
- [ ] T072 [US2] Update `SA/PlatformAdapters.android.kt` to wire `RotationVectorHeadingSource` in place of
  `NoHeadingSource`. Delete `NoHeadingSource` if nothing else uses it.
- [ ] T073 [P] [US2] Create `AM/ui/GuidanceSemantics.kt`. It holds the `object TestTags` with every tag listed in
  the guidance-ui contract, and the custom `SemanticsPropertyKey<Double>`s `ConeHalfAngleDegrees` and
  `ConeDisplayBearingDegrees`, plus `SemanticsPropertyKey<Dp>` `ConeDrawSize`, each with a `SemanticsPropertyReceiver`
  extension setter.
- [ ] T074 [US2] Implement `AM/ui/GuidanceDisplay.kt` (FR-020 to FR-025, FR-027, QR-008, QR-009). It is a
  stateless `@Composable fun GuidanceDisplay(state: HomeScreenState.Guidance, onArrivalAnswered: (Boolean) ->
  Unit)`:
  - A `BoxWithConstraints` sets `minDim = min(maxWidth, maxHeight)` and holds a centered `minDim × minDim`
    `Canvas` tagged `GUIDANCE_CONE`.
  - The canvas draws the sector with `drawArc(useCenter = true)` from `sweepStartDegrees`/`sweepDegrees`,
    converted to Canvas angle convention. The radius is `CONE_LENGTH_FRACTION × minDim` and the pivot is at
    `center + apex × minDim`, with y flipped. This scale and translate is the only arithmetic. No centerline is
    drawn.
  - The `Icon`s for person and car are placed at `apex` and `carAnchor`.
  - The distance `Text` is centered and not rotated, tagged `DISTANCE_TEXT`.
  - It sets the semantics properties from T073.
  - There is no allocation inside the draw lambda.

  This makes T061 pass.
- [ ] T075 [US2] Implement `AM/ui/HomeScreen.kt` and `AM/ui/StatusMessage.kt`:
  - `HomeScreen(state, onArrivalAnswered)` is a pure `when(state)`. `Guidance` renders `GuidanceDisplay`, and
    `Unavailable` renders `StatusMessage(stringResource(R.string.status_unavailable))`.
  - The `Driving` and `Parking` branches render `StatusMessage` with their resources, which are added in US4
    (T087).
  - `StatusMessage` is a centered `Text` tagged `STATUS_MESSAGE`.
  - Add `status_unavailable` = `Parked location unavailable.` to `app/src/main/res/values/strings.xml`.

  This covers FR-016, FR-031 and QR-008.
- [ ] T076 [US2] Create `AM/GuidanceSessionObserver.kt`, a `DefaultLifecycleObserver`. Its `onStart` calls
  `engine.setGuidanceVisible(true)` and `heading.start()`, and its `onStop` calls `setGuidanceVisible(false)` and
  `heading.stop()`. Add a `HomeScreenPresenter` to `CarFinderApplication`. Then update `AM/MainActivity.kt`:
  - Register the observer.
  - Hold `private var uiState by mutableStateOf<HomeScreenState>(HomeScreenState.Unavailable)`.
  - In `lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { presenter.state.collect { uiState
    = it } } }`, copy the presenter's state into `uiState`.
  - Call `setContent { CarFinderTheme { HomeScreen(uiState, presenter::onArrivalAnswered) } }` as the home
    screen, with no navigation.

  No Composable collects flows or runs side effects (FR-017, QR-009, Constitution IV). This makes T063 pass.
- [ ] T077 [US2] Run `.\gradlew.bat :shared:allTests :app:testDebugUnitTest`. All tests from US1 and US2 must
  pass, and the traceability report must show FR-016, FR-017, FR-019 to FR-027, FR-031, FR-034, QR-003 (partial),
  QR-008 and QR-009 traced.

**Checkpoint**: The P1 MVP is complete. Parking is detected, and the user is guided back to the car.

---

## Phase 5: User Story 3 - Arrival is recognized (Priority: P2)

**Goal**: When the cone half-angle reaches `ARRIVAL_HALF_ANGLE_DEGREES`, the cone is replaced by "You have
arrived" and the prompt "Do you see your car?". Answering it only dismisses the prompt, and it re-arms only
after the half-angle drops below the threshold.

**Independent Test**: Drive the half-angle to the threshold through the fixture accuracy and distance. Verify
that the arrival message and prompt appear and the cone is gone. Answer Yes or No, then verify that the prompt
closes, the state is PARKED, and the store is unchanged.

### Tests for User Story 3 ⚠️ (write first, must fail)

- [ ] T078 [P] [US3] Write `ST/guidance/ArrivalTest.kt` (FR-028, SC-009). It covers:
  - `isArrived` is true at exactly `ARRIVAL_HALF_ANGLE_DEGREES` and false just below it.
  - It is true whenever uncertainty ≥ distance, and false when uncertainty < distance, checked with parameterized
    pairs.
  - It is true at distance 0.
- [ ] T079 [P] [US3] Write `ST/guidance/ArrivalPromptTrackerTest.kt` (FR-028, FR-029). It covers:
  - Armed changes to Prompting when `isArrived` becomes true.
  - Answering changes Prompting to Dismissed.
  - While Dismissed and `isArrived` is still true, the prompt is not shown again.
  - Dismissed changes to Armed only when `isArrived` becomes false, after which the prompt shows again on the
    next arrival.
- [ ] T080 [P] [US3] Write `ST/engine/HomeScreenPresenterArrivalTest.kt` (FR-028, FR-029). It covers:
  - At the threshold, `Guidance.isArrived` and `isArrivalPromptVisible` are true.
  - `onArrivalAnswered(true)` and `onArrivalAnswered(false)` each hide the prompt.
  - Engine `lifecycle == PARKED`, and `InMemoryParkingStore.writes` did not change after answering.
- [ ] T081 [P] [US3] Write `AT/ui/ArrivalDisplayTest.kt` (QR-003, FR-028, FR-029). It covers:
  - With `isArrived` and the prompt visible, `GUIDANCE_CONE`, `PERSON_ICON`, `CAR_ICON` and `DISTANCE_TEXT` do not
    exist (FR-028), and `ARRIVAL_MESSAGE` reads "You have arrived" and `ARRIVAL_PROMPT` reads "Do you see your car?".
  - Clicking `ARRIVAL_YES` invokes the callback with `true`, and `ARRIVAL_NO` invokes it with `false`.
  - With `isArrived` but the prompt dismissed, the message shows and the prompt does not.
  - Just below the threshold, the cone exists and the arrival nodes do not.

### Implementation for User Story 3

- [ ] T082 [US3] Add `isArrived(halfAngleDegrees)` = `halfAngleDegrees >= ARRIVAL_HALF_ANGLE_DEGREES` to
  `SC/guidance/GuidanceCalculator.kt`, and populate `GuidanceState.isArrived` in `compute()` (FR-028). This makes
  T078 pass.
- [ ] T083 [P] [US3] Implement `SC/guidance/ArrivalPromptTracker.kt` as a state machine `Armed → Prompting →
  Dismissed` with `onArrivedChanged(isArrived)`, `onAnswered()` and `isPromptVisible`. It holds no reference to
  the engine or the store (FR-028, FR-029). This makes T079 pass.
- [ ] T084 [US3] Integrate the tracker into `SC/engine/HomeScreenPresenter.kt`. `Guidance.isArrived` and
  `isArrivalPromptVisible` come from the tracker, and `onArrivalAnswered(sawCar)` calls `tracker.onAnswered()`
  only and never touches the engine or the store (FR-029). This makes T080 pass.
- [ ] T085 [US3] Update `AM/ui/GuidanceDisplay.kt`. When `isArrived`, it does not compose the cone, the icons or
  the distance text (FR-028). It shows `Text(stringResource(R.string.arrival_message))` tagged `ARRIVAL_MESSAGE`.
  When `isArrivalPromptVisible`, it shows the prompt text tagged `ARRIVAL_PROMPT` with Yes and No `Button`s,
  tagged `ARRIVAL_YES` and `ARRIVAL_NO`, which call `onArrivalAnswered(true/false)`. Add the strings
  `arrival_message` = "You have arrived", `arrival_prompt` = "Do you see your car?", `arrival_yes` = "Yes" and
  `arrival_no` = "No". Confirm that `MainActivity` passes `presenter::onArrivalAnswered` to `HomeScreen`. This
  makes T081 pass.

**Checkpoint**: Arrival is recognized. US1 and US2 are still green.

---

## Phase 6: User Story 4 - Always-on status when there is nothing to guide to (Priority: P2)

**Goal**: The home screen always shows exactly one of the view variants, chosen by FR-016's priority order, with
the exact status wording.

**Independent Test**: Put the presenter into each lifecycle state using fakes, and render each `HomeScreenState`
variant. Assert the exact status strings through Compose semantics.

### Tests for User Story 4 ⚠️ (write first, must fail)

- [ ] T086 [P] [US4] Write `AT/ui/StatusMessagesTest.kt` (QR-003, FR-016, FR-017). It covers:
  - For `HomeScreenState.Driving`, `STATUS_MESSAGE` has exactly "Driving - Waiting to Park.".
  - For `Unavailable`, it has exactly "Parked location unavailable.".
  - For `Parking`, it has exactly "Sensing you will be Parking Soon.".
  - In each case `GUIDANCE_CONE` does not exist.
  - Use `assertTextEquals` so that wording, capitalization and punctuation are exact.
- [ ] T087 [P] [US4] Write `ST/engine/HomeScreenPresenterStatusTest.kt` (FR-016, US4 acceptance scenarios 1 to 5).
  It covers:
  - Engine DRIVING, including the first drive after a fresh install, gives `Driving`.
  - FINDING, including a fresh install, gives `Unavailable`.
  - PARKING gives `Parking`.
  - PARKED with guidance available gives `Guidance`.
  - PARKED with guidance not available gives `Unavailable`.
- [ ] T088 [P] [US4] Write `AT/MainActivityLaunchTest.kt` (FR-017, SC-006). It uses Robolectric
  `ActivityScenario.launch(MainActivity::class.java)`, with location permissions granted through
  `ShadowApplication.grantPermissions` and `TestCarFinderApplication` seeded to FINDING through
  `InMemoryParkingStore`. With no user interaction, the `STATUS_MESSAGE` node must display "Parked location
  unavailable.", which shows that the default view is the launch screen and needs no navigation. It also covers
  the case where permission is granted before `onResume`: the Activity must then issue the
  `ParkingDetectionService` start intent (spec Edge Cases, FR-033).

### Implementation for User Story 4

- [ ] T089 [US4] Add `status_driving` = `Driving - Waiting to Park.` and `status_parking` = `Sensing you will be
  Parking Soon.` to `app/src/main/res/values/strings.xml`, with the exact wording from FR-016. Complete the
  `Driving` and `Parking` branches in `AM/ui/HomeScreen.kt` using `StatusMessage`. This makes T086 pass.
- [ ] T090 [US4] Verify that `SC/engine/HomeScreenPresenter.kt` maps every `LifecycleState` through
  `DefaultViewSelector`, and adjust it if T087 fails. Also confirm that the notification text in
  `AM/service/DetectionNotification.kt` uses the same three status strings, for consistency (FR-016). This makes
  T087 pass.

**Checkpoint**: Every view variant is correct.

---

## Phase 7: User Story 5 - Driving away clears the old location (Priority: P2)

**Goal**: From PARKED, a filtered speed above the driving threshold deletes the Parked Location and enters
DRIVING, even when the app is never opened. A single spike does not trigger this. The `IN_VEHICLE` activity hint
shortens the detection latency.

**Independent Test**: From a seeded PARKED record, feed filtered-fast readings through the engine. Assert DRIVING
and a store with no location, and after re-creating the engine assert that it is not PARKED. Feed one spike among
slow readings and assert that PARKED is kept.

### Tests for User Story 5 ⚠️ (write first, must fail)

- [ ] T091 [P] [US5] Write `ST/domain/ParkingStateMachineDriveAwayTest.kt` (FR-003, FR-009, FR-015, FR-032). It
  covers:
  - PARKED plus a filtered speed above `DRIVING_SPEED_THRESHOLD_MPH` gives DRIVING with `parkedLocation == null`
    and `persist == true`.
  - PARKED with a single raw spike stays PARKED with the location kept.
  - PARKED with dead-zone or slow speeds stays PARKED.
  - PARKED never goes to FINDING and never to PARKING.
- [ ] T092 [P] [US5] Write `ST/engine/ParkingEngineDriveAwayTest.kt` (FR-013, FR-015, FR-033, SC-005). It covers:
  - Two full park cycles (PARKED, then DRIVING, then PARKING, then PARKED at a second site) leave exactly one
    Parked Location in the store, equal to the second centroid (FR-013).
  - A seeded PARKED store followed by fast readings with no UI (guidance never visible) gives a store record of
    DRIVING with no location.
  - Re-creating the engine over the store gives a state other than PARKED and no location.
  - `ActivityInVehicle` while in `IDLE_WATCH` upgrades the profile to `DRIVING` without changing the lifecycle,
    and the next lifecycle change resets the upgrade (research R2).
- [ ] T093 [P] [US5] Write `SAT/ActivityTransitionSourceTest.kt` (QR-004, research R2). It uses a Robolectric
  `FakeActivityPort` and covers:
  - An `IN_VEHICLE` ENTER transition intent emits once.
  - An EXIT or any other activity emits nothing.
  - A `SecurityException` or a denied permission results in emitting nothing, with no crash.

### Implementation for User Story 5

- [ ] T094 [US5] Complete the PARKED branch in `SC/domain/ParkingStateMachine.kt` (FR-003, FR-009, FR-015). When
  PARKED and the filtered `v > DRIVING_SPEED_THRESHOLD_MPH`, go to DRIVING with `parkedLocation = null` and
  `persist = true`. Remove the US1 placeholder marker. This makes T091 pass.
- [ ] T095 [P] [US5] Implement `SA/ActivityPort.kt` and `SA/ActivityTransitionSource.kt` (research R2):
  - It uses `ActivityRecognition.getClient(context).requestActivityTransitionUpdates` for `IN_VEHICLE`
    `ACTIVITY_TRANSITION_ENTER`, with a mutable `PendingIntent` to an internal `BroadcastReceiver`.
  - It parses the intent with `ActivityTransitionResult.extractResult`.
  - `inVehicleEntered` is a `SharedFlow<Unit>`.
  - Failures are logged and the flow emits nothing.

  Register the receiver in `app/src/main/AndroidManifest.xml` with `android:exported="false"`. This makes T093
  pass.
- [ ] T096 [US5] Update `SA/PlatformAdapters.android.kt` to wire `ActivityTransitionSource` in place of
  `NoActivitySignalSource`. Update `SC/engine/ParkingEngine.kt` so that `ActivityInVehicle` upgrades
  `IDLE_WATCH` to `DRIVING` until the next lifecycle transition, and never changes the lifecycle (research R2).
  This makes T092 pass.

**Checkpoint**: All five user stories work on their own, and the full lifecycle table is implemented.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature verification, documentation and the traceability gate.

- [ ] T097 [P] Write `ST/engine/FullLifecycleReplayTest.kt` (FR-001, FR-009, SC-001, SC-002, SC-005, SC-009,
  SC-010). It replays FINDING → DRIVING → PARKING → PARKED, then guidance, then arrival, then an answer, then
  drive-away → DRIVING, through the engine and the presenter with fakes. It asserts every intermediate
  `HomeScreenState` and every store record.
- [ ] T098 [P] Write `ST/engine/ConcurrencyTest.kt` (research R7). It sends interleaved location, activity and
  visibility inputs from multiple coroutines, and asserts that the transitions are serialized and the FR-018
  invariant holds after each emission.
- [ ] T099 [P] Create `README.md` at the repository root, covering:
  - What Car Finder does.
  - The prerequisites (JDK 17+, Android SDK, PowerShell 5.1+, Pester 5).
  - Build commands (`gradlew assembleDebug`) and test commands (the tasks from quickstart §2), and how to run the
    traceability report.
  - How to install the debug APK.
  - Links to `specs/002-park-detect-guidance/` (the spec, plan, quickstart and contracts) and to
    `.specify/memory/constitution.md`.
- [ ] T100 Create the `:benchmark` Macrobenchmark module (`com.android.test` plugin, `targetProjectPath =
  ":app"`), add `include(":benchmark")` to `settings.gradle.kts`, and add a `benchmark` build type to `:app`.
  Then:
  - Put the synthetic-guidance hook only in the `benchmark` build type's own source set, so no main code
    changes:
    - Create `app/src/benchmark/kotlin/com/packmuleforge/carfindermvp/BenchmarkCarFinderApplication.kt`. It
      extends `CarFinderApplication` and overrides `createAdapters()` to return a seeded PARKED
      `InMemoryParkingStore` plus synthetic fix and heading sources that emit at the UI sensor rate.
    - Point to it from `app/src/benchmark/AndroidManifest.xml` with `<application
      android:name=".BenchmarkCarFinderApplication" tools:replace="android:name"/>`.
    - Add `benchmarkImplementation(project(":shared-testing"))`.
  - Write `benchmark/src/main/kotlin/com/packmuleforge/carfindermvp/benchmark/GuidanceJankBenchmark.kt`
    (FR-027, SC-008). It launches the benchmark build, runs for 60 s, and uses `FrameTimingMetric` to assert
    that the 95th-percentile `frameDurationCpuMs` is at or below 16.7 ms.
  - Run it on the reference test device named in the spec's Assumptions, and record the device model and API
    level with the result in `validation-results.md`. This task cannot run while that Assumption still holds
    the placeholder.
  - It runs on a connected device with `.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest`. It is
    automated, but not part of CI at MVP (Constitution II).
- [ ] T101 Review every Composable in `AM/ui/` and `AM/MainActivity.kt` for local `remember { mutableStateOf }`
  holding domain data, and for any Composable that collects flows or runs side effects (Constitution IV). The
  Activity-held `uiState` in `MainActivity` (T076) is the approved hoisting boundary and is exempt: it only
  mirrors `presenter.state` and is never computed there. Portability of `SC/` is enforced automatically by
  `CommonMainPortabilityScanTest`. Review test files for hard-coded `3` used as a window or sample count
  (QR-002; T012 cannot catch this). Fix any finding at its source.
- [ ] T102 Run the full suite: `.\gradlew.bat :shared:allTests :app:testDebugUnitTest :app:lintDebug` and
  `Invoke-Pester tools\traceability\tests`. Everything must be green, and lint must report no errors.
- [ ] T103 Run `.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps` and commit the generated
  `specs/002-park-detect-guidance/traceability.md`. Every FR and QR must be `TRACED`, with QR-005 to QR-007 and
  the other process QRs allowed to be `UNIMPLEMENTED`, and there must be 0 orphaned tags (SC-011, Constitution
  II). For any gap, add the missing tag or test at its source.
- [ ] T104 Execute the device validation in [quickstart.md](quickstart.md) §5 (persistence survival) and §6
  (V1 to V7) on a physical API 34+ phone. Record the results, including the V6 boot-start outcome and the V7
  battery figure, in `specs/002-park-detect-guidance/validation-results.md`. This is supplementary evidence only
  and does not replace the automated tests (Constitution III).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup, and blocks every story.
- **US1 (Phase 3)**: Depends on Foundational. It provides `ParkingEngine`, the platform factory, the service and
  the manifest.
- **US2 (Phase 4)**: Depends on Foundational and on US1's `ParkingEngine` (T042) and factory (T046). Its guidance
  math tests (T053 to T056) can start right after Foundational, in parallel with US1.
- **US3 (Phase 5)**: Depends on US2's presenter and display (T069, T074).
- **US4 (Phase 6)**: Depends on US2's `HomeScreen`, `StatusMessage` and presenter (T069, T075). It is independent
  of US3.
- **US5 (Phase 7)**: Depends only on US1 (T041, T042, T046). It can run in parallel with US2, US3 and US4.
- **Polish (Phase 8)**: Depends on every story.

### Story graph

```text
Setup → Foundational → US1 ─┬─→ US2 ─┬─→ US3
                            │        └─→ US4
                            └─→ US5
```

### Within each story

- Write the tests first, and confirm they fail.
- Build in this order: pure domain, then engine and presenter, then Android adapters, then app wiring and UI.
- Finish each story's checkpoint run before moving on.

### Same-file sequencing (these tasks are not parallel with each other)

- `ParkingStateMachine.kt`: T041 → T094
- `ParkingEngine.kt`: T042 → T070 → T096
- `HomeScreenPresenter.kt`: T069 → T084 → T090
- `GuidanceCalculator.kt`: T065 → T082
- `GuidanceDisplay.kt`: T074 → T085
- `HomeScreen.kt`: T075 → T089
- `PlatformAdapters.android.kt`: T046 → T072 → T096
- `SC/platform/PlatformAdapters.kt`: T027 → T046
- `AndroidManifest.xml`: T006 → T049 → T095
- `strings.xml`: T007 → T047 → T075 → T085 → T089
- `MainActivity.kt`: T007 → T050 → T076 → T085
- `CarFinderApplication.kt`: T007 → T027 → T046 → T050 → T076
- `settings.gradle.kts`: T005 → T100

---

## Parallel Example: User Story 1

```text
# All US1 tests together (different files):
T030 SpeedMedianFilterTest   T031 ConvergenceWindowTest   T032 ParkingStateMachineParkTest
T033 ParkingEngineParkTest   T034 DataStoreParkingStoreTest   T035 FusedLocationSourceTest
T036 AndroidPermissionControllerTest   T037 ParkingDetectionServiceTest   T038 BootReceiverTest

# Then the independent implementations together:
T039 SpeedMedianFilter   T040 ConvergenceWindow   T043 DataStoreParkingStore
T044 FusedLocationSource   T045 AndroidClocks + AndroidPermissionController
```

## Parallel Example: User Story 2

```text
T053 GuidanceCalculatorTest  T054 ConeGeometryCalculatorTest  T055 FixCurrencyTest  T056 DefaultViewSelectorTest
T059 HeadingMathTest  T060 RotationVectorHeadingSourceTest  T061 GuidanceDisplayTest  T062 GuidanceRecompositionTest
T063 GuidanceSessionObserverTest  T064 ComposeOnlySourceScanTest
then: T065  T066  T067  T068  T071  T073   (all different files)
```

## Parallel Example: after US1

```text
Developer A: US2 (T053–T077) → US3 (T078–T085)
Developer B: US5 (T091–T096) → US4 (T086–T090, once T075 has landed)
```

---

## Implementation Strategy

### MVP First (P1 stories: US1 and US2)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1). Stop and validate the engine replay tests and quickstart §5 persistence on an
   emulator.
3. Complete Phase 4 (US2). Stop and validate the guidance UI tests and quickstart V2 to V4 on a device.
4. This is the demoable MVP: parking is detected and the user is guided back.

### Incremental Delivery

1. US5 (drive-away), which protects the MVP from stale locations and should come next.
2. US3 (arrival).
3. US4 (full status wording).
4. Polish: the traceability gate with `-FailOnGaps`, which is part of "done" under Constitution II.

### Constitution gate before implementation

Run `/speckit.analyze` against spec.md, plan.md and tasks.md before `/speckit.implement`. Fix any finding at its
source, and re-run until it is clean.

---

## Notes

- [P] means a different file with no incomplete dependencies. The same-file chains above are always sequential.
- Commit after each task or logical group, using Conventional Commits.
- The first `/speckit.analyze` pass (2026-09-30) was remediated at the source: spec, plan, contracts and tasks.
  Research R12 records which interpretations moved into the spec.
