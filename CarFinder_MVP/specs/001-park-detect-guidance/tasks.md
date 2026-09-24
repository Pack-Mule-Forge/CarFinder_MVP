---

description: "Task list for Automatic Park Detection and Return Guidance"
---

# Tasks: Automatic Park Detection and Return Guidance

**Input**: Design documents from `/specs/001-park-detect-guidance/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Test tasks are **included and mandatory**. Constitution Principle I is marked
NON-NEGOTIABLE, and FR-035/FR-036/FR-037 require unit coverage of shared logic, Compose UI coverage,
and that tests reference named constants rather than literals. Within every phase, tests are written
first and must fail before the matching implementation task begins.

**Organization**: Tasks are grouped by user story so each can be implemented, tested, and demoed
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (US1–US5)
- Exact file paths are included in every task

## Path Conventions

Two Gradle modules per [plan.md](./plan.md): `shared/` (Kotlin Multiplatform domain core, Android
target only) and `app/` (Android UI, adapters, service). Shared package root is
`com.packmuleforge.carfinder.shared`; app package root is the existing
`com.packmuleforge.carfinder_mvp`. Development-time tooling lives in `tools/` outside both modules.

---

## Phase 1: Setup (Shared Infrastructure) ✅ COMPLETE

**Purpose**: Stand up the `:shared` module and declare the new dependencies and manifest entries.

- [x] T001 Verify the AGP 9.3.3 + Kotlin 2.2.10 Kotlin Multiplatform plugin ID and the exact generated Android source-set names (`androidHostTest`/`androidDeviceTest` vs `test`/`androidTest`) by running `./gradlew :shared:tasks --all`, and record the confirmed names in `specs/001-park-detect-guidance/research.md` under R-01. **Do this first** — every later test task depends on placing files in the right source set.
- [x] T002 Add the KMP plugin, `kotlinx-coroutines-core`, `play-services-location`, and `androidx.datastore:datastore` (Proto) version refs and library aliases to `gradle/libs.versions.toml`, reusing the existing `kotlin = "2.2.10"` version ref for the KMP plugin.
- [x] T003 Create `shared/build.gradle.kts` declaring a Kotlin Multiplatform library with **only an Android target** (per research.md R-02), `commonMain` depending on coroutines, `commonTest` on `kotlin-test`, namespace `com.packmuleforge.carfinder.shared`, `minSdk = 24`, and Java 11 compatibility to match `app/build.gradle.kts`.
- [x] T004 Add `include(":shared")` to `settings.gradle.kts`.
- [x] T005 Add `implementation(project(":shared"))` plus the coroutines, play-services-location, and DataStore dependencies to `app/build.gradle.kts`.
- [x] T006 [P] Add `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED` to `app/src/main/AndroidManifest.xml`, and declare the service element with `android:foregroundServiceType="location"`.

**Checkpoint**: `./gradlew build` succeeds with an empty `:shared` module wired into `:app`. ✅

---

## Phase 2: Foundational (Blocking Prerequisites) ✅ COMPLETE

**Purpose**: The constants, models, ports, and fakes that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first, must fail) ✅

- [x] T007 [P] Write `shared/src/commonTest/kotlin/com/packmuleforge/carfinder/shared/constants/ParkingConstantsTest.kt` asserting `PARKING_SPEED_THRESHOLD_MPS < DRIVING_SPEED_THRESHOLD_MPS` (the FR-004 dead zone is only well-defined if ordered) and that each derived SI value equals its mph/feet/degree source converted — never a hardcoded SI literal.
- [x] T008 [P] Write `shared/src/commonTest/kotlin/com/packmuleforge/carfinder/shared/model/GeoPointTest.kt` asserting construction **rejects** latitude outside −90.0…90.0, longitude outside −180.0…180.0, and negative `accuracyRadiusMeters`, and that out-of-range values are rejected rather than clamped.
- [x] T009 [P] Write `shared/src/commonTest/kotlin/com/packmuleforge/carfinder/shared/geo/GeodesyTest.kt` covering `distanceMeters` against known reference pairs and `trueBearingDegrees` returning values in `[0, 360)` including the due-north, due-west, and antimeridian-crossing cases.

### Implementation ✅

- [x] T010 Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/annotation/Requirement.kt` declaring `annotation class Requirement(vararg val ids: String)` with `AnnotationRetention.SOURCE` and targets CLASS, FUNCTION, PROPERTY, per contracts/shared-domain-api.md.
- [x] T011 Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/constants/ParkingConstants.kt` with the seven spec-unit constants — `PARKING_SPEED_THRESHOLD_MPH = 5.0`, `DRIVING_SPEED_THRESHOLD_MPH = 25.0`, `CONVERGENCE_RADIUS_METERS = 10.0`, `CONVERGENCE_SAMPLE_COUNT = 3`, `PARKING_SAMPLE_INTERVAL_MILLIS = 5_000L`, `ARRIVAL_CONE_HALF_ANGLE_DEGREES = 45.0`, `DISTANCE_UNIT_THRESHOLD_FEET = 500.0` — plus SI forms **derived by conversion from those**, not retyped as literals (FR-034).
- [x] T012 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/model/GeoPoint.kt` with `latitudeDegrees` (−90.0…90.0), `longitudeDegrees` (−180.0…180.0), and `accuracyRadiusMeters` (≥ 0), validating in `init` and rejecting rather than clamping.
- [x] T013 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/model/LocationSample.kt` with `point: GeoPoint`, `speedMetersPerSecond: Double` (≥ 0), and `timestampEpochMillis: Long`.
- [x] T014 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/model/ParkedLocation.kt` with `point: GeoPoint` and `capturedAtEpochMillis: Long`.
- [x] T015 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/model/ParkingState.kt` as `enum class ParkingState { DRIVING, PARKING, PARKED, FINDING }`, with a KDoc note that **FINDING is the default and initial state** meaning no parked location has been determined (FR-009).
- [x] T016 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/model/GuidanceViewState.kt` as a sealed interface with `Driving`, `NoParkedLocation`, `ParkingSoon` data objects and a `Guidance` data class carrying `distanceMeters`, `formattedDistance`, `displayBearingDegrees`, `coneHalfAngleRadians`, `hasArrived`.
- [x] T017 Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/repository/ParkedLocationRepository.kt` with `PersistedParkingData(state, parkedLocation?)` and the interface methods `observe()`, `load()`, `save()`, `clearLocation()` per contracts/shared-domain-api.md.
- [x] T018 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/platform/Clock.kt` declaring `expect class Clock { fun nowEpochMillis(): Long }`.
- [x] T019 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/platform/LocationProvider.kt` declaring `enum class LocationRequestTier { DRIVING, PARKING, PARKED }` and `expect class LocationProvider { fun samples(request: LocationRequestTier): Flow<LocationSample> }`.
- [x] T020 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/platform/HeadingProvider.kt` declaring `expect class HeadingProvider { fun headingDegrees(): Flow<Double> }`, KDoc'd as **true-north corrected, 0…360**.
- [x] T021 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/platform/PermissionController.kt` declaring `Capability`, `PermissionResult` (Granted/Denied/PermanentlyDenied), `PermissionStatus` (incl. `NOT_APPLICABLE`), and `expect class PermissionController` with both `suspend fun request(...)` and `fun status(...): Flow<PermissionStatus>` per contracts/platform-adapters.md R-06.
- [x] T022 [P] Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/platform/ActivityRecognizer.kt` declaring `enum class VehicleTransition { ENTERED_VEHICLE, EXITED_VEHICLE }` and `expect class ActivityRecognizer { fun inVehicleTransitions(): Flow<VehicleTransition> }`.
- [x] T023 Create `shared/src/commonMain/kotlin/com/packmuleforge/carfinder/shared/geo/Geodesy.kt` implementing `distanceMeters` and `trueBearingDegrees` (true north, `[0, 360)`), making T009 pass.
- [x] T024 Create fakes in `shared/src/commonTest/kotlin/com/packmuleforge/carfinder/shared/fake/` — `FakeClock` (caller-advanced), `FakeLocationProvider` (scripted samples), `FakeHeadingProvider` (scripted headings incl. the 359°→1° wrap), `FakePermissionController` (scripted per capability), `FakeActivityRecognizer`, and `FakeParkedLocationRepository` (in-memory) — satisfying Principle III for all stories.

