# Implementation Plan: Automatic Park Detection & Guidance Back to the Vehicle (Run 2)

**Branch**: `main` (no feature branch; spec directory `specs/003-park-detect-guidance`) | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-park-detect-guidance/spec.md`

## Summary

Car Finder infers parking from the phone's smoothed speed and a pairwise convergence of location
readings, stores the centroid with a conservative accuracy radius, corrects a premature declaration
inside a fixed recovery window, and guides the user back with an uncertainty cone that follows a
true-north heading and gives way to an arrival prompt.

The approach is a Kotlin Multiplatform shared core with native adapters at the edges, Android first:

- **`:shared` `commonMain`**: all domain logic as pure Kotlin. A reducer for the lifecycle, the speed
  filter, the convergence window, the recovery rule, the sampling policy, guidance math, cone
  geometry, view selection, the engine and the presenter, plus the adapter interfaces.
- **`:shared` `androidMain`**: adapter implementations for fused location, the rotation-vector
  heading, activity transitions, DataStore persistence and permissions.
- **`:shared-testing`**: fakes for every adapter and the scripted-replay harness.
- **`:app`**: a location-type foreground service that owns detection, a boot receiver, the
  notification, and a Compose home screen that is a pure function of the presenter's state.
- **`tools/traceability/`**: a PowerShell script that builds the requirement traceability report.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (Multiplatform for `:shared` and `:shared-testing`, Compose
compiler plugin for `:app`). Gradle 9.5 wrapper, AGP 9.3.3. PowerShell 5.1+ for tooling.

**Primary Dependencies**: `kotlinx-coroutines` 1.10.2, `kotlinx-serialization-json` 1.9.0,
`androidx.datastore` 1.2.1, `play-services-location` 21.1.0, Jetpack Compose (BOM 2026.02.01),
`activity-compose`, `lifecycle-runtime-compose`, `lifecycle-service`. All are already in
`gradle/libs.versions.toml`.

**Storage**: One typed DataStore file holding a single JSON record: lifecycle state and an optional
Parked Location with its declaration time ([research R7](research.md#r7-persistence)).

**Testing**: `kotlin-test` and `kotlinx-coroutines-test` for shared logic, run as Android host
tests. Robolectric 4.17 for adapters, service and receiver. Compose semantics tests on the JVM.
Pester for the traceability tool. Emulator or device checks for operating-system boundaries
([quickstart §5](quickstart.md)).

**Target Platform**: Android, `minSdk` 26 (QR-015), `compileSdk`/`targetSdk` at the latest level AGP
9.3 supports. The shared core must compile for iOS later; no iOS target is built.

**Project Type**: Mobile app (KMP shared library, test-support library, Android application).

**Performance Goals**: Guidance visible within 2 s (FR-044). Guidance updates within 1 s at the
guidance interval (SC-008). "Location unavailable" within 1 s of staleness (SC-010). No visible
stutter (FR-046); the spec gives no frame-time number, so none is asserted.

**Constraints**: Fully offline. One location stream (FR-029). No sensing without permission
(FR-052). No platform types in `commonMain` (QR-014). No geometry in Composables (QR-013). No
coordinates in logs.

**Scale/Scope**: One user, one vehicle, one Parked Location. 56 FRs, 16 QRs, 13 SCs. One screen with
six view states. Eight adapter interfaces.

**Starting point**: the repository has the Gradle wrapper, root build files and version catalog, and
no module source. `settings.gradle.kts` includes four modules that do not exist; this plan creates
three and removes `:benchmark`.

No `NEEDS CLARIFICATION` items remain. The five values this plan first introduced were moved into
spec.md on 2026-10-02 ([research.md](research.md#values-this-plan-introduces)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | How this plan meets it | Pre-research | Post-design |
|---|---|---|---|
| **I. Test-first coverage of shared domain logic** | Every shared unit has a `commonTest` suite written before its implementation. Tests use `CarFinderConstants` names and derive fixtures from them. Compose semantics tests cover cone geometry, the feet-to-miles switch and the arrival prompt. | PASS | PASS ([contracts/guidance-ui.md](contracts/guidance-ui.md)) |
| **II. Forward requirement traceability** | KDoc `@requirement` tags on implementing declarations and on tests; a script produces the report and flags untraced requirements and orphaned tags. | PASS | PASS ([contracts/traceability.md](contracts/traceability.md)) |
| **III. Platform adapters tested via fakes** | Every adapter is an interface with a fake in `:shared-testing`; each Android implementation is host-tested through an internal seam. Device checks are additional, not a substitute. | PASS | PASS ([contracts/platform-adapters.md](contracts/platform-adapters.md)) |
| **IV. Compose-only UI driven by hoisted state** | Stateless Composables fed by `StateFlow<HomeScreenState>`; geometry arrives normalized from shared code; a source scan forbids View-system imports. | PASS | PASS |
| **V. Shared core, adapted at the edges** | `commonMain` depends only on the Kotlin standard library, coroutines and serialization. Platform services are reached through `expect fun createPlatformAdapters`. `PermissionController` has exactly one suspend function, `request(capability)`, plus observable status; the launch order and the FR-056 confirm-or-ask-again loop are a separate shared function. | PASS, with the note below | PASS, with the note below |
| Constraint: on-device first | No network use. | PASS | PASS |
| Constraint: Android first, iOS not precluded | Nothing JVM-only in `commonMain`. | PASS | PASS |
| Constraint: proportionate sensor use | Sampling interval follows FR-027; heading runs only while guidance is visible. | PASS | PASS |
| Constraint: do not foreclose future directions | Coordinates and accuracy are plain domain values (map); the store is single-record behind an interface (history); adapters are target-agnostic (Phone Finder); the engine exposes a transition stream (telemetry). | PASS | PASS |
| Workflow: clarify before plan | Spec Clarifications, session 2026-10-02, five answers. | PASS | n/a |
| Workflow: analyze before implement | `/speckit-analyze` must run clean after `/speckit-tasks`. Findings go to `analysis-findings.md`. | pending | pending |

**Note on Principle V.** The constitution says platform services "MUST sit behind an
`expect`/`actual` boundary as native adapters". The spec (QR-003) says an adapter a test must replace
is an interface and never an `expect class`. This plan satisfies both by making the adapters
interfaces and putting the `expect`/`actual` boundary at one factory function
([research R2](research.md#r2-adapter-boundary)). This is a reading of Principle V, not a waiver. If
the owner reads the principle as requiring `expect class` adapters, the constitution needs an
amendment before `/speckit-tasks`, because that reading cannot coexist with Principle III.

**Result**: No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-park-detect-guidance/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── analysis-findings.md        # append-only findings ledger (QR-011)
├── contracts/
│   ├── shared-domain-api.md
│   ├── platform-adapters.md
│   ├── guidance-ui.md
│   └── traceability.md
├── checklists/requirements.md
└── tasks.md                    # /speckit-tasks output, not created here
```

