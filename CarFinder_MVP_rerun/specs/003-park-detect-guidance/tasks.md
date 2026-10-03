---

description: "Task list for Automatic Park Detection & Guidance Back to the Vehicle (Run 2)"
---

# Tasks: Automatic Park Detection & Guidance Back to the Vehicle (Run 2)

**Input**: Design documents in `specs/003-park-detect-guidance/`: [plan.md](plan.md), [spec.md](spec.md),
[research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/),
[quickstart.md](quickstart.md)

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Required. QR-001 to QR-006 and constitution Principles I to III make automated tests
mandatory. Every story writes its tests first, and each test must fail before its implementation
task starts.

**Organization**: Tasks are grouped by user story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel: a different file, and no dependency on an unfinished task.
- **[Story]**: The user story the task belongs to (US1 to US7).

## Path Conventions

| Alias | Expands to |
|---|---|
| `SC/` | `shared/src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/` |
| `ST/` | `shared/src/commonTest/kotlin/com/packmuleforge/carfindermvp/shared/` |
| `SA/` | `shared/src/androidMain/kotlin/com/packmuleforge/carfindermvp/shared/platform/android/` |
| `SAT/` | `shared/src/androidHostTest/kotlin/com/packmuleforge/carfindermvp/shared/platform/android/` |
| `TS/` | `shared-testing/src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/testing/` |
| `AM/` | `app/src/main/kotlin/com/packmuleforge/carfindermvp/` |
| `AT/` | `app/src/test/kotlin/com/packmuleforge/carfindermvp/` |
| `TT/` | `tools/traceability/` |

## Rules for every task

- **Closing a task**: a task is marked `[X]` only after a build or test command has been run for it
  in the same session and its actual output shows success. A description of the result is not enough.
- **Test first**: a test task is complete when the test exists and fails for the right reason (a
  missing symbol or a failed assertion). Its implementation task makes it pass.
- **Tags**: every implementing declaration carries KDoc `@requirement <ID>[, <ID>]` for the IDs in
  its task. Every test function carries `@requirement <ID>` for what it verifies. List each ID; ranges
  are not read ([contracts/traceability.md](contracts/traceability.md)).
- **Class tags**: every `ST/` test class for the state machine, convergence, uncertainty, recovery,
  sampling and guidance math carries `@requirement QR-001`. Every `SAT/` adapter test class carries
  `@requirement QR-003`. Every replay test class carries `@requirement QR-016`.
- **Constants**: tests use `CarFinderConstants.*` by name and derive fixtures
  from them (for example `DRIVING_SPEED_THRESHOLD_MPH + 1.0`). No test repeats a constant's value
  (QR-005).
- **Portable shared code**: nothing under `SC/` or `TS/` imports `android.*` or `java.*` (QR-014).
- **Logs**: no log or diagnostic event contains coordinates.
- **Before a batch**: confirm the files its tasks name exist or are created by an earlier task.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the three modules on the existing Gradle wrapper and catalog, and prove the
empty skeleton builds and runs a test in each module.

- [X] T001 Edit `settings.gradle.kts` to include only `:app`, `:shared` and `:shared-testing`. In
  `gradle/libs.versions.toml` remove the `benchmark` and `uiautomator` versions, the
  `androidx-benchmark-macro-junit4` and `androidx-uiautomator` libraries and the `android-test`
  plugin. In `build.gradle.kts` remove the `android.test` plugin alias.
- [X] T002 Create `shared/build.gradle.kts`: Kotlin Multiplatform with the Android KMP library
  plugin and the serialization plugin; namespace `com.packmuleforge.carfindermvp.shared`;
  `minSdk = 26`; `commonMain` depends on coroutines and serialization; `androidMain` depends on
  `play-services-location` and `datastore`; `commonTest` depends on `kotlin-test`,
  `kotlinx-coroutines-test` and `project(":shared-testing")`; Android host tests enabled with
  Robolectric, `androidx-test-core` and `kotlin-test-junit`, with Android resources included.
- [X] T003 [P] Create `shared-testing/build.gradle.kts`: Kotlin Multiplatform with the Android KMP
  library plugin; namespace `com.packmuleforge.carfindermvp.shared.testing`; `minSdk = 26`;
  `commonMain` has `api(project(":shared"))` and `api` on `kotlinx-coroutines-test`.
- [X] T004 Create `app/build.gradle.kts` (Android application, Compose plugin, application id
  `com.packmuleforge.carfindermvp`, `minSdk = 26`, depends on `:shared`, Compose BOM, `activity-compose`,
  `lifecycle-runtime-compose`, `lifecycle-service`, `fragment-ktx`; unit tests depend on
  `:shared-testing`, Robolectric, `compose-ui-test-junit4`, `compose-ui-test-manifest`, with Android
  resources included), `app/src/main/AndroidManifest.xml` (application and launcher Activity only),
  `AM/CarFinderApplication.kt` (empty `open class`), `AM/MainActivity.kt` (empty Compose content) and
  `app/src/main/res/values/strings.xml` (`app_name` = "Car Finder").
- [X] T005 Add one placeholder test per module so each test task has something to run:
  `ST/SkeletonTest.kt`, `SAT/SkeletonHostTest.kt` and `AT/SkeletonAppTest.kt`, each asserting `true`.
- [X] T006 Walking-skeleton gate: run
  `.\gradlew.bat :app:assembleDebug :shared:testAndroidHostTest :app:testDebugUnitTest :app:lintDebug`
  with `JAVA_HOME` set, and fix the build files until it succeeds with the three placeholder tests
  executed. Paste the task summary lines into the commit message body.
- [X] T007 Confirm `specs/003-park-detect-guidance/analysis-findings.md` has no finding with Status
  `Open`. The plan and analyze findings were settled on 2026-10-02, and owner decision OD-1 on
  2026-10-03; AN1-C4 (PL-7) and AN1-B2 (PL-8) are `Accepted` pending device checks V9 and V3 and are
  re-examined in T119. AN2-C1 is `Accepted` as out of scope and needs no task.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Constants, value types, adapter interfaces, fakes and the traceability tool. Every
story depends on these.

**ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â CRITICAL**: No story work starts until this phase is complete.

### Tests for Foundational ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [X] T008 Write `SAT/SourceTree.kt` (test helper: locate the repository root from the working
  directory, list `.kt` files under a directory, return paths relative to the root). T009 to T012 use
  it.
- [X] T009 [P] Write `SAT/ConstantsScanTest.kt` (FR-055, QR-005): each of the 18 names in
  [data-model.md](data-model.md) `CarFinderConstants` is declared exactly once under
  `shared/src/commonMain`, in `CarFinderConstants.kt`; no file under any test source set of `:shared`
  or `:app` contains a standalone numeric token in one of these literal forms: `5.0`, `25.0`, `10.0`,
  `45.0`, `500.0`, `0.90`, `0.9`, `0.65`, and `500L`, `1_000L`, `2_000L`, `5_000L`, `20_000L`,
  `30_000L`, `180_000L` with or without the underscores. Not scanned, and covered by review instead:
  the two count constants (value 3), integer literals without an `L` suffix, and lines carrying a
  Robolectric `sdk =` configuration.
- [X] T010 [P] Write `SAT/NumericConstantScanTest.kt` (QR-007): every `const val` with a numeric
  initializer under `shared/src/commonMain` is declared in `CarFinderConstants.kt`, and every name
  declared there is either one of the 18 FR-055 names or one of the three unit-conversion factors.
- [X] T011 [P] Write `SAT/CommonMainPortabilityScanTest.kt` (QR-014): no file under
  `shared/src/commonMain` or `shared-testing/src/commonMain` contains `import android.`,
  `import androidx.` or `import java.`.