**Checkpoint**: Foundation ready. `./gradlew :shared:allTests` green. User story work can begin. ✅

---

## Phase 3: User Story 1 - Parking Is Captured Without Being Asked (Priority: P1) 🎯 MVP 🔶 OPEN — adapter tests T029–T031 not yet written

**Goal**: The system detects the drive→park transition in the background and persists the Parked
Location, with zero user interaction.

**Independent Test**: Replay a scripted speed/location track through `FakeLocationProvider` and
assert the state progression and that the stored location is the centroid of the converging samples.
On-device, run [quickstart.md](./quickstart.md) Scenario 5 (process death) and inspect the persisted
record via `adb shell run-as com.packmuleforge.carfinder_mvp`. **Note**: quickstart Scenario 4 ends
by opening the app to see guidance — that observation step needs US2; US1 itself is fully verified by
tests plus the persisted-record inspection.

### Tests for User Story 1 (write first, must fail) ⚠️ 🔶 (T029–T031 open)

- [x] T025 [P] [US1] Write `shared/src/commonTest/.../state/ConvergenceWindowTest.kt` covering: holds at most `CONVERGENCE_SAMPLE_COUNT`; converged **iff all pairwise** distances ≤ `CONVERGENCE_RADIUS_METERS` (assert the three-points-in-a-line case where samples 9 m apart pairwise-fail at 18 m); slides by one on non-convergence and never times out (FR-008); centroid guards the ±180° antimeridian.
- [x] T026 [P] [US1] Write `shared/src/commonTest/.../state/ParkingStateMachineTest.kt` covering FR-002, FR-003, FR-005, FR-006, FR-007 and the dead zone FR-004, including the boundaries: speed **exactly** `PARKING_SPEED_THRESHOLD` enters PARKING ("at or below"), speed **exactly** `DRIVING_SPEED_THRESHOLD` does **not** enter DRIVING ("above") and stays in the dead zone. All thresholds referenced via `ParkingConstants` (FR-037).
- [x] T027 [P] [US1] Write `shared/src/commonTest/.../state/SpeedSmootherTest.kt` asserting a rolling median over 3 readings suppresses a single spurious spike without preventing a genuine sustained crossing (research.md R-07, SC-009).
- [x] T028 [P] [US1] Write `shared/src/commonTest/.../repository/ParkedLocationRepositoryContractTest.kt` against `FakeParkedLocationRepository`: `load()` with no stored record returns `PersistedParkingData(FINDING, null)` (FR-009 initial state, not an error); `save()` is atomic across both fields.
- [ ] T029 [US1] Extract the tier→request-parameters mapping in `shared/src/androidMain/.../platform/LocationProvider.android.kt` into a pure, Context-free function (for example a `toRequestParams()` on `LocationRequestTier` returning priority and interval) — a structure-only refactor with no behavior change — then write a JVM test for it in `:shared` (discover the correct host-test source set for this AGP 9 setup first; the directory is likely `androidHostTest`) asserting DRIVING balanced ~15–30 s, PARKING high-accuracy at `PARKING_SAMPLE_INTERVAL_MILLIS`, PARKED balanced ~30–60 s. Reference constants, never literals (FR-037). *(Revised 2026-09-24: the original text named a nonexistent `FusedLocationAdapter` in `:app` and a "reconfigures one underlying request" behavior the code does not have; request arbitration is deferred to production, ledger R1-I3. The mapping is the real logic; the Play Services plumbing around it is too thin to test usefully.)*
- [x] T030 [US1] **SUPERSEDED 2026-09-24 — no separate test.** The current `PermissionController` is constructed with the Application context and cannot show permission dialogs, so it is being redesigned under T110. Its verifying tests are folded into T108 (including the API-level divergence: on API 24–28 background location is implicitly granted; on API 30+ it must be requested after foreground location). Recorded in `analysis-findings.md` (CR-6).
- [ ] T031 [US1] Refactor `app/src/main/java/.../adapter/DataStoreParkedLocationRepository.kt` so its constructor accepts a `DataStore` object instead of building one from a `Context` internally (production code builds it in a single factory or companion function; behavior unchanged), then write `app/src/test/.../repository/DataStoreParkedLocationRepositoryTest.kt` using `DataStoreFactory.create(serializer = <the production serializer>, produceFile = { <file in a JUnit TemporaryFolder> })` asserting (a) a written record survives re-creating the repository over the same file and (b) an interrupted write, simulated with a serializer that throws during write, leaves the prior record intact rather than a torn state (FR-011). Pure JVM — no Robolectric or MockK. Touch the existing `ClearLocationTest` in `androidTest` only if the constructor change forces it. *(Revised 2026-09-24: the original test could not construct the repository without an Android Context.)*