### Source Code (repository root)

```text
shared/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/packmuleforge/carfindermvp/shared/
    │   ├── domain/        # CarFinderConstants, LifecycleState, LocationReading,
    │   │                  # SpeedFilter, ConvergenceWindow, ParkedLocation, ParkingStateMachine,
    │   │                  # SamplingPolicy
    │   ├── guidance/      # GeoMath, GuidanceCalculator, ConeGeometry, DefaultViewSelector,
    │   │                  # ArrivalPrompt, HeadingReading
    │   ├── persistence/   # PersistedParkingRecord, ParkingStore
    │   ├── platform/      # adapter interfaces, PlatformAdapters, PermissionSequence,
    │   │                  # expect createPlatformAdapters
    │   └── engine/        # ParkingEngine, HomeScreenPresenter, HomeScreenState
    ├── commonTest/kotlin/…/shared/            # unit, engine, presenter and replay tests
    ├── androidMain/kotlin/…/shared/platform/android/
    │                      # FusedLocationSource, RotationVectorHeadingSource, HeadingMath,
    │                      # ActivityTransitionSource, DataStoreParkingStore,
    │                      # AndroidPermissionController, clocks, log, actual factory
    └── androidHostTest/kotlin/…/shared/platform/android/   # adapter tests, source scans

shared-testing/
└── src/commonMain/kotlin/…/shared/testing/    # fakes, FakePlatform, Readings, ReplayScript, ReplayRunner

app/
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/packmuleforge/carfindermvp/
    │   │   ├── CarFinderApplication.kt        # builds adapters, engine, presenter; overridable for tests
    │   │   ├── MainActivity.kt
    │   │   ├── service/                       # ParkingDetectionService, BootReceiver, DetectionNotification
    │   │   └── ui/                            # HomeScreen, GuidanceDisplay, StatusMessage, ArrivalPrompt, semantics
    │   └── res/values/strings.xml
    └── test/kotlin/com/packmuleforge/carfindermvp/   # Compose, service, receiver, Activity tests

tools/traceability/
├── Get-TraceabilityReport.ps1
├── no-code-requirements.psd1
└── tests/                                     # Pester tests and fixtures

settings.gradle.kts                            # include :app, :shared, :shared-testing
gradle/libs.versions.toml                      # existing; remove benchmark and uiautomator entries
```

**Structure Decision**: Mobile app with a KMP shared core. Three Gradle modules and one tools
directory ([research R1](research.md#r1-modules)). The root Gradle files exist; every module
directory is created by this feature.

## Notes for `/speckit-tasks`

- Tests come before the code they cover, and must fail first (Principle I).
- A task is closed only on the actual output of a build or test command run for it.
- Tasks that touch permissions, the foreground service, boot, process death or stored-file
  corruption are not closed until the matching check in [quickstart §5](quickstart.md) has been run
  on an emulator or device.
- Before each batch, confirm that the files its tasks name exist or are created by an earlier task.

## Complexity Tracking

No constitution violations to justify.