- [X] T012 [P] Write `SAT/AdapterBoundaryScanTest.kt` (QR-003): every `expect` declaration under
  `shared/src/commonMain` is either `expect class PlatformContext` or
  `expect fun createPlatformAdapters` (none exists until T047, which is allowed); `LocationSource`,
  `HeadingSource`, `ActivitySignalSource`, `ParkingStore`, `PermissionController`, `MonotonicClock`,
  `WallClock` and `DiagnosticLog` are each declared with the `interface` keyword;
  `PermissionController` declares exactly one `suspend fun`.
- [X] T013 [P] Write Pester fixtures under `TT/tests/fixtures/` (a small spec with three FR and two
  QR IDs; a source tree with a traced requirement, an untested one, an unannotated one, an orphaned
  tag, a declared no-code requirement, and a requirement tagged only by a `// @requirement` line in
  a `build.gradle.kts`) and `TT/tests/Get-TraceabilityReport.Tests.ps1` (QR-002, QR-008, QR-009,
  QR-010): ID parsing; test-versus-implementation classification by path; a `.gradle.kts` tag counts
  as an implementation tag; statuses `TRACED`, `NO-CODE`, `UNTESTED`, `NO-ANNOTATION`; orphaned-tag
  detection; a declared no-code requirement with no test tag still blocks; `-FailOnGaps` exits 1 for
  each of the three blocking conditions and 0 for a clean fixture; `build/` directories are not
  scanned; a run against the real `specs/003-park-detect-guidance/spec.md` finds exactly FR-001 to
  FR-056 and QR-001 to QR-016.
- [X] T014 [P] Write `TT/tests/FindingsLedger.Tests.ps1` (QR-011):
  `specs/003-park-detect-guidance/analysis-findings.md` exists, every table row has a unique ID and a
  non-empty Status, and any row whose Status is not `Open` has a non-empty last column.

### Implementation for Foundational

- [X] T015 [P] Create `SC/domain/CarFinderConstants.kt` (FR-055) with exactly the 18 names and
  values in [data-model.md](data-model.md), plus the three unit-conversion factors. No other file
  under `SC/` declares a numeric constant.
- [X] T016 [P] Create `SC/domain/LifecycleState.kt` (FR-001): `@Serializable enum` with `FINDING`,
  `DRIVING`, `PARKING`, `PARKED`.