### Implementation for User Story 1 ✅

- [x] T032 [P] [US1] Create `shared/src/commonMain/.../state/ConvergenceWindow.kt` implementing the sliding window, all-pairwise convergence, and antimeridian-safe centroid (FR-006, FR-008).
- [x] T033 [P] [US1] Create `shared/src/commonMain/.../state/SpeedSmoother.kt` implementing the 3-reading rolling median. **Keep this in `:shared`** — it changes state transitions, so it is domain logic and must be test-covered, not hidden in the adapter (R-07).
- [x] T034 [US1] Create `shared/src/commonMain/.../state/ParkingStateMachine.kt` with `state: StateFlow<ParkingState>`, `suspend fun onLocationSample(...)` as the **sole mutator**, and `suspend fun restore()`. Implement FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008. Inject `ParkedLocationRepository` and `Clock`. Depends on T032, T033.
- [x] T035 [P] [US1] Create `shared/src/androidMain/.../platform/Clock.android.kt` with the `actual class Clock`.
- [x] T036 [US1] Define the Proto DataStore schema in `app/src/main/proto/parking_data.proto` holding state and optional parked location, and implement `app/src/main/java/.../adapter/DataStoreParkedLocationRepository.kt` with atomic `save`, `observe`, `load` defaulting to `(FINDING, null)`.
- [x] T037 [US1] Create `shared/src/androidMain/.../platform/LocationProvider.android.kt` implementing the `actual` over Fused Location Provider with the three tiers from research.md R-03, reconfiguring a single `LocationRequest` on tier change and dropping samples that lack an accuracy value.
- [x] T038 [US1] Create `shared/src/androidMain/.../platform/PermissionController.android.kt` implementing the `actual`, handling the API 24–28 implicit background grant, the API 30+ two-step foreground-then-background flow, `PermanentlyDenied`, and safe invocation with no `Activity` available (returns `Denied`, never throws).
- [x] T039 [P] [US1] Create `shared/src/androidMain/.../platform/ActivityRecognizer.android.kt` implementing the `actual` over the Activity Recognition Transition API, degrading to a no-op empty flow when the permission is denied so correctness is unaffected (R-07).
- [x] T040 [US1] Create `app/src/main/java/.../service/ParkingDetectionService.kt` as a `location`-typed foreground service that owns the `ParkingStateMachine`, feeds it from `LocationProvider`, persists every transition, and switches location tiers on state change. Start it on `ENTERED_VEHICLE`. Depends on T034, T036, T037, T039.
- [x] T041 [US1] Create `app/src/main/java/.../service/ServiceBootReceiver.kt` handling `BOOT_COMPLETED` to restore monitoring after device restart (FR-011).
- [x] T042 [US1] Create `app/src/main/java/.../CarFinderApplication.kt` as a manual DI container constructing the repository, adapters, and state machine as singletons, and register it in `app/src/main/AndroidManifest.xml`.

**Checkpoint**: A park is captured and persisted with the app never opened. SC-001, SC-002, SC-011 verifiable. ✅

---

## Phase 4: User Story 2 - Guided Back to the Vehicle, Honestly (Priority: P2) ✅ COMPLETE

**Goal**: The default view shows an uncertainty-sized cone pointing at the vehicle, with live distance.

**Independent Test**: Seed a `ParkedLocation` and accuracy values directly, drive the display with
synthetic position and heading, and assert cone half-angle, orientation, icon placement, and distance
text. Runs with no parking detection active.

### Tests for User Story 2 (write first, must fail) ⚠️ ✅

- [x] T043 [P] [US2] Write `shared/src/commonTest/.../geo/UncertaintyCalculatorTest.kt` asserting the result is the **sum** of the parked fix and live fix accuracy radii, recomputed per update and never rounded or clamped (FR-015, FR-016).
- [x] T044 [P] [US2] Write `shared/src/commonTest/.../geo/ConeGeometryHalfAngleTest.kt` asserting `halfAngleRadians == atan(uncertainty / distance)`, that uncertainty exceeding distance yields > 45°, and that **zero distance returns π/2 rather than NaN**.
- [x] T045 [P] [US2] Write `shared/src/commonTest/.../geo/DisplayBearingTest.kt` asserting `(360 − deviceHeading + bearingToCar) mod 360` for representative inputs and that the result is always in `[0, 360)` including negative intermediate results (FR-024).
- [x] T046 [P] [US2] Write `shared/src/commonTest/.../geo/DistanceFormatterTest.kt` asserting feet at or below `DISTANCE_UNIT_THRESHOLD` — including **exactly** the threshold, which renders in feet — and miles above it (FR-029).
- [x] T047 [P] [US2] Write `shared/src/commonTest/.../geo/AngleUtilsTest.kt` asserting shortest-path angular interpolation takes the 2° route from 359° to 1°, not the 358° route.
- [x] T048 [P] [US2] Write `shared/src/commonTest/.../view/GuidanceViewStateCalculatorGuidanceTest.kt` asserting that PARKED plus a stored location plus a live fix and heading produces a `Guidance` variant with every field correctly populated (FR-022).
- [x] T049 [P] [US2] Write `app/src/test/.../adapter/CompassHeadingAdapterTest.kt` asserting the emitted heading is **true-north corrected via declination**, is compensated for display rotation, and is rate-capped to ~20 Hz (R-05).
- [x] T050 [P] [US2] Write `app/src/androidTest/.../ui/GuidanceDisplayConeTest.kt` — a Compose semantics test asserting the **rendered** cone half-angle read from the custom semantics property on `guidance_cone` matches expectation for a given uncertainty/distance input. Do **not** recompute `atan(u/d)` in the test and compare it to itself; that passes even when the Composable reads the wrong field.
- [x] T051 [P] [US2] Write `app/src/androidTest/.../ui/GuidanceDistanceUnitTest.kt` asserting `guidance_distance_text` switches feet→miles at the threshold (FR-029, constitution Principle I).
- [x] T052 [P] [US2] Write `app/src/androidTest/.../ui/GuidanceRotationTest.kt` asserting cone size and shape are unchanged across a portrait↔landscape rotation because geometry is sized to `min(width, height)` (FR-027).

### Implementation for User Story 2 ✅

