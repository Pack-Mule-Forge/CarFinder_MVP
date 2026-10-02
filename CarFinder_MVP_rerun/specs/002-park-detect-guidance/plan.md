# Implementation Plan: Automatic Park Detection & Guidance Back to the Vehicle

**Branch**: `main` (no feature branch; spec directory `specs/002-park-detect-guidance`) | **Date**: 2026-09-30 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-park-detect-guidance/spec.md`, planned against the architecture
brief in `Claude Prompts/prompt-plan.md`.

## Summary

Car Finder detects parking automatically from filtered location speed, confirms the stop with a pairwise
convergence test on 5-second location samples, persists the centroid and its accuracy as the single Parked
Location, and guides the user back with an uncertainty cone that rotates with the compass heading and collapses
into an arrival prompt when the uncertainty reaches the remaining distance. For a bounded window after PARKED
is declared, a new convergence silently corrects the location, which recovers from a brief stop being taken for
parking (FR-035, [research.md §R13](research.md#r13-bounded-re-convergence-recovery-while-parked)).

The technical approach is a **Kotlin Multiplatform shared core with native adapters at the edges**, Android
first:

- **`:shared` `commonMain`** holds all domain logic as pure Kotlin: the FINDING/DRIVING/PARKING/PARKED state
  machine (a pure reducer), the rolling-median speed filter, the convergence window, uncertainty, distance,
  bearing, cone and display-bearing math, default-view selection, the arrival-prompt tracker, the persistence
  *model* (record type + store interface), and the platform-adapter *interfaces*. It has no dependency on
  Android or iOS APIs.
- **`:shared` `androidMain`** holds the `actual` adapters: Fused Location Provider, the rotation-vector compass,
  Activity Recognition transitions (a sampling-rate hint only), DataStore persistence, and the
  `PermissionController`.
- **`:app`** holds the Android shell: an always-on `location`-type foreground service that hosts the engine,
  a boot receiver, the notification, and the Jetpack Compose home screen. The home screen is a pure function of a
  `StateFlow<HomeScreenState>` produced by the shared `HomeScreenPresenter`.
- **`tools/traceability/`** holds a development-time PowerShell script that scans KDoc `@requirement` tags in
  production and test sources and writes the forward-traceability report that Constitution II requires.

## Technical Context

**Language/Version**: Kotlin 2.2.x (current catalog: 2.2.10). Kotlin Multiplatform for `:shared`, with the Kotlin
Compose compiler plugin for `:app`. Gradle 9.5 wrapper, AGP 9.3.x, JDK 17+ toolchain. PowerShell 5.1+ for the
traceability tool.

**Primary Dependencies**:
- `kotlinx-coroutines-core` (StateFlow and the engine actor). The catalog pin of 1.7.3 predates Kotlin 2.2, so
  bump it to current stable (≥ 1.10).
- `kotlinx-serialization-json` for the persisted record schema, which is defined in `commonMain`.
- `androidx.datastore:datastore` for the Android storage mechanism. Bump it from 1.0.0 to the current stable
  1.1+ line.
- `com.google.android.gms:play-services-location` (Fused Location Provider and the Activity Recognition
  Transition API).
- Jetpack Compose via the BOM (ui, foundation Canvas, material3), plus `activity-compose` and
  `lifecycle-runtime-compose`.
- The `com.google.protobuf` plugin and `protobuf-kotlin` in the current catalog become unused (see
  [research.md §R4](research.md#r4-persistence-mechanism)) and should be removed from the catalog.

**Storage**: A single typed DataStore file (`parking_state.json`) on Android. It holds one
`PersistedParkingRecord` containing the lifecycle state and an optional Parked Location, and every transition
writes it atomically with `updateData`. Parking history is out of scope; when it arrives it goes in a separate
table-backed store behind its own interface.

**Testing**:
- `:shared` `commonTest`: `kotlin-test` and `kotlinx-coroutines-test`, run as Android host tests. These cover
  domain logic, the engine, and the presenter.
- `:shared` `androidHostTest`: Robolectric plus fakes. These cover the Android adapters (location mapping,
  heading math, DataStore on a temp file including a corrupted file, and permission sequencing through a test
  `ActivityResultRegistry`).
- `:app` `test`: Robolectric plus `compose-ui-test-junit4`. These are the semantics-based Compose UI tests.
  They run on the JVM and do not need an emulator.
- `tools/traceability`: Pester tests against fixture source trees.
- `:shared-testing`: a test-only KMP module that holds the fakes, used by `:shared` `commonTest` and `:app`
  tests. It exists because test source sets are not visible across modules.
- `:benchmark`: a Macrobenchmark module with a frame-timing test for FR-027. It runs on a connected reference
  device and is automated, but outside the fast test loop and outside CI at MVP.
- Every test carries `@requirement <ID>` in KDoc (see [contracts/traceability.md](contracts/traceability.md)).

**Target Platform**: Android phones. minSdk 26, and targetSdk/compileSdk set to the latest stable API level that
AGP 9.3 supports. Devices need GPS and a magnetometer. iOS is a planned future target: the shared core is written
so it compiles for iOS unchanged, but no iOS target or UI is built in this version.

**Project Type**: Mobile app (KMP shared library plus Android application).

**Performance Goals**:
- The guidance display recomposes at 60 fps without jank on every location or heading update (FR-027).
- Direction and distance reflect movement within 1 s (SC-008).
- Guidance is visible within 2 s of opening the app while parked (SC-006).
- The "unavailable" fallback appears within 1 s of the fix-staleness timeout or of heading loss (SC-010).

**Constraints**:
- Fully offline (Constitution, "On-device first").
- Always-on background detection with a persistent notification that restarts after reboot (FR-033).
- Proportionate sensor duty cycle (see [research.md §R2](research.md#r2-location-sampling-strategy-and-reuse-of-one-always-on-location-stream)).
- No Android types in `commonMain`.
- The Composable never computes geometry.
- All spec constants (FR-030) are defined once and referenced by name.

**Scale/Scope**: A single user and a single vehicle, with one current Parked Location. There are 35 FRs and
10 QRs, one screen with 4 view variants, and about 6 platform adapters.

No `NEEDS CLARIFICATION` items remain. The spec's 2026-09-30 clarification session resolved the behavioral
ambiguities. The technical unknowns (sampling rates, storage mechanism, heading source, background-start
permissions, and centroid accuracy) are resolved in [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | How this plan satisfies it | Pre-research | Post-design |
|---|---|---|---|
| **I. Test-first coverage of shared domain logic** | Every domain unit (`ParkingStateMachine`, `SpeedMedianFilter`, `ConvergenceWindow`, `Uncertainty`, `GeoMath`, `GuidanceCalculator`, `ConeGeometry`, `DefaultViewSelector`, `ArrivalPromptTracker`, `FixCurrency`) has a `commonTest` suite. Tests reference `CarFinderConstants.*` and build fixtures from them, for example a speed of `DRIVING_SPEED_THRESHOLD_MPH + 1.0`. There are Compose semantics tests for cone geometry, feet↔miles, and the arrival prompt. | PASS | PASS ([contracts/guidance-ui.md](contracts/guidance-ui.md) §Test hooks) |
| **II. Forward requirement traceability** | KDoc `@requirement FR-xxx` / `QR-xxx` on implementing declarations and on every test. `Get-TraceabilityReport.ps1` is extended to parse FR+QR IDs, classify tags as implementation or test by path, and flag untraced and orphaned tags. It runs manually, with no CI at MVP. | PASS | PASS ([contracts/traceability.md](contracts/traceability.md)) |
| **III. Platform adapters tested via fakes** | Each Android adapter wraps its framework client behind a thin seam (`FusedClientPort`, `SensorPort`, a DataStore file, `ActivityResultRegistry`), and host tests drive it with test doubles and Robolectric. Real-device runs appear in quickstart as supplementary integration checks only. | PASS | PASS ([contracts/platform-adapters.md](contracts/platform-adapters.md) §Fakes) |
| **IV. Compose-only UI driven by hoisted state** | `HomeScreen(state, onArrivalAnswered)` and its children are stateless Composables. No Composable collects flows or runs side effects. `MainActivity` copies presenter state into an Activity-held Compose `State` via `repeatOnLifecycle`, and `GuidanceSessionObserver` toggles guidance visibility and the heading sensor (research R10). All geometry arrives as normalized `ConeGeometry` from shared, and the Canvas applies only the scale-to-min-dimension and translate-to-center transform. There are no `AndroidView` or XML layouts. Rotation needs no save/restore because state lives in the engine and presenter. | PASS | PASS |
| **V. Shared core, adapted at the edges** | `commonMain` imports only Kotlin stdlib, coroutines, and serialization. Platform services are reached through `expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters`, with `actual typealias PlatformContext = android.content.Context`. `PermissionController` is one suspend `request()` plus observable status, with a per-capability `RequestMode` so iOS implicit-on-first-use motion is not forced into an explicit request. | PASS | PASS |
| Constraint: on-device first | No network calls anywhere. Fused Location works offline via GNSS. | PASS | PASS |
| Constraint: Android first, iOS not precluded | There are no iOS targets now. Nothing in `commonMain` is JVM-only: the plan avoids `String.format` and `java.*` there (see research §R9). | PASS | PASS |
| Constraint: proportionate sensor use | Location rate is state-dependent: 20 s idle-watch, 5 s DRIVING/PARKING, and 1 s only while guidance is on screen. The 5 s rate is also held for the 120 s parked-recovery window after each PARKED declaration (FR-035). Heading runs only while guidance is visible. Activity Recognition only raises the rate and never gates sampling. | PASS | PASS |
| Constraint: do not foreclose future directions | Map: the domain exposes lat/lon and accuracy. History: the store interface is single-record and a history store is additive. Phone Finder: the location and heading adapters and `GeoMath` are target-agnostic. Telemetry: the engine emits transition events on a `SharedFlow` that a future sink can collect. | PASS | PASS |
| Workflow: clarify before plan | Spec §Clarifications, session 2026-09-30, with 6 answers encoded. | PASS | n/a |
| Workflow: analyze before implement | The first `/speckit.analyze` pass (2026-09-30) found two CRITICAL issues (D1 and D2) and six HIGH/MEDIUM ones. They were fixed at the source: in spec.md, in this plan, in the contracts, and in tasks.md. Analyze must be re-run until it is clean. | pending (downstream) | pending (downstream) |

**Result**: No violations, and Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-park-detect-guidance/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R12
├── data-model.md        # Phase 1: entities, persisted schema, state transitions
├── quickstart.md        # Phase 1: build/test/validate guide
├── contracts/
│   ├── shared-domain-api.md   # Public Kotlin API of :shared commonMain
│   ├── platform-adapters.md   # Adapter interfaces, expect/actual, PermissionController
│   ├── guidance-ui.md         # HomeScreenState → Compose rendering + semantics test hooks
│   └── traceability.md        # @requirement tag grammar + report format + CLI
├── checklists/requirements.md # (from /speckit.specify)
└── tasks.md             # Phase 2 (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
settings.gradle.kts            # includes :app, :shared (already present); add :shared-testing, :benchmark
build.gradle.kts               # plugin aliases; drop protobuf, add kotlin.serialization
gradle/libs.versions.toml      # version bumps per Technical Context

shared/                        # KMP library (com.android.kotlin.multiplatform.library)
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/packmuleforge/carfindermvp/shared/
    │   ├── domain/
    │   │   ├── CarFinderConstants.kt     # FR-030 constants (only place literals appear)
    │   │   ├── TuningConstants.kt        # plan-level knobs (sampling profiles, cone length, recheck tick)
    │   │   ├── LifecycleState.kt
    │   │   ├── LocationReading.kt
    │   │   ├── ParkedLocation.kt
    │   │   ├── SpeedMedianFilter.kt
    │   │   ├── ConvergenceWindow.kt
    │   │   ├── ParkingStateMachine.kt    # pure reducer: (Snapshot, Event) -> Transition
    │   │   └── SamplingProfile.kt
    │   ├── guidance/
    │   │   ├── GeoMath.kt                # haversine distance, initial bearing, centroid
    │   │   ├── Uncertainty.kt
    │   │   ├── GuidanceCalculator.kt     # half-angle, display bearing, arrival, distance display
    │   │   ├── ConeGeometry.kt           # normalized geometry for rendering
    │   │   ├── FixCurrency.kt
    │   │   ├── ArrivalPromptTracker.kt
    │   │   └── DefaultViewSelector.kt
    │   ├── engine/
    │   │   ├── ParkingEngine.kt          # actor: adapters + reducer + store + profile control
    │   │   └── HomeScreenPresenter.kt    # StateFlow<HomeScreenState>, staleness ticker
    │   ├── persistence/
    │   │   ├── PersistedParkingRecord.kt # @Serializable schema v1
    │   │   └── ParkingStore.kt           # interface
    │   └── platform/
    │       ├── PlatformAdapters.kt       # expect fun createPlatformAdapters(...)
    │       ├── LocationSource.kt  HeadingSource.kt  ActivitySignalSource.kt
    │       ├── Clocks.kt  PermissionController.kt
    │   (engine/ also holds HomeScreenState.kt)
    ├── commonTest/kotlin/...             # unit tests for everything above (fakes come from :shared-testing)
    ├── androidMain/kotlin/com/packmuleforge/carfindermvp/shared/platform/android/
    │   ├── PlatformAdapters.android.kt   # actual factory, actual typealias PlatformContext
    │   ├── FusedLocationSource.kt        # + FusedClientPort seam
    │   ├── RotationVectorHeadingSource.kt# + HeadingMath (pure), SensorPort seam
    │   ├── ActivityTransitionSource.kt
    │   ├── DataStoreParkingStore.kt      # + JSON serializer, corruption handler
    │   └── AndroidPermissionController.kt
    └── androidHostTest/kotlin/...        # Robolectric + fakes adapter tests

shared-testing/                # KMP library: fakes shared by :shared commonTest and :app tests
└── src/commonMain/kotlin/com/packmuleforge/carfindermvp/shared/testing/

app/                           # Android application
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml           # permissions, FGS type=location, boot receiver
    │   └── kotlin/com/packmuleforge/carfindermvp/
    │       ├── CarFinderApplication.kt   # app-scoped graph; open createAdapters() for test fakes
    │       ├── MainActivity.kt           # permissions, repeatOnLifecycle → uiState, setContent { HomeScreen(uiState) }
    │       ├── GuidanceSessionObserver.kt# lifecycle: guidance visibility + heading start/stop
    │       ├── service/ParkingDetectionService.kt  # foreground service hosting the engine
    │       ├── service/BootReceiver.kt
    │       ├── service/DetectionNotification.kt
    │       └── ui/
    │           ├── HomeScreen.kt         # pure: when(state) → status or guidance
    │           ├── GuidanceDisplay.kt    # Canvas cone, icons, distance text, arrival
    │           ├── GuidanceSemantics.kt  # custom SemanticsPropertyKeys + test tags
    │           └── theme/
    ├── benchmark/                        # benchmark build type only: BenchmarkCarFinderApplication + manifest override
    └── test/kotlin/...                   # Robolectric Compose semantics tests, service tests, TestCarFinderApplication

benchmark/                     # Macrobenchmark (com.android.test): GuidanceJankBenchmark, FR-027 frame timing

tools/traceability/
├── Get-TraceabilityReport.ps1  # extend: FR+QR, KDoc tag, impl/test by path, spec 002 default
└── tests/Get-TraceabilityReport.Tests.ps1 + fixtures/
```

**Structure Decision**: The build uses two Gradle modules that already exist in `settings.gradle.kts`, `:shared`
(KMP) and `:app` (Android), plus a standalone tool directory. Two test-only modules are added: `:shared-testing`,
which holds the fakes because test source sets cannot be shared across modules, and `:benchmark`, which holds the
FR-027 frame-timing Macrobenchmark. The engine and presenter live in `:shared`
`commonMain` so that iOS can reuse the orchestration as well as the math. `:app` contains only
Android-lifecycle glue and the Compose UI. The traceability tool has no build-time or runtime link to either
module.

## Complexity Tracking

No constitution violations to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