- [X] T017 [P] Create `SC/domain/LocationReading.kt` (FR-009, FR-010): fields `latitude`,
  `longitude`, `accuracyMeters: Double?` ("`null` when the platform reported none; never `0.0` for
  absent"), `speedMetersPerSecond: Double?` (same rule), `receivedElapsedMillis: Long`; derived
  `speedMph: Double?`.
- [X] T018 [P] Create `SC/domain/ParkedLocation.kt` (FR-015): `@Serializable`, fields `latitude`,
  `longitude`, `accuracyMeters` ("finite and > 0", enforced with `require`),
  `declaredAtEpochMillis`.
- [X] T019 [P] Create `SC/guidance/HeadingReading.kt` (FR-033): `trueHeadingDegrees` ("in
  [0, 360)", enforced with `require`), `receivedElapsedMillis`.
- [X] T020 [P] Create `SC/persistence/PersistedParkingRecord.kt` (FR-017, FR-018): `@Serializable`
  with `schemaVersion = 1`, `state`, `parkedLocation: ParkedLocation?`; `DEFAULT` =
  `{1, FINDING, null}`. Do not add `normalized()` yet (T037).
- [X] T021 Create `SC/platform/Adapters.kt` (QR-003, FR-029, FR-047, FR-049): the eight interfaces
  exactly as in [contracts/platform-adapters.md](contracts/platform-adapters.md), with
  `PermissionController` holding exactly one suspend function, `request(capability)`; `Capability`
  (enum in the order `FINE_LOCATION`, `BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`,
  `NOTIFICATIONS`); `PermissionStatus` (`NOT_REQUESTED`, `GRANTED`, `DENIED`); `PermissionState`
  with `areRequiredGranted` ("`FINE_LOCATION` and `NOTIFICATIONS` are both `GRANTED`");
  `DenialConfirmation(capability, isClosing)` (FR-056, transient); `DiagnosticEvent` (sealed: `StoreUnreadable`,
  `RecordNormalized`, `ReadingDropped`; no coordinate fields); and `class PlatformAdapters`.
- [X] T022 Create `SC/platform/InertSources.kt`: `InertHeadingSource` (always `null`) and
  `InertActivitySignalSource` (always `false`), used by the Android factory until US2 and US6 supply
  real ones. The `expect`/`actual` factory is created whole in T047, so that `:shared` compiles at
  every step before it.
- [X] T023 [P] Create the fakes in `TS/`: `FakeLocationSource.kt` (`emit`, `isStarted`,
  `intervalHistory` of every `setIntervalMillis` argument), `FakeHeadingSource.kt` (`emit`,
  `isStarted`, `stopCount`), `FakeActivitySignalSource.kt`, `InMemoryParkingStore.kt` (`record`,
  `writes`, `seed`, `makeUnreadable()` which makes `read()` return `DEFAULT` and report
  `StoreUnreadable` to the log), `FakePermissionController.kt` (settable `PermissionStatus` per
  capability, a queue of scripted answers per capability for `request` so a test can deny and then
  grant, `requests` in the order made), `FakeClocks.kt`
  (`FakeMonotonicClock`, `FakeWallClock`, each with `advanceBy`), `RecordingDiagnosticLog.kt`, and
  `FakePlatform.kt` wiring them into `PlatformAdapters`. `FakePlatform` defaults to all four
  capabilities `GRANTED`.
- [X] T024 [P] Create `TS/Readings.kt`: builders whose values derive from `CarFinderConstants`
  (`DRIVING_MPH`, `PARKED_MPH`, `DEAD_ZONE_MPH`, `GOOD_ACCURACY_METERS`), `readingAt(...)`,
  `readingOffset(northMeters, eastMeters, accuracy, speedMph, receivedElapsedMillis)`, and
  `pairwiseTriangle(d12, d23, d13)` returning three readings with the given pairwise distances.
- [X] T025 Create `TT/Get-TraceabilityReport.ps1` and `TT/no-code-requirements.psd1` (QR-008,
  QR-009, QR-010) per [contracts/traceability.md](contracts/traceability.md). The script scans
  `*.kt`, `*.ps1` and `*.gradle.kts` under the source paths. The no-code list holds exactly QR-001,
  QR-002, QR-004, QR-005, QR-006, QR-007 and QR-011, each with its reason; QR-016 is not on it. Tag
  the script header `# @requirement QR-008, QR-009, QR-010`. Add the comment line
  `// @requirement QR-015` above `minSdk = 26` in `app/build.gradle.kts`, `shared/build.gradle.kts`
  and `shared-testing/build.gradle.kts`. This makes T013 pass.
- [X] T026 Run `.\gradlew.bat :shared:testAndroidHostTest` and `Invoke-Pester tools\traceability\tests`.
  T009 to T014 must pass. Delete `ST/SkeletonTest.kt` and `SAT/SkeletonHostTest.kt`.

**Checkpoint**: Types, interfaces, fakes and tooling exist and are green.

---

## Phase 3: User Story 1 - Parking is detected and remembered automatically (Priority: P1) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ MVP

**Goal**: The shared engine turns readings into FINDING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ DRIVING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ PARKING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ PARKED, stores the
centroid with the FR-016 radius, and keeps it across restarts.

**Independent Test**: Replay drive, slow, three converging readings through `ParkingEngine` with
fakes; the store holds PARKED and the centroid; a new engine on the same store restores it.

### Tests for User Story 1 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [X] T027 [P] [US1] Write `ST/domain/SpeedFilterTest.kt` (FR-007, FR-008): `smoothed` is `null`
  with fewer than `SPEED_FILTER_WINDOW_SIZE` speeds; it is the median, not the mean, of a full
  window (two slow and one far above the driving threshold gives the slow value); the oldest speed
  leaves when a new one arrives; `add` returns a new filter and leaves the receiver unchanged.
- [X] T028 [P] [US1] Write `ST/domain/ConvergenceWindowTest.kt` (FR-010, FR-011, FR-016): pairwise
  distances 0.4R, 0.6R, 0.9R converge and 0.4R, 0.6R, 1.1R do not (R = `CONVERGENCE_RADIUS_METERS`);
  a pair exactly R apart converges; fewer than `CONVERGENCE_SAMPLE_COUNT` readings never converge; a
  reading with `accuracyMeters == null` is not added; the oldest reading leaves when full; the
  accuracy radius equals `max(accuracy + distance to centroid)` and is larger than the mean of the
  accuracies when readings are spread; `toParkedLocation` carries the given declaration time.
- [X] T029 [P] [US1] Write `ST/domain/ParkingStateMachineParkTest.kt` (FR-001, FR-002, FR-003,
  FR-004, FR-005, FR-006, FR-008, FR-009, FR-012, FR-013, FR-014, FR-015, FR-017): initial snapshot
  is FINDING with no location; FINDING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ DRIVING above the driving threshold; DRIVING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ PARKING at or
  below the parking threshold, and exactly at it; FINDING never enters PARKING at any slow speed;
  dead-zone speeds change nothing in every state; no transition before the filter is full even for a
  very fast reading; a reading with no speed causes no transition even when the filter's smoothed
  value is past a threshold; PARKING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ PARKED on convergence with the location's
  `declaredAtEpochMillis` equal to the event time; the reading that causes PARKING entry is not in
  the window; non-converging readings slide indefinitely with no other state; PARKING ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ DRIVING
  discards the window and stores nothing; after every step `(lifecycle == PARKED) ==
  (parkedLocation != null)`; `reduce` leaves its input unchanged and is deterministic.
- [X] T030 [P] [US1] Write `ST/persistence/PersistedParkingRecordTest.kt` (FR-017, FR-020):
  `normalized()` turns PARKED with no location into FINDING; drops a location held with any state
  other than PARKED; leaves a consistent record equal to itself; JSON round-trips, including
  `declaredAtEpochMillis`.
- [X] T031 [P] [US1] Write `ST/domain/SamplingPolicyTest.kt` (FR-027, FR-028): a table-driven test
  over every combination of the four lifecycle states ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â recovery open/closed ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â guidance
  visible/hidden ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â in-vehicle true/false, asserting the five rows of
  [data-model.md](data-model.md) "Sampling interval" in order, including: PARKED with recovery open
  and guidance visible gives the parking interval; FINDING not in vehicle gives the idle interval;
  FINDING in vehicle gives the parking interval; the in-vehicle flag never lowers an interval.
- [X] T032 [P] [US1] Write `ST/engine/ParkingEngineParkTest.kt` (FR-003, FR-018, FR-020, FR-027,
  FR-029): `start()` restores a seeded PARKED record; a park sequence writes the record before the
  state is published (collect `state` and compare with the store at each emission); an unreadable
  store yields FINDING, a `StoreUnreadable` log event and a store that accepts the next write; a
  seeded inconsistent record is normalized, logged as `RecordNormalized` and rewritten once;
  `intervalHistory` follows FINDING (idle) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ DRIVING (parking) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ PARKING (parking) with no repeated
  consecutive value; `start()` twice subscribes once; `restore()` before `start()` publishes the
  stored state, starts nothing and writes nothing.
- [X] T033 [P] [US1] Write `ST/engine/ParkReplayTest.kt` (SC-001, SC-002, SC-004): drive, stop and
  converge stores the centroid on the third converging reading with no other input; a session of
  dead-zone speeds only changes nothing; a session with no drive stores nothing; a very fast first
  reading changes nothing; a second engine created on the same store after PARKED reports PARKED and
  the same location.
- [X] T034 [P] [US1] Write `SAT/FusedLocationSourceTest.kt` (FR-009, FR-010, FR-027, FR-029, FR-049)
  against a fake `FusedClientPort`: a fix that reports no accuracy maps to `accuracyMeters == null`
  even though the platform value reads `0.0`; the same for speed; non-finite or negative values map
  to `null`; out-of-range coordinates drop the reading and record `ReadingDropped`;
  `setIntervalMillis` re-issues one request with the minimum update interval equal to the interval
  and no batching, and never holds two subscriptions; the same interval twice issues one request;
  `start()` without fine-location permission issues no request and does not throw; each reading
  carries the monotonic clock's receipt time.
- [X] T035 [P] [US1] Write `SAT/DataStoreParkingStoreTest.kt` (FR-018, FR-020) on a temporary file:
  write then read round-trips state and location; a new store instance on the same file reads the
  same record; a file of garbage bytes reads as `DEFAULT`, records `StoreUnreadable`, and a following
  write and read succeed; a record with an unknown `schemaVersion` is treated the same way.

### Implementation for User Story 1

- [X] T036 [P] [US1] Implement `SC/domain/SpeedFilter.kt` (FR-007, FR-008). Makes T027 pass.
- [X] T037 [P] [US1] Add `normalized()` to `SC/persistence/PersistedParkingRecord.kt` (FR-017,
  FR-020). Makes T030 pass.
- [X] T038 [P] [US1] Implement `SC/domain/SamplingPolicy.kt` (FR-027, FR-028). Makes T031 pass.
- [X] T039 [US1] Create `SC/guidance/GeoMath.kt` with `distanceMeters` (haversine) and
  `centroid(points)`; `initialBearingDegrees` is added in T060. Implement
  `SC/domain/ConvergenceWindow.kt` (FR-010, FR-011, FR-016) including `lastReceivedElapsedMillis`.
  Makes T028 pass.
- [X] T040 [US1] Implement `SC/domain/ParkingStateMachine.kt` (FR-001, FR-002, FR-003, FR-004,
  FR-005, FR-006, FR-009, FR-012, FR-013, FR-014, FR-015, FR-017): `MachineSnapshot`, `MachineEvent`
  (`Reading`, `Restored`), `Transition` (with `persist` and `normalizedOnRestore`, as in
  [contracts/shared-domain-api.md](contracts/shared-domain-api.md)), and `reduce` implementing
  transition rules 2, 3, 4, 8 and the restore row of [data-model.md](data-model.md), and rule 1 from
  FINDING and PARKING only. PARKED ignores readings for now (US3, US6). Makes T029 pass. Depends on
  T036, T039.
- [X] T041 [US1] Implement `SC/engine/ParkingEngine.kt` (FR-003, FR-018, FR-020, FR-027, FR-029):
  `EngineState`, the single-consumer actor, `restore()`, `start()`, `stop()`, `setGuidanceVisible`,
  `transitions`; persist before publish; `SamplingPolicy` applied after every input with the request
  re-issued only on change; `RecordNormalized` logged when a restore rewrote the record. Makes T032
  and T033 pass. Depends on T037, T038, T040.
- [X] T042 [P] [US1] Create `SA/FusedClientPort.kt` (seam over the fused client) and implement
  `SA/FusedLocationSource.kt` (FR-009, FR-010, FR-027, FR-029, FR-049). Makes T034 pass.
- [X] T043 [P] [US1] Implement `SA/ParkingRecordSerializer.kt` and `SA/DataStoreParkingStore.kt`
  (FR-018, FR-020) with a corruption handler that replaces the file with `DEFAULT` and records
  `StoreUnreadable`. Makes T035 pass.
- [X] T044 [P] [US1] Implement `SA/AndroidClocks.kt` (`MonotonicClock`, `WallClock`) and
  `SA/AndroidDiagnosticLog.kt` (writes event type and count only).
- [X] T045 [P] [US1] Write `SAT/AndroidDiagnosticLogTest.kt` (FR-020): each `DiagnosticEvent` type
  produces one log line; no line contains a decimal coordinate pattern.
- [X] T046 [US1] Create `SA/StubPermissionController.kt`: an `internal` `PermissionController`
  that reports `GRANTED` or `NOT_REQUESTED` from the platform's current grants, whose `request`
  returns the current grant without prompting. It is replaced in T107.
- [X] T047 [US1] Create `SC/platform/PlatformFactory.kt` with `expect class PlatformContext` and
  `expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters`, and, in the same
  task so the module never holds an `expect` without its `actual`,
  `shared/src/androidMain/kotlin/com/packmuleforge/carfindermvp/shared/platform/PlatformFactory.android.kt`
  with `actual typealias PlatformContext = android.content.Context` and
  `actual fun createPlatformAdapters` wiring T042 to T046 with the inert heading and activity sources
  (QR-014).
- [X] T048 [US1] Implement `AM/CarFinderApplication.kt`: `open fun createAdapters()`, an
  application-scoped `engine` created once, and `open val engineDispatcher`. Create
  `AT/TestCarFinderApplication.kt` overriding `createAdapters()` with a `FakePlatform` and using an
  unconfined dispatcher.
- [X] T049 [US1] Delete `AT/SkeletonAppTest.kt`. Run
  `.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest`. Every US1 test passes.

**Checkpoint**: Parking is detected, stored and restored in the shared engine.

---

## Phase 4: User Story 2 - Guided back to the car with honest direction and distance (Priority: P1)

**Goal**: With a Parked Location, a fresh fix and a usable heading, the home screen shows the cone
and the distance; without them it says "Location unavailable".

**Independent Test**: Seed PARKED, feed a fix and a heading, and check the cone's bearing and
half-angle and the distance text against FR-030 to FR-037; stop the fix or the heading and see
"Location unavailable" with the location kept.

### Tests for User Story 2 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [X] T050 [P] [US2] Write `ST/guidance/GeoMathTest.kt` (FR-032): haversine distance and initial
  bearing against at least three published reference pairs within stated tolerances; bearing due
  north is 0, due east 90; the result is always in [0, 360); zero distance does not fail.
- [X] T051 [P] [US2] Write `ST/guidance/GuidanceCalculatorTest.kt` (FR-030, FR-031, FR-032, FR-037,
  FR-038): uncertainty is the sum of the two radii; half-angle equals `atan(u/d)` for several pairs
  and is 90 at distance zero; display bearing follows `(360 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¹ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ heading + bearing) mod 360` including
  wrap-around on both sides; distance text is whole feet at exactly `DISTANCE_UNIT_THRESHOLD_FEET`
  and miles to two decimals just above it; `isArrived` is false just below
  `ARRIVAL_CONE_HALF_ANGLE_DEGREES` and true exactly at it.
- [X] T052 [P] [US2] Write `ST/guidance/ConeGeometryCalculatorTest.kt` (FR-035, FR-036): apex and
  car anchor are `CONE_LENGTH_FRACTION / 2` either side of the center along the display bearing; the
  sweep starts at `bearing ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¹ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ halfAngle` and spans `2 ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â halfAngle`; every point of the sector lies
  within ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â±0.5 of the origin for every half-angle below the arrival half-angle; the type has no
  centerline field.
- [X] T053 [P] [US2] Write `ST/guidance/DefaultViewSelectorTest.kt` (FR-010, FR-040, FR-041,
  FR-042, FR-049, FR-056): the seven rules in order. Rule 1: a `DenialConfirmation` for either
  required capability gives `PermissionRequired` for that capability when `isClosing` is false and
  `Closing` when it is true, in every lifecycle state and whatever the `PermissionState`. Rule 2:
  with no `DenialConfirmation`, fine location or notifications anything but `GRANTED` gives
  unavailable in every lifecycle state; background location and activity recognition not granted do
  not. For rule 6 each cause on
  its own: no fix; a fix one millisecond older than `FIX_STALENESS_TIMEOUT_MILLIS` (and one exactly
  at it is fresh); a fix with no accuracy; a `null` heading; a heading one millisecond older than
  `HEADING_STALENESS_TIMEOUT_MILLIS`.
- [X] T054 [P] [US2] Write `ST/engine/HomeScreenPresenterGuidanceTest.kt` (FR-040, FR-041, FR-042,
  SC-010): seeded PARKED with a fix and a heading gives `Guidance`; advancing the clock past the fix
  timeout with no new event gives `Unavailable` within one re-check tick and at most 1 s; the same
  for the heading timeout; a `null` heading gives `Unavailable` on that emission; a new fix and
  heading give `Guidance` again; through all of it the engine's lifecycle is PARKED and the location
  is unchanged; `onGuidanceVisible(true)` starts the heading source and tells the engine, `false`
  stops it.
- [X] T055 [P] [US2] Write `ST/engine/GuidanceReplayTest.kt` (FR-044, FR-045, SC-006, SC-007,
  SC-008) using `ReplayRunner`: with a fix and heading already present, the first `Guidance` state is
  published on the visibility step itself, so the elapsed virtual time is zero and within
  `TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS`; over a walk back with seeded reading error no larger than
  each reading's reported accuracy, the true car lies inside the drawn cone, by FR-045's measure, in
  at least `CONE_CONTAINMENT_TARGET` of cone frames, with arrival and unavailable frames not counted;
  the same seed gives the same result twice; outside the recovery window each fix or heading step
  changes the published state before the next step.
- [X] T056 [P] [US2] Write `SAT/HeadingMathTest.kt` (FR-033): for each of the four display rotations
  the remap axes are the ones research R6 names and a device pointing at a known direction yields the
  same heading; declination is added and the result wraps into [0, 360); a heading computed without
  remap differs in the three non-default rotations.
- [X] T057 [P] [US2] Write `SAT/RotationVectorHeadingSourceTest.kt` (FR-034, FR-041) against a fake
  `SensorPort`: the source registers the rotation-vector sensor type and no other; it emits `null`
  when accuracy becomes unreliable and a reading again on the next reliable event; `null` when the
  device has no such sensor; it registers on `start()` and unregisters on `stop()`.
- [X] T058 [P] [US2] Write `AT/ui/GuidanceDisplayTest.kt` (FR-031, FR-035, FR-036, FR-037, QR-004,
  QR-006), tests a, b, e and h of [contracts/guidance-ui.md](contracts/guidance-ui.md), each built
  from `GuidanceCalculator` output for constant-derived inputs.
- [X] T059 [P] [US2] Write `AT/ui/GuidanceRecompositionTest.kt` (FR-046, QR-013) and
  `AT/ui/UiSourceScanTest.kt` (QR-012, QR-013): a guidance update recomposes the guidance display
  and not the surrounding screen; no file under `app/src/main/kotlin/.../ui/` imports
  `android.view`, `android.widget` or `androidx.compose.ui.viewinterop`, calls a `kotlin.math`
  trigonometric function, or collects a flow.

### Implementation for User Story 2

- [X] T060 [P] [US2] Add `initialBearingDegrees` to `SC/guidance/GeoMath.kt` (FR-032). Makes T050
  pass.
- [X] T061 [P] [US2] Implement `SC/guidance/ConeGeometry.kt` with `ConeGeometryCalculator` (FR-035,
  FR-036). Makes T052 pass.
- [X] T062 [US2] Implement `SC/guidance/GuidanceCalculator.kt` with `GuidanceState` (FR-030, FR-031,
  FR-032, FR-037, FR-038); distance text built without platform formatting. Makes T051 pass. Depends
  on T060, T061.
- [X] T063 [P] [US2] Implement `SC/guidance/DefaultViewSelector.kt` with `ViewKind` (FR-040, FR-041,
  FR-042, FR-056), taking `DenialConfirmation?` and `PermissionState`. Makes T053 pass.
- [X] T064 [US2] Create `SC/engine/HomeScreenState.kt` exactly as in [data-model.md](data-model.md)
  and implement `SC/engine/HomeScreenPresenter.kt` (FR-040, FR-041, FR-042, FR-044, QR-013): combines
  engine state, heading, permission state and the
  `CarFinderConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS` tick; `onGuidanceVisible`; emits
  `Guidance` for rule 7 and passes its `DenialConfirmation` (always `null` until T112) to the
  selector (the `Arrived` branch is US4; the permission sequence, `PermissionRequired`, `Closing`
  and `isClosePending` are US7). Makes T054 pass. Depends on T062, T063.
- [X] T065 [US2] Implement `TS/ReplayScript.kt` and `TS/ReplayRunner.kt` (QR-016, FR-045): timed
  steps, optional ground truth, a seeded error source that displaces each reported position by no
  more than its reported accuracy, recorded frames, and the cone-containment measure exactly as
  FR-045 defines it. Makes T055 pass.
- [X] T066 [P] [US2] Create `SA/SensorPort.kt` and implement `SA/HeadingMath.kt` and
  `SA/RotationVectorHeadingSource.kt` (FR-033, FR-034, FR-041). Makes T056 and T057 pass. Update
  `PlatformFactory.android.kt` to use it in place of the inert heading source.
- [X] T067 [P] [US2] Implement `AM/ui/GuidanceSemantics.kt` (the semantics keys in
  [contracts/guidance-ui.md](contracts/guidance-ui.md)) and `AM/ui/GuidanceDisplay.kt` (FR-031,
  FR-035, FR-036, FR-037, FR-046, QR-012): Canvas sector, two icons, centered distance text, one
  scale and one translation. Makes T058 pass.
- [X] T068 [US2] Implement `AM/ui/StatusMessage.kt` and `AM/ui/HomeScreen.kt` (FR-042, QR-012,
  QR-013) for `Unavailable` and `Guidance`, with "Location unavailable" in `strings.xml`. The other
  states draw nothing until their stories add them. Makes T059 pass.
- [X] T069 [US2] Add `presenter` to `AM/CarFinderApplication.kt`. Implement `AM/MainActivity.kt`
  (FR-043, QR-013): call `engine.restore()`, collect `presenter.state` with lifecycle awareness into
  `HomeScreen` as the Activity's content with no user action, and report started/stopped to
  `presenter.onGuidanceVisible`. It does not call `engine.start()`.
- [X] T070 [P] [US2] Write `AT/MainActivityObserverTest.kt` (QR-013): launching the Activity on a
  seeded PARKED store shows the restored state, calls no `start()` on any fake source, writes
  nothing to the store, and toggles guidance visibility with the Activity's started state.
- [X] T071 [US2] Run `.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest`. Every US1
  and US2 test passes.

**Checkpoint**: Guidance is computed in shared code and drawn by a stateless Compose screen.

---

## Phase 5: User Story 3 - A premature "parked" is corrected (Priority: P2)

**Goal**: Inside the recovery window a new convergence elsewhere silently replaces the Parked
Location; outside it nothing does.

**Independent Test**: Replay a brief converging stop, a creep and a second convergence inside the
window: the second location is stored with the original declaration time. Repeat with the second
stop after the window: the first location is kept.

### Tests for User Story 3 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [X] T072 [P] [US3] Write `ST/domain/ParkedLocationRecoveryWindowTest.kt` (FR-021, FR-023):
  `isRecoveryOpen` is true at the declaration time and exactly `PARKED_RECOVERY_WINDOW_MILLIS` after
  it, false one millisecond later, and false one millisecond before the declaration time.
- [X] T073 [P] [US3] Write `ST/domain/ParkingStateMachineRecoveryTest.kt` (FR-010, FR-021, FR-022,
  FR-023, FR-024, FR-025, FR-026): starting from a PARKED snapshot built directly (no DRIVING first),
  a convergence inside the window whose centroid is more than the radius away replaces the location,
  with `from == to == PARKED`, `persist == true` and the original `declaredAtEpochMillis`; a
  convergence whose centroid is within the radius changes nothing and `persist == false`; a
  convergence completed one millisecond after the window changes nothing; after a correction, a
  further convergence inside a window counted from the correction but outside the original window
  changes nothing; two corrections inside the original window both apply; readings received less
  than `SAMPLING_INTERVAL_PARKING_MILLIS` apart are thinned and three such readings do not correct;
  readings exactly the interval apart are used; readings without accuracy are not used; partly
  collected readings are discarded when the window closes (`RecoveryWindowElapsed` and a late
  reading both empty the window); a wall clock earlier than the declaration corrects nothing;
  smoothed speed above the driving threshold during the window gives no correction on that reading.
- [X] T074 [P] [US3] Write `ST/engine/ParkingEngineRecoveryTest.kt` (FR-022, FR-024, FR-027): a
  correction is written to the store and published, and nothing is emitted on `transitions`; after
  PARKED the requested interval stays the parking interval, with guidance visible too; when the
  virtual clock reaches the end of the window with no reading arriving, the interval changes to the
  guidance interval (visible) or idle interval (hidden); an engine started on a PARKED record inside
  its window recovers and schedules the timer for the remaining time; one started after the window
  does neither.
- [X] T075 [P] [US3] Write `ST/engine/RecoveryReplayTest.kt` (SC-003): brief stop, creep, second
  convergence inside the window ends with the second centroid stored; the same with the second
  convergence after the window ends with the first; park, wait past the window, walk away and settle
  keeps the location.

### Implementation for User Story 3

- [X] T076 [US3] Add `isRecoveryOpen(nowEpochMillis)` to `SC/domain/ParkedLocation.kt` (FR-021,
  FR-023). Makes T072 pass.
- [X] T077 [US3] Add `MachineEvent.RecoveryWindowElapsed` and transition rules 5, 6 and 7 of
  [data-model.md](data-model.md) to `SC/domain/ParkingStateMachine.kt` (FR-021, FR-022, FR-023,
  FR-024, FR-025, FR-026). The recovery path reads no activity signal. Makes T073 pass.
- [X] T078 [US3] In `SC/engine/ParkingEngine.kt` (FR-024, FR-027) pass `isRecoveryOpen` to
  `SamplingPolicy`, schedule one window-end timer on entering PARKED, on restoring inside a window
  and after a correction, and feed `RecoveryWindowElapsed` to the reducer when it fires. Makes T074
  and T075 pass.
- [X] T079 [US3] Run `.\gradlew.bat :shared:testAndroidHostTest`. All shared tests pass.

**Checkpoint**: A premature PARKED is corrected inside the window and never outside it.

---

## Phase 6: User Story 4 - Arrival is recognized (Priority: P2)

**Goal**: At the arrival half-angle the cone gives way to "You have arrived" and a prompt that
either answer dismisses, with no other effect.

**Independent Test**: Raise the half-angle to the threshold: message and prompt appear. Answer: the
prompt is gone, the state is PARKED and the location is unchanged.

### Tests for User Story 4 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [ ] T080 [P] [US4] Write `ST/guidance/ArrivalPromptTest.kt` (FR-039): armed ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ prompting when
  arrival begins; prompting ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ dismissed on an answer; dismissed stays dismissed while arrival holds
  and across updates with no guidance state; dismissed ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ armed only after a non-arrived guidance
  state; the next arrival prompts again.
- [ ] T081 [P] [US4] Write `ST/engine/HomeScreenPresenterArrivalTest.kt` (FR-038, FR-039, SC-009):
  `Arrived(isPromptVisible = true)` when uncertainty is at least the distance and `Guidance` when it
  is less; `onArrivalAnswered()` gives `Arrived(false)` and leaves the engine's lifecycle and
  location unchanged and the store unwritten; an unavailable spell inside the arrival zone does not
  bring the prompt back; leaving and re-entering the arrival zone does.
- [ ] T082 [P] [US4] Write `AT/ui/ArrivalDisplayTest.kt` (FR-038, FR-039, QR-004, QR-006), tests c
  and f of [contracts/guidance-ui.md](contracts/guidance-ui.md): the arrival message and the prompt
  are shown and the cone, icons and distance nodes do not exist; tapping Yes removes the prompt node
  and the message stays; the same for No; after dismissal, recreating the Activity for rotation does
  not show the prompt.

### Implementation for User Story 4

- [ ] T083 [P] [US4] Implement `SC/guidance/ArrivalPrompt.kt` (FR-039). Makes T080 pass.
- [ ] T084 [US4] Add the `Arrived` branch and `onArrivalAnswered()` to
  `SC/engine/HomeScreenPresenter.kt` (FR-038, FR-039). Makes T081 pass.
- [ ] T085 [US4] Implement `AM/ui/ArrivalPrompt.kt` (a Compose dialog) and the `Arrived` branch of
  `AM/ui/HomeScreen.kt`, with "You have arrived", "Do you see your car?", "Yes" and "No" in
  `strings.xml` (FR-038, FR-039, QR-012). Makes T082 pass.
- [ ] T086 [US4] Run `.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest`.

**Checkpoint**: Arrival is shown and dismissed without touching the lifecycle.

---

## Phase 7: User Story 5 - The screen always says something true (Priority: P2)

**Goal**: Every reachable combination of inputs shows exactly one view, by FR-042's order.

**Independent Test**: Drive the presenter through every combination and check the view; launch the
app fresh and see the default view with no interaction.

### Tests for User Story 5 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [ ] T087 [P] [US5] Write `ST/engine/DefaultViewMatrixTest.kt` (FR-002, FR-042, FR-049): through
  the presenter, with no denial confirmation pending (rule 1 is covered by T053 and T099), for every
  combination of fine location granted/not ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â notifications granted/not ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â the four lifecycle states ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â
  fix none/stale/no-accuracy/fresh ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â
  heading none/stale/fresh, exactly one `HomeScreenState` results and it is the one FR-042 names; a
  missing required permission leaves the engine's lifecycle and location unchanged; losing the fix
  or heading while PARKED never changes the lifecycle.
- [ ] T088 [P] [US5] Write `AT/ui/StatusMessagesTest.kt` (FR-042, QR-004), test g: `Unavailable`
  shows exactly "Location unavailable", `Driving` exactly "Driving", `Parking` exactly "Sensing you
  will be parking soon", and no cone node exists in any of them.
- [ ] T089 [P] [US5] Write `AT/MainActivityLaunchTest.kt` (FR-043, QR-004), test d: on a fresh
  store the Activity shows "Location unavailable" with no interaction; on a seeded PARKED store with
  a fix and heading emitted it shows the cone with no interaction.

### Implementation for User Story 5

- [ ] T090 [US5] Complete `AM/ui/StatusMessage.kt` and `AM/ui/HomeScreen.kt` for `Driving` and
  `Parking`, with their texts in `strings.xml` (FR-042). Fix anything T087 shows in
  `SC/engine/HomeScreenPresenter.kt`. Makes T087, T088 and T089 pass.
- [ ] T091 [US5] Run `.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest`.

**Checkpoint**: The default view is total and correct.

---

## Phase 8: User Story 6 - Driving away clears the old location (Priority: P2)

**Goal**: From PARKED, smoothed speed above the driving threshold deletes the location and stores
DRIVING, and the idle sampling rate rises when the phone is in a vehicle.

**Independent Test**: From a seeded PARKED record, replay fast readings: the store holds DRIVING and
no location, and a new engine on that store agrees.

### Tests for User Story 6 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [ ] T092 [P] [US6] Write `ST/domain/ParkingStateMachineDriveAwayTest.kt` (FR-004, FR-006, FR-007,
  FR-019, FR-026): PARKED ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ DRIVING when smoothed speed exceeds the threshold, with the location
  `null` and `persist == true`; one fast reading among slow ones keeps PARKED and the location;
  dead-zone and slow speeds keep PARKED; PARKED never becomes FINDING or PARKING; inside the
  recovery window a fast smoothed speed drives away and does not correct.
- [ ] T093 [P] [US6] Write `ST/engine/ParkingEngineDriveAwayTest.kt` (FR-019, FR-026, FR-028,
  SC-005): seeded PARKED then fast readings writes state DRIVING and no location in one store write;
  a new engine on that store is not PARKED and holds nothing; two park cycles leave exactly the
  second location; in FINDING and in PARKED outside the window, `isInVehicle = true` raises the
  requested interval to the parking interval and `false` returns it to idle, and neither changes the
  lifecycle or stops the location source; inside an open recovery window the same recovery script
  gives the same correction with `isInVehicle` true, false, and changing part-way.
- [ ] T094 [P] [US6] Write `SAT/ActivityTransitionSourceTest.kt` (FR-028, FR-049) against a fake
  `ActivityPort`: `isInVehicle` becomes true on the enter transition and false on the exit
  transition; other activities change nothing; `start()` without activity-recognition permission
  registers nothing, leaves `false` and does not throw.

### Implementation for User Story 6

- [ ] T095 [US6] Extend transition rule 1 in `SC/domain/ParkingStateMachine.kt` to PARKED (FR-004,
  FR-019, FR-026): delete the location, empty the window, persist. Makes T092 pass.
- [ ] T096 [US6] In `SC/engine/ParkingEngine.kt` (FR-028) collect `activity.isInVehicle` as an
  engine input passed to `SamplingPolicy`, and cancel the window-end timer on leaving PARKED. Makes
  T093 pass.
- [ ] T097 [P] [US6] Create `SA/ActivityPort.kt` and implement `SA/ActivityTransitionSource.kt`
  (FR-028, FR-049). Makes T094 pass. Update `PlatformFactory.android.kt` to use it in place of the
  inert activity source, and delete `SC/platform/InertSources.kt` if nothing else uses it.
- [ ] T098 [US6] Run `.\gradlew.bat :shared:testAndroidHostTest`.

**Checkpoint**: No stale location survives a drive-away.

---

## Phase 9: User Story 7 - Detection keeps running, within what the user allowed (Priority: P2)

**Goal**: Detection runs in a foreground service behind the specified notification, starts only
with permission, restarts after reboot when allowed, and never crashes on any permission state.

**Independent Test**: With fakes, walk every permission combination, including boot and a sticky
restart: no crash, no sensing without permission, the right notification text and the right view.
Then run quickstart ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§5 on an emulator or device.

### Tests for User Story 7 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â (write first, must fail)

- [ ] T099 [P] [US7] Write `ST/platform/PermissionSequenceTest.kt` (FR-048, FR-056) for the shared
  `requestPermissionsInOrder` with `FakePermissionController` and a recording `confirmDenial`: with
  all four `NOT_REQUESTED` and every answer a grant it calls `request` four times in the order fine
  location, background location, activity recognition, notifications, each call starting only after
  the previous one returned, never calls `confirmDenial`, and returns `Completed`; a `GRANTED`
  capability is not requested; background location or activity recognition that is `DENIED` is not
  requested again, and denying either goes on to the next capability without `confirmDenial`; a
  `DENIED` fine location or notifications is requested again; when fine location is denied and
  `confirmDenial` answers `true` it returns `CloseConfirmed(FINE_LOCATION)` and no other capability
  is requested; when it answers `false` fine location is requested again, and a grant on that
  second request continues the sequence; the same two cases for notifications; background location
  is never requested while fine location is not `GRANTED`; a second call after everything was
  granted makes no request. Write `ST/engine/HomeScreenPresenterPermissionTest.kt` (FR-042, FR-049,
  FR-056) with virtual time: a denied required request makes the state `PermissionRequired` for that
  capability in every lifecycle state; `onDenialDismissed` clears the confirmation and requests the
  same capability again; `onDenialConfirmed` makes the state `Closing`, and `isClosePending` turns
  `true` exactly `SHUTDOWN_NOTICE_DURATION_MILLIS` later and not before; it stays `true` with no
  collector until `onClosed`; no other capability is requested after the confirmation; a dismissal
  never sets `isClosePending`; `runPermissionSequence` makes no request while a sequence is running
  or `isClosePending` is `true`; after `onClosed` the state is no longer `PermissionRequired` or
  `Closing`, `isClosePending` is `false`, and a new `runPermissionSequence` requests the denied
  capability again (AN2-U1); `cancelPermissionSequence` while waiting on a request, while waiting on
  a confirmation, and during the closing wait each clear the confirmation and the guard, so a new
  `runPermissionSequence` requests again (AN2-U2); a request that throws clears the guard; the
  engine's lifecycle, Parked Location and store writes are unchanged throughout. Write
  `SAT/AndroidPermissionControllerTest.kt` (FR-047, FR-048, FR-049, FR-056) with a test
  `ActivityResultRegistry`: `request` launches exactly one platform request for exactly one
  capability, and for fine location that one request names both fine and coarse location; coarse
  granted without fine returns `false` and reports `DENIED`; after a denial the status is `DENIED`
  and a new controller instance on the same context still reports `DENIED`; a capability with no
  runtime grant on the running API level is `GRANTED` and `request` launches nothing (background
  location and activity recognition on API 28, notifications on API 32); `refresh()` turns any
  status into `GRANTED` after a grant made outside the app, and a required permission revoked
  outside the app back into `DENIED`; no call throws when no Activity is attached; a request
  launched from one registry, followed by `detach` and `attach` to a new registry that receives the
  result under the same key, resumes the pending `request` with that result, and `detach` alone
  does not complete it (AN2-U2).
- [ ] T100 [P] [US7] Write `AT/service/ParkingDetectionServiceTest.kt` (FR-049, FR-050, FR-051,
  FR-052): with fine location and notifications granted, a start command posts the foreground
  notification, calls `engine.start()` and returns sticky; with either one not granted, the service
  stops itself, posts nothing and the fake location source is never started; a second start command
  with a required permission since revoked (the sticky-restart path, `intent == null`) stops the
  service; `onDestroy` stops the engine.
- [ ] T101 [P] [US7] Write `AT/service/DetectionNotificationTest.kt` (FR-051, FR-054): title is
  exactly "Car Finder"; with background location granted the text is exactly "Monitoring for
  parking"; without it the text contains "Allow all the time" and the content intent opens the app's
  details settings.
- [ ] T102 [P] [US7] Write `AT/service/BootReceiverTest.kt` (FR-052, FR-053, FR-054): on boot with
  background location and both required permissions granted the service is started; with only
  foreground location it is not; with no location it is not; with notifications not granted it is
  not; an unrelated action does nothing.
- [ ] T103 [P] [US7] Write `AT/MainActivityPermissionFlowTest.kt` (FR-048, FR-049, FR-052, FR-054,
  FR-056): at launch with nothing requested and every answer a grant, the four capabilities are
  requested once each in order; relaunching requests nothing; with both required permissions
  granted the service start intent is sent; with fine location denied the location confirmation is
  shown and no service intent is sent; pressing back there requests fine location again; tapping
  "Close" shows "Car Finder is closing.", requests nothing else, and after
  `SHUTDOWN_NOTICE_DURATION_MILLIS` the Activity is finishing, a service stop was requested and
  `presenter.onClosed()` was called, with no exception; when the Activity is stopped during the
  closing message and started again after the duration, it closes on that start; launching a new
  Activity in the same application instance after a close shows neither `Closing` nor a stale
  confirmation and requests the denied permission again (AN2-U1); recreating the Activity while a
  prompt is pending delivers the answer and the sequence continues, and finishing it while a prompt
  or the confirmation is pending lets the next Activity in the same application instance ask again
  (AN2-U2); the same for notifications denied after the other three; after a confirmed denial,
  relaunching requests that permission again; returning to the foreground calls `refresh()`, asks
  again for a required permission that has since been revoked, and, if both required permissions
  are now granted, starts the service; the Activity never calls `engine.start()`. Write
  `AT/ui/PermissionConfirmationTest.kt` (FR-056, QR-004, QR-006), test k of
  [contracts/guidance-ui.md](contracts/guidance-ui.md).
- [ ] T104 [P] [US7] Write `AT/PermissionMatrixTest.kt` (FR-049, SC-011): for every combination of
  granted and not granted across the four capabilities, launching the Activity, delivering boot, and
  delivering a start command to the service throws nothing, the fake location source is started
  only when fine location and notifications are both granted, and launching with either one denied
  shows `PermissionRequired` rather than crashing or closing on its own.
- [ ] T105 [P] [US7] Write `AT/ManifestTest.kt` (FR-047, FR-051, QR-015): the merged manifest
  declares fine and coarse location, background location, activity recognition, post notifications,
  foreground service, foreground service location and receive boot completed; the service declares
  the location foreground-service type and is not exported; the receiver listens for boot completed;
  the application's minimum SDK is 26.
- [ ] T106 [P] [US7] Write `AT/EngineStartOwnershipScanTest.kt` (FR-052): under `app/src/main`, the
  text `engine.start(` appears only in `service/ParkingDetectionService.kt`.

### Implementation for User Story 7

- [ ] T107 [US7] Implement `SC/platform/PermissionSequence.kt` with `PermissionSequenceResult` and
  the top-level `suspend fun requestPermissionsInOrder(controller, confirmDenial)` exactly as in
  [contracts/shared-domain-api.md](contracts/shared-domain-api.md) (FR-048, FR-056), and
  `SA/AndroidPermissionController.kt` (FR-047, FR-048, FR-049, FR-056), attachable to and detachable
  from an Activity's result registry, requesting fine and coarse location together for
  `FINE_LOCATION` and counting only a fine grant, keeping its "requested" marks in a private
  preferences file, holding the pending request itself and re-registering its launcher under one
  fixed key on every `attach`. The interface keeps exactly one suspend function. Makes
  `PermissionSequenceTest` and `AndroidPermissionControllerTest` of T099 pass. Use the controller in `PlatformFactory.android.kt` and delete
  `SA/StubPermissionController.kt`.
- [ ] T108 [US7] Complete `app/src/main/AndroidManifest.xml` (FR-047, FR-051, QR-015): the
  permissions, the service and the receiver of T105. Makes T105 pass.
- [ ] T109 [P] [US7] Implement `AM/service/DetectionNotification.kt` (FR-051, FR-054) with its
  channel and both texts in `strings.xml`. Makes T101 pass.
- [ ] T110 [US7] Implement `AM/service/ParkingDetectionService.kt` (FR-049, FR-050, FR-051,
  FR-052), checking `areRequiredGranted` on every start command. Makes T100 and T106 pass. Depends on
  T109.
- [ ] T111 [P] [US7] Implement `AM/service/BootReceiver.kt` (FR-052, FR-053, FR-054). Makes T102
  pass.
- [ ] T112 [US7] Add `runPermissionSequence`, `onDenialConfirmed`, `onDenialDismissed`, the
  `onClosed`, `cancelPermissionSequence`, the `DenialConfirmation` state and `isClosePending` to
  `SC/engine/HomeScreenPresenter.kt` (FR-042,
  FR-048, FR-056), as the presenter guarantees in
  [contracts/shared-domain-api.md](contracts/shared-domain-api.md). Add the `PermissionRequired`
  dialog and the `Closing` message to `AM/ui/HomeScreen.kt`, with their texts, "Close" and "Allow"
  in `strings.xml` (FR-056). In `AM/MainActivity.kt` (FR-048, FR-049, FR-054, FR-056) attach the
  permission controller; on each start call `refresh()` and `presenter.runPermissionSequence()`;
  start the service when `areRequiredGranted`; whenever `isClosePending` is `true` while started,
  stop the service, call `finishAndRemoveTask()` and then `presenter.onClosed()`; in `onDestroy`,
  when finishing and not changing configuration, call `presenter.cancelPermissionSequence()`. Makes `HomeScreenPresenterPermissionTest` of T099, and T103 and T104,
  pass.
- [ ] T113 [US7] Run `.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest :app:lintDebug`.
  All tests pass and lint reports no errors.
- [ ] T114 [US7] Run quickstart ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§5 checks O1 to O13 on an emulator or device (O10 needs Android 12+) and record each result
  in `specs/003-park-detect-guidance/validation-results.md`. T107 to T112 are not closed until this
  is done; any failure is logged in `analysis-findings.md` and fixed at its source.

**Checkpoint**: The app runs on a phone, in the background, within its permissions.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T115 [P] Write `ST/engine/FullTripReplayTest.kt` (FR-001, SC-012): one script through park,
  early-stop correction, guidance, arrival, an answer, and drive-away, asserting the lifecycle at
  each stage and that the requested interval equals `SamplingPolicy.intervalFor` for the engine's
  inputs after every step.
- [ ] T116 [P] Rewrite `README.md`: what the app does, prerequisites (including `JAVA_HOME`), build,
  test, traceability report, install on a phone, links to this feature's documents. Remove every
  reference to `specs/002-park-detect-guidance` and to the benchmark module.
- [ ] T117 Run `.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps`. Add missing tags until
  it exits 0 with every FR and QR `TRACED` or `NO-CODE` and no orphaned tags (SC-013). Keep the
  generated `specs/003-park-detect-guidance/traceability.md`.
- [ ] T118 Run the full gate:
  `.\gradlew.bat :app:assembleDebug :shared:testAndroidHostTest :app:testDebugUnitTest :app:lintDebug`
  and `Invoke-Pester tools\traceability\tests`. Record the test counts and lint result in
  `specs/003-park-detect-guidance/validation-results.md`.
- [ ] T119 Run quickstart ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§6 field checks V1 to V10 on a phone and record them in
  `specs/003-park-detect-guidance/validation-results.md`. V2 is the binding measurement for FR-044.
  Use V9's reading-spacing measurement to close or reopen finding AN1-C4, and V3 for AN1-B2, in
  `analysis-findings.md`. This is evidence in addition to the automated tests, not in place of them.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup**: none.
- **Foundational**: Setup. Blocks every story.
- **US1**: Foundational.
- **US2**: US1 (engine T041, application T048). T050 to T053 and T056 to T057 can start right after
  Foundational.
- **US3**: US1 (T039 to T041). Independent of US2 except T074's guidance-visible case, which needs
  only `setGuidanceVisible` from T041.
- **US4**: US2 (T064, T068).
- **US5**: US2 (T064, T068). Independent of US4.
- **US6**: US1 (T040, T041) and US3 (T077, T078), because both edit the PARKED branch and the
  engine's timer.
- **US7**: US1 (T048) and US2 (T069).
- **Polish**: every story.

```text
Setup ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Foundational ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US1 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US2 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US4
                            ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡        ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US5
                            ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡        ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US7
                            ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US3 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US6
```

### Same-file sequences (never parallel with each other)

- `ParkingStateMachine.kt`: T040 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T077 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T095
- `ParkingEngine.kt`: T041 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T078 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T096
- `HomeScreenPresenter.kt`: T064 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T084 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T090 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T112
- `ParkedLocation.kt`: T018 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T076
- `PersistedParkingRecord.kt`: T020 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T037
- `GeoMath.kt`: T039 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T060
- `PlatformFactory.android.kt`: T047 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T066 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T097 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T107
- `HomeScreen.kt`: T068 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T085 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T090 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T112
- `strings.xml`: T004 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T068 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T085 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T090 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T109 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T112
- `MainActivity.kt`: T004 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T069 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T112
- `CarFinderApplication.kt`: T004 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T048 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T069
- `AndroidManifest.xml`: T004 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T108

### Within each story

Tests first and failing; pure domain, then engine and presenter, then Android adapters, then app
wiring and UI; the story's run task last.

## Parallel Examples

```text
# Foundational tests, then types:
T008, then T009 T010 T011 T012 T013 T014
T015 T016 T017 T018 T019 T020   then T021 ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ T022,  T023 T024

# US1 tests together, then independent implementations:
T027 T028 T029 T030 T031 T032 T033 T034 T035
T036 T037 T038 T042 T043 T044

# US2 tests together:
T050 T051 T052 T053 T054 T055 T056 T057 T058 T059

# After US2, by two people:
A: US4 (T080ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“T086) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US5 (T087ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“T091) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US7 (T099ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“T114)
B: US3 (T072ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“T079) ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ US6 (T092ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“T098)
```

## Implementation Strategy

### MVP first

1. Setup and Foundational.
2. US1: validate with the replay tests.
3. US2: validate with the guidance and Compose tests.
4. US7: required before the app can run on a phone, because it supplies the permissions and the
   service. US1 and US2 are demonstrable through automated tests alone; the phone MVP is US1, US2
   and US7.

### Then

5. US3 and US6 (both change what PARKED does with a reading).
6. US4 and US5.
7. Polish: the traceability gate with `-FailOnGaps` is part of done (Constitution II).

### Gate before implementation

Run `/speckit-analyze` on spec.md, plan.md and tasks.md. Record every finding in
`analysis-findings.md`, fix each at its source, and re-run until clean.

## Notes

- Commit after each task or logical group, with Conventional Commits.
- The plan-stage and analyze-stage findings were settled on 2026-10-02 and are recorded in
  `analysis-findings.md`. Two are accepted pending device evidence (AN1-C4, AN1-B2).