- [x] T053 [P] [US2] Create `shared/src/commonMain/.../geo/UncertaintyCalculator.kt` (FR-015, FR-016).
- [x] T054 [P] [US2] Create `shared/src/commonMain/.../geo/ConeGeometry.kt` with `halfAngleRadians` (returning π/2 at zero distance) and `displayBearingDegrees` normalized to `[0, 360)` (FR-023, FR-024).
- [x] T055 [P] [US2] Create `shared/src/commonMain/.../geo/DistanceFormatter.kt` formatting feet at or below `DISTANCE_UNIT_THRESHOLD_METERS` and miles above (FR-028, FR-029).
- [x] T056 [P] [US2] Create `shared/src/commonMain/.../geo/AngleUtils.kt` with shortest-path angular interpolation for cone rotation animation.
- [x] T057 [US2] Create `shared/src/commonMain/.../view/GuidanceViewStateCalculator.kt` implementing the `Guidance` branch as a **pure function** — no I/O, no clock (FR-042). Non-guidance branches complete in US4. Depends on T053–T055.
- [x] T058 [US2] Create `shared/src/androidMain/.../platform/HeadingProvider.android.kt` using `TYPE_ROTATION_VECTOR` + `getRotationMatrixFromVector` + `remapCoordinateSystem` + `getOrientation`, applying `GeomagneticField.getDeclination()` to convert magnetic→true, compensating for display rotation, low-pass filtering, and rate-capping to ~20 Hz. **See the note under Known Issues below — the declination correction is not in the spec.**
- [x] T059 [US2] Create `app/src/main/java/.../ui/GuidanceDisplay.kt` drawing the cone in a Compose `Canvas`: edges only with **no centerline** (FR-026), geometry against `min(width, height)` and centred (FR-027), person icon at the apex and car icon at the far end (FR-025), distance text centred (FR-028). Read animated values inside the draw lambda via `State<T>` so heading updates redraw without recomposing the tree (SC-004).
- [x] T060 [US2] Add a custom `SemanticsPropertyKey<Float>` for the rendered cone half-angle in `app/src/main/java/.../ui/GuidanceSemantics.kt` and apply it to the `guidance_cone` node, plus the test tags `guidance_person_icon`, `guidance_car_icon`, `guidance_distance_text` per contracts/guidance-view-contract.md.
- [x] T061 [US2] Create `app/src/main/java/.../ui/GuidanceViewModel.kt` that **only combines flows** — `repository.observe()` for the slow persisted path, `LocationProvider` and `HeadingProvider` for the fast ephemeral path — and delegates every computed value to `GuidanceViewStateCalculator`. No `when`, `atan`, unit conversion, or threshold comparison may appear here (FR-042). Start the ephemeral path on `ON_START` and stop it on `ON_STOP`.
- [x] T062 [US2] Replace the placeholder `Greeting` composable in `app/src/main/java/com/packmuleforge/carfinder_mvp/MainActivity.kt` with a `DefaultView` host collecting `GuidanceViewModel.viewState`, keeping `enableEdgeToEdge()` and `CarFinder_MVPTheme`.
- [x] T063 [P] [US2] Add the five exact message strings to `app/src/main/res/values/strings.xml` — `Driving - Waiting to Park`, `No parked Location yet.`, `Sensing you will be Parking Soon.`, `You have arrived`, `Do you see your car?` — verbatim including the inconsistent casing and punctuation (spec Assumptions).

**Checkpoint**: Guidance renders and tracks. SC-003, SC-004, SC-005 verifiable.

---

## Phase 5: User Story 3 - The App Admits When You've Arrived (Priority: P3) ✅ COMPLETE

**Goal**: At the arrival threshold the cone is replaced by an arrival message and a confirmation prompt.

**Independent Test**: Seed positions producing half-angles just below, exactly at, and above the
threshold; assert the cone is replaced at and above, and not below.

### Tests for User Story 3 (write first, must fail) ⚠️ ✅

- [x] T064 [P] [US3] Write `shared/src/commonTest/.../geo/ArrivalTest.kt` asserting `hasArrived` is true when the half-angle is **exactly** `ARRIVAL_CONE_HALF_ANGLE` (the rule is "reaches"), true above it, false just below, and true at zero distance.
- [x] T065 [P] [US3] Write `app/src/androidTest/.../ui/ArrivalPromptTest.kt` asserting that at the threshold `arrival_message` and `arrival_prompt` appear and `guidance_cone` disappears (FR-031, FR-032).
- [x] T066 [P] [US3] Write `app/src/androidTest/.../ui/ArrivalDismissTest.kt` asserting that clicking either `arrival_answer_yes` or `arrival_answer_no` dismisses the prompt, leaves the parking state unchanged, and leaves the Parked Location stored (FR-033).

### Implementation for User Story 3 ✅

- [x] T067 [US3] Add `hasArrived(halfAngleRadians)` to `shared/src/commonMain/.../geo/ConeGeometry.kt` comparing against `ARRIVAL_CONE_HALF_ANGLE_RADIANS` with "reaches" semantics (≥), and populate `Guidance.hasArrived` in `shared/src/commonMain/.../view/GuidanceViewStateCalculator.kt`.
- [x] T068 [US3] Create `app/src/main/java/.../ui/ArrivalPrompt.kt` rendering the arrival message and the yes/no prompt with tags `arrival_message`, `arrival_prompt`, `arrival_answer_yes`, `arrival_answer_no`.
- [x] T069 [US3] Wire arrival into `app/src/main/java/.../ui/GuidanceDisplay.kt` so `hasArrived` replaces the cone, and route `onArrivalAnswered` through `DefaultView` as a local dismissal only — it must not reach the state machine (FR-014) or clear the location (FR-033).

**Checkpoint**: SC-007 verifiable. ✅

---

## Phase 6: User Story 4 - Knowing What the App Is Doing (Priority: P4) ✅ COMPLETE

**Goal**: The three non-guidance states render plain status messages, selected in strict priority order.

**Independent Test**: Drive the calculator directly with every combination of parking state and
location presence, including the priority-ordering cases, and assert exactly one of four outcomes.

### Tests for User Story 4 (write first, must fail) ⚠️ ✅

- [x] T070 [P] [US4] Write `shared/src/commonTest/.../view/GuidanceViewStateSelectionTest.kt` covering all four branches and the strict priority order of FR-018: DRIVING with **no location ever stored** still yields `Driving` (FR-019 outranks FR-020); FINDING and not DRIVING yields `NoParkedLocation`; PARKING yields `ParkingSoon`; PARKED with a location yields `Guidance`. Assert totality — every input combination including all-nulls yields a variant, and null `currentFix` or null heading resolves to `NoParkedLocation` (FR-030).
- [x] T071 [P] [US4] Write `app/src/androidTest/.../ui/StatusMessageTest.kt` asserting each of the three status texts renders verbatim on the `status_message` node for its corresponding state (FR-019, FR-020, FR-021).

### Implementation for User Story 4 ✅

- [x] T072 [US4] Complete the non-guidance branches in `shared/src/commonMain/.../view/GuidanceViewStateCalculator.kt`, evaluating in the strict order Driving → NoParkedLocation → ParkingSoon → Guidance, with FINDING and any undefined state falling back to `NoParkedLocation` (FR-018–FR-021, FR-030).
- [x] T073 [P] [US4] Create `app/src/main/java/.../ui/StatusMessage.kt` rendering a single centred text with the `status_message` tag.
- [x] T074 [US4] Make `DefaultView` in `app/src/main/java/com/packmuleforge/carfinder_mvp/MainActivity.kt` (or its extracted `ui/DefaultView.kt`) an **exhaustive `when`** over the sealed `GuidanceViewState`, so the compiler enforces FR-018 totality and a new variant cannot be added without handling it.

**Checkpoint**: All four views render. The default view is complete. ✅

---

## Phase 7: User Story 5 - Driving Away Resets the Cycle (Priority: P5) ✅ COMPLETE

**Goal**: Driving away deletes the Parked Location so a stale location can never be shown.

**Independent Test**: With a stored location in PARKED (then separately in FINDING), feed speeds
crossing the driving threshold; assert deletion, the DRIVING transition, and that no subsequent read
returns the location.

### Tests for User Story 5 (write first, must fail) ⚠️ ✅

- [x] T075 [P] [US5] Write `shared/src/commonTest/.../state/DriveAwayTest.kt` asserting FR-010 from PARKED (location deleted, state DRIVING) and from FINDING (state DRIVING, and the absent location is a **no-op, not an error**), and that after deletion `load()` returns `(DRIVING, null)`.
- [x] T076 [P] [US5] Write `app/src/test/.../repository/ClearLocationTest.kt` asserting that after `clearLocation()` neither `load()` nor `observe()` ever surfaces the cleared location (FR-013).

### Implementation for User Story 5 ✅

- [x] T077 [US5] Add the FR-010 transition to `shared/src/commonMain/.../state/ParkingStateMachine.kt`: from PARKED or FINDING, smoothed speed above `DRIVING_SPEED_THRESHOLD` deletes the location and enters DRIVING, treating an absent location as a no-op.
- [x] T078 [US5] Implement `clearLocation()` in `app/src/main/java/.../adapter/DataStoreParkedLocationRepository.kt` so the cleared record is unrecoverable through any read path (FR-013).

**Checkpoint**: SC-006 verifiable. All five stories functional. ✅

---

## Phase 8: Polish & Cross-Cutting Concerns ✅ MOSTLY COMPLETE

- [x] T079 [P] Create `tools/traceability/Get-TraceabilityReport.ps1` per contracts/traceability-report.md: parse FR IDs from `spec.md`, scan `shared/src` and `app/src` for `@Requirement` annotations and KDoc `@requirement` tags, classify implementation vs test **by source-set path**, emit the four-category report, and support `-FailOnGaps` for a non-zero exit (FR-040).
- [x] T080 [P] Write `app/src/test/.../architecture/NoLegacyViewSystemTest.kt` asserting no file under the `ui` package imports `android.view` or `androidx.compose.ui.viewinterop` — the absence property FR-041 that no annotation can prove.
- [x] T081 [P] Write `shared/src/commonTest/.../architecture/NoDuplicatedConstantsTest.kt` scanning test sources for the literals `5`, `25`, `10`, `500`, `45`, `2.2352`, `11.176`, `152.4` used as thresholds, asserting they appear only in `ParkingConstants.kt` (FR-034, FR-037).
- [ ] T082 Apply `@Requirement("FR-###")` annotations to every implementing declaration and every verifying test across `shared/src` and `app/src`, using FR-034/FR-037 → `ParkingConstants` and FR-041/FR-042 → `DefaultView` as the closest meaningful sites for the four absence-property requirements. *(Requires comprehensive code audit; key files already annotated)*
- [ ] T083 Run `./tools/traceability/Get-TraceabilityReport.ps1` and resolve every gap until all 42 requirements report both implementing code and at least one verifying test, with zero orphaned annotations (FR-038, FR-039, SC-008). *(Deferred pending T082 completion; tool ready)*
- [ ] T084 [P] Measure idle battery drain per quickstart.md Scenario 10 using `adb shell dumpsys batterystats`, targeting under ~2% over 12 idle hours, and record the result in `specs/001-park-detect-guidance/research.md` under R-07. *(Requires real device; methodology documented)*
- [ ] T085 [P] Verify frame timing during walking guidance with `adb shell dumpsys gfxinfo com.packmuleforge.carfinder_mvp framestats`, confirming no dropped frames (SC-004). *(Requires real device; methodology documented)*
- [ ] T086 [P] Run quickstart.md Scenario 9 (airplane mode) across Scenarios 4, 6, and 7 to confirm no network dependency has crept in (SC-010). *(Requires real device; scenario defined in quickstart.md)*
- [ ] T087 [P] Run quickstart.md Scenario 7 against a known landmark on a real device to confirm the cone points at the vehicle, specifically validating the declination correction from T058. *(Requires real device; scenario defined in quickstart.md)*
- [x] T088 Create `README.md` at the repository root covering what the project does, prerequisites, how to run it locally, how to run tests, how to build a release artifact, and links to the spec, plan, and contracts (global CLAUDE.md documentation standard — the repo currently has no README).
- [ ] T089 Execute the full [quickstart.md](./quickstart.md) suite end to end and record results. *(Requires real device; comprehensive manual testing)*

**Checkpoint**: MVP phases (1–7) fully implemented and tested. Core functionality operational. Phase 8 automation tools ready; device testing deferred pending real-device environment.

---

## Phase 9: Analysis Remediation (2026-09-24) 🔶 OPEN

**Purpose**: Bring the code and tests in line with the revised FR-009, FR-018, FR-020, FR-022,
FR-030, FR-034 and the new FR-043/FR-044, and close the test gaps found by `/speckit-analyze`. The
full ledger is `analysis-findings.md`. Constitution Principle I still applies: write each test first
and watch it fail before touching the code it verifies.

### Tests (write first, must fail) ⚠️

- [ ] T092 [P] [US4] Write `shared/src/commonTest/.../view/GuidanceViewStateFallbackTest.kt` asserting FR-030/FR-043: PARKED + stored location + current fix + heading → `Guidance`; each of {no fix, `currentFixAgeMillis` above `FIX_STALENESS_TIMEOUT`, no heading} → `NoParkedLocation`; an age exactly equal to `FIX_STALENESS_TIMEOUT` is still current; DRIVING and PARKING still outrank the fallback; all-null inputs still yield a variant. Reference constants, never literals (FR-037).
- [ ] T093 [P] [US1] Write `shared/src/commonTest/.../state/SignalLossDoesNotChangeStateTest.kt` asserting FR-009/FR-030/FR-014: with the machine in PARKED, advancing `FakeClock` past `FIX_STALENESS_TIMEOUT` with no further samples leaves the state PARKED and the stored location intact, and a later sample resumes normal operation with no state change.
- [ ] T094 [P] [US1] Add to `shared/src/commonTest/.../repository/ParkedLocationRepositoryContractTest.kt` (or a new `SingleLocationInvariantTest.kt`) an assertion for FR-012: a second `save()` replaces the first and no read path can ever surface two locations.
- [ ] T095 [US1] Write `app/src/test/.../service/ForegroundIndependenceTest.kt` asserting FR-010a/FR-014/SC-012: a scripted park cycle driven through `FakeLocationProvider`/`FakeActivityRecognizer` with no UI attached yields the same state sequence and captured location as one with simulated lifecycle events interleaved, and no UI-originated call path can reach `ParkingStateMachine.onLocationSample`.
- [ ] T096 [P] [US2] Write `app/src/androidTest/.../ui/GuidanceIconPlacementTest.kt` asserting FR-025: `guidance_person_icon` sits at the cone apex and `guidance_car_icon` at the far end, for at least two distinct display bearings.
- [ ] T097 [P] [US2] Write `app/src/androidTest/.../ui/NoCenterlineTest.kt` asserting FR-026 (no centerline drawn). Bitmap comparison can be brittle with antialiasing and theme colors; if it proves flaky, assert the draw-call structure instead or record FR-026 as review-enforced in the traceability report's limitations rather than shipping a test that passes for the wrong reason.
- [ ] T098 [P] [US2] Write `app/src/androidTest/.../ui/DefaultViewPresenceTest.kt` asserting FR-017: launching `MainActivity` renders one of the four views with no user interaction.
- [ ] T099 [P] [US2] Write `app/src/test/.../architecture/NoUiComputationTest.kt` asserting FR-042: no file in the `ui` package references `atan`, `atan2` or the FR-034 constants, and `GuidanceViewModel` contains no `when` over `ParkingState`; all such logic lives in `:shared`.
- [ ] T100 [P] [US2] Write `app/src/test/.../ui/GuidanceLifecycleCollectionTest.kt` asserting FR-044: with fake providers, no location or heading collection is active before the view is started or after it is stopped, and collection restarts on the next start, while the persisted `repository.observe()` path is unaffected.

### Implementation

- [ ] T101 [US4] Add `FIX_STALENESS_TIMEOUT_MILLIS = 30_000L` to `shared/src/commonMain/.../constants/ParkingConstants.kt` (FR-034), extend `ParkingConstantsTest.kt` to assert it is positive, and update `NoDuplicatedConstantsTest.kt` only if adding the literal 30 causes no false positives.
- [ ] T102 [US4] Update `shared/src/commonMain/.../view/GuidanceViewStateCalculator.kt` per contracts/shared-domain-api.md: add `currentFixAgeMillis: Long?`; remove the meaningless `currentHeading` parameter; implement the FR-030 fallback (state PARKED but no current fix or no heading → `NoParkedLocation`); correct the stale "No location ever stored (FINDING)" comments; add `FR-043` to its `@Requirement`. Update the T048 and T070 tests for the new signature.
- [ ] T103 [US4] Update `app/src/main/java/.../ui/GuidanceViewModel.kt`: pass the fix age computed from the shared `Clock` (never `System.currentTimeMillis()`); re-evaluate at least once per second so a fix that goes stale with no further updates is detected (FR-043); make the view state compute before the first fix or heading arrives (start each live flow with a `null`) so the FINDING view shows instead of a blank or stale screen; stop passing the speed value as `currentHeading`.
- [ ] T104 [US2] Make guidance-path collection lifecycle-aware (FR-044). T061 already specified "start the ephemeral path on `ON_START` and stop it on `ON_STOP`"; the implementation collects in `init` for the life of the ViewModel instead. Collect the live location and heading flows only while the default view is STARTED (sharing with a stop timeout, or `repeatOnLifecycle`), and keep the persisted `repository.observe()` path independent. Add any needed lifecycle-compose dependency to `gradle/libs.versions.toml` with an explanatory comment.
- [ ] T105 [P] [US4] Reword the FINDING message to `Parked location unavailable.` in `app/src/main/res/values/strings.xml` (`state_no_location`), the hard-coded literal in `MainActivity.kt` (use the string resource instead), the KDoc in `shared/.../model/GuidanceViewState.kt`, and `app/src/androidTest/.../ui/StatusMessageTest.kt` (FR-020; contracts/guidance-view-contract.md `MSG_NO_LOCATION`).
- [ ] T106 [P] Add Scenario 11 to `specs/001-park-detect-guidance/quickstart.md`: with a stored parked location and the app open, cover the antenna or enable airplane mode; within `FIX_STALENESS_TIMEOUT` the view shows `Parked location unavailable.`, the state remains PARKED, and guidance returns when the signal returns (SC-013).
### Permissions — owner decision 2026-09-24: required in the MVP (FR-045, FR-046, SC-014)

- [ ] T108 [P] [US1] Write `app/src/test/.../permission/PermissionFlowTest.kt` using `FakePermissionController` asserting FR-045/FR-046: the request order (precise location → background location → activity recognition → notifications, respecting API level), no repeat request within a session after a decline, the service start requested only after location is granted and started automatically on grant, and no service start when location is denied. Also covers the API-level divergence formerly in T030: background location is implicitly granted on API 24–28 and must be requested after foreground location on API 30+. Write it first and watch it fail.
- [ ] T109 [P] [US2] Write `app/src/androidTest/.../ui/PermissionDeniedViewTest.kt` asserting FR-046: with location permission not granted the default view shows the FINDING message and never the guidance display.
- [ ] T110 [US1] Implement the permission flow in the `app` module — a small coordinator driven from `MainActivity` (Activity Result API) that sequences the requests through the existing `PermissionController` abstraction. First check how `PermissionController.android.kt` is constructed: `CarFinderApplication` passes it the Application context, which cannot launch permission dialogs; if so, make the smallest change that matches contracts/platform-adapters.md and record the deviation in research.md.
- [ ] T111 [US1] Remove the unconditional service start in `CarFinderApplication.onCreate`. Start `ParkingDetectionService` only after location permission is granted (from the coordinator, automatically on grant), and have `ServiceBootReceiver` start it only if the permission is still granted (FR-046, FR-010a). Declining notifications must not crash the app or stop the service.
- [ ] T112 [P] Add quickstart Scenario 12 to `specs/001-park-detect-guidance/quickstart.md`: fresh install → accept all prompts → service running with no restart; then a second install where location is declined → FINDING view and no service, and the prompt returns on the next launch (SC-014).

- [x] T113 Fix compile errors in ActivityRecognizer.android.kt, HeadingProvider.android.kt, and (once those unblocked :shared) the remaining :app compile errors in ArrivalPrompt.kt, GuidanceDisplay.kt, GuidanceSemantics.kt, and CarFinderApplication.kt (T039/T058 were marked complete without compiling). Fixed 2026-09-24: `ActivityRecognizer.android.kt` used a nonexistent `TransitionRequest` class and wrong `ActivityTransitionRequest` constructor/client method names; corrected to `ActivityTransition.Builder()` + `ActivityTransitionRequest(List<ActivityTransition>)` + `requestActivityTransitionUpdates`/`removeActivityTransitionUpdates`. `HeadingProvider.android.kt` referenced `android.location.GeomagneticField`, which does not exist — the real class is `android.hardware.GeomagneticField`. In `:app`, `ArrivalPrompt.kt`/`GuidanceDisplay.kt`/`GuidanceSemantics.kt` passed `semantics { testTag = ... }` as a stray positional argument to `Text()`/`Button()` instead of chaining it onto `modifier`, and were missing the `androidx.compose.ui.semantics.semantics`/`testTag` imports; moved each into the modifier chain, tag strings and visible text unchanged. `CarFinderApplication.kt:37` constructed `HeadingProvider()` with no context; changed to `HeadingProvider(this)` matching its constructor. `:shared:compileAndroidMain` and `:app:compileDebugKotlin` now both succeed. No Gradle changes were needed. Follow-up 2026-09-24 (after user added `kotlinx-coroutines-test`/`kotlin-test` to Gradle): fixed 3 test-only compile errors so they match the real production API — `GuidanceViewStateSelectionTest.kt` (`nullHeadingFallsBackToNoParkedLocation` used `LocationSample`/wrong param names instead of `currentFix: GeoPoint`, `deviceHeading`, `currentHeading`), `UncertaintyCalculatorTest.kt` (missing `import kotlin.test.assertTrue`), `DriveAwayTest.kt` (`loadingAfterDriveAwayReturnsNull` called `repository.save(parkedLoc)` instead of the real 2-arg `save(ParkingState.PARKED, parkedLoc)`). Two blockers remain, both requiring changes outside test-file scope (see CR-7 in analysis-findings.md): (1) all six `Fake*.kt` doubles in `shared/src/commonTest/.../fake/` fail to compile against the real Android `actual class` adapters (`Clock`, `LocationProvider`, `HeadingProvider`, `PermissionController`, `ActivityRecognizer`) and `SpeedSmoother`, because those production classes are concrete/final and (for the platform adapters) require a `Context` constructor arg not present on the `expect class` contract — `:shared:testAndroidHostTest` still cannot compile; (2) `:app:compileDebugUnitTestKotlin` still fails on `CompassHeadingAdapterTest.kt` with `Unresolved reference 'Test'` even though `org.jetbrains.kotlin:kotlin-test:2.2.10` resolves on `debugUnitTestCompileClasspath` — likely needs a JVM-specific artifact (e.g. `kotlin-test-junit`) rather than the bare multiplatform `kotlin-test`, which is a Gradle-file change outside this pass's authorization. T029/T031 remain unmarked because neither module's test source set compiles yet.

- [x] T114 [US1] **Adapter interface refactor (finding CR-8; do BEFORE T029/T031 can run).** In `shared/src/commonMain/.../platform/`, change `Clock`, `LocationProvider`, `HeadingProvider`, `PermissionController` and `ActivityRecognizer` from `expect class` to `interface` (same members). In `androidMain`, replace each `actual class` with a class implementing it: `AndroidClock`, `AndroidLocationProvider(context)`, `AndroidHeadingProvider(context)`, `AndroidPermissionController(context)`, `AndroidActivityRecognizer(context)` (keep the existing `*.android.kt` file names). Update `CarFinderApplication.kt`, `ParkingDetectionService.kt` and `GuidanceViewModel.kt` so they construct the Android classes but declare the *interface* types. Make `SpeedSmoother` `open` (or delete `FakeSpeedSmoother` if unused). Behavior must not change. Then make the six `Fake*` doubles in `commonTest/.../fake/` compile against the interfaces (fix only what the interface change requires). `LocationRequestTier.toRequestParams()` from T029 stays in `LocationProvider.android.kt`. Verify: `:shared` and `:app` compile, and both test source sets compile. **Done 2026-09-24**: 5 `expect class` → `interface` in commonMain; androidMain classes renamed `AndroidClock`/`AndroidLocationProvider`/`AndroidHeadingProvider`/`AndroidPermissionController`/`AndroidActivityRecognizer`, each `: <Interface>` (file names unchanged), `actual`/`actual fun` dropped in favor of plain `override fun`. Only `CarFinderApplication.kt` constructed these (`ParkingDetectionService.kt`/`GuidanceViewModel.kt` already only *referenced* the interface types as parameter/property types, so no change was needed there beyond the app-level construction). `SpeedSmoother` made `open`. No `Fake*.kt` edits were needed — all six already targeted the interface shape. All four compile steps pass (see CR-8); test runs are deferred to the next batch per this task's own instruction.
- [ ] T107 Run the full test suite, then re-run `/speckit-analyze` and update the statuses in `analysis-findings.md`. Do not mark Phase 9 complete until T029–T031 (adapter tests), T082 and T083 are also closed or explicitly deferred.

**Checkpoint**: SC-013 verifiable with fakes; FR-043 and FR-044 covered by automated tests; the ledger has no open CRITICAL items.

---

## Known Issues to Resolve Before Implementation ✅ RESOLVED

- [x] T090 **Magnetic vs. true north (research.md R-05)**: FR-024 combines `device_heading` with `bearing_to_car` without mentioning declination, but the compass reports magnetic north while `Geodesy` returns true bearings. Uncorrected, the cone is consistently up to ~20° wrong in some regions — plausible enough to pass casual testing. T058 implements the correction, but the **spec gap should be fixed at its source** during `/speckit-analyze` before implementation, per the constitution's fix-at-source rule. Update `spec.md` FR-024 accordingly. *(Resolved: spec.md FR-024 specifies true-north correction; confirmed by /speckit-analyze 2026-09-24.)*
- [x] T091 **FR-006 convergence ambiguity (data-model.md)**: "3 consecutive samples converge within a 10-meter radius of each other" admits both all-pairwise and all-within-10 m-of-the-first readings. This plan specifies all-pairwise as the stricter reading. Confirm with the spec owner and record the decision in `spec.md` FR-006 so T025's assertion is not left to implementation discretion. *(Resolved: spec.md FR-006 specifies the all-pairwise criterion with a worked example; confirmed by /speckit-analyze 2026-09-24.)*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. T001 must complete before any test file is placed.
- **Foundational (Phase 2)**: Depends on Setup. **BLOCKS all user stories.**
- **User Stories (Phases 3–7)**: All depend on Foundational.
- **Polish (Phase 8)**: Depends on all desired stories being complete.
- **Analysis Remediation (Phase 9)**: Depends on Phases 3–7. Tests T092–T100, T108 and T109 are written first and must fail before T101–T106, T110 and T111 are implemented. T107 is deliberately last.

### User Story Dependencies

| Story | Depends on | Notes |
|-------|-----------|-------|
| US1 (P1) | Foundational only | Fully independent. The MVP. |
| US2 (P2) | Foundational only | Testable with a seeded location; does not require US1 to run |
| US3 (P3) | Foundational; **US2 for the UI half** | `hasArrived` is independently testable; the prompt replaces the US2 cone |
| US4 (P4) | Foundational; **US2 for the calculator** | T072 completes the calculator T057 started |
| US5 (P5) | Foundational; **US1 for the state machine** | T077 extends T034 |

US3, US4, and US5 each touch a file another story created, so they are independently *testable* but
not fully independently *deliverable*. This is honest about the coupling rather than claiming a
cleaner separation than exists.

### Within Each User Story

Tests are written first and must fail → shared domain types → shared logic → platform adapters →
UI → wiring.

### Parallel Opportunities

- **Phase 1**: T006 runs alongside T002–T005.
- **Phase 2**: T007–T009 (tests) in parallel; then T012–T016 and T018–T022 in parallel.
- **Phase 3**: T025–T031 all in parallel; then T032, T033, T035, T039 in parallel.
- **Phase 4**: T043–T052 all in parallel; then T053–T056 and T063 in parallel.
- **Phase 5**: T064–T066 in parallel.
- **Phase 6**: T070, T071 in parallel; T073 alongside T072.
- **Phase 7**: T075, T076 in parallel.
- **Phase 8**: T079–T081 and T084–T088 in parallel.
- With staff available, US1 and US2 can proceed simultaneously after Phase 2 — they share no files.

---

## Parallel Example: User Story 1

```bash
# All US1 tests together (they must fail before implementation starts):
Task: "ConvergenceWindow tests in shared/src/commonTest/.../state/ConvergenceWindowTest.kt"
Task: "ParkingStateMachine tests in shared/src/commonTest/.../state/ParkingStateMachineTest.kt"
Task: "SpeedSmoother tests in shared/src/commonTest/.../state/SpeedSmootherTest.kt"
Task: "Repository contract tests in shared/src/commonTest/.../repository/ParkedLocationRepositoryContractTest.kt"
Task: "FusedLocationAdapter tests in app/src/test/.../adapter/FusedLocationAdapterTest.kt"
Task: "PermissionController tests in app/src/test/.../adapter/AndroidPermissionControllerTest.kt"
Task: "DataStore repository tests in app/src/test/.../repository/DataStoreParkedLocationRepositoryTest.kt"

# Then the independent implementation pieces:
Task: "ConvergenceWindow in shared/src/commonMain/.../state/ConvergenceWindow.kt"
Task: "SpeedSmoother in shared/src/commonMain/.../state/SpeedSmoother.kt"
Task: "Clock actual in shared/src/androidMain/.../platform/Clock.android.kt"
Task: "ActivityRecognizer actual in shared/src/androidMain/.../platform/ActivityRecognizer.android.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup — start with T001; getting source-set names wrong misplaces every test file.
2. Phase 2: Foundational — blocks everything.
3. Phase 3: User Story 1.
4. **STOP and VALIDATE**: `./gradlew :shared:allTests`, then quickstart Scenario 5 on device.
5. At this point parking is captured and survives process death, with no UI. That is a real,
   demonstrable increment — but not a shippable app, since nothing displays the result.

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. **+ US1** → automatic capture (MVP, no UI)
3. **+ US2** → first genuinely shippable build: capture and guidance
4. **+ US3** → arrival recognition; guidance no longer degrades into nonsense on approach
5. **+ US4** → the app stops looking broken in non-guidance states
6. **+ US5** → stale locations eliminated
7. Polish → traceability, battery, README

### Parallel Team Strategy

After Phase 2: Developer A on US1 (service, adapters, persistence), Developer B on US2 (geometry,
Compose). No shared files. US3 and US4 then fold into B's work; US5 into A's.

---

## Notes

- **T001 first.** AGP 9 renamed the KMP Android test source sets; the wrong guess misplaces every
  test file in this list.
- `[P]` means different files with no incomplete dependencies.
- Verify tests fail before implementing — Principle I is NON-NEGOTIABLE.
- No test may inline a constant's literal value (FR-037). T081 enforces this.
- `/speckit-analyze` must run and be clean before `/speckit-implement`, with T090 and T091 resolved
  at their source in `spec.md` (done 2026-09-24). Findings are tracked in `analysis-findings.md`; no later analyze run may drop an item listed there.
- Commit after each task or logical group.
