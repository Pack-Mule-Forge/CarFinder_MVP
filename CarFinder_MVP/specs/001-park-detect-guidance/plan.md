# Implementation Plan: Automatic Park Detection and Return Guidance

**Branch**: *(none — no `before_specify`/`before_plan` branch hook configured; working directly against the spec directory)* | **Date**: 2026-09-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-park-detect-guidance/spec.md`

## Summary

Car Finder detects parking without user action and guides the user back to the vehicle with an
uncertainty-honest directional cone. The implementation adds a Kotlin Multiplatform `:shared` module
holding all domain logic — the four-state parking machine, convergence and uncertainty calculation,
guidance geometry, and the persistence *interface* — with zero platform dependency, and confines all
sensing, storage, permissions, and background execution to Android adapters behind an
`expect`/`actual` boundary.

Two decisions dominate the design, both driven by FR-014 (background-only state machine):

1. The state machine runs inside a **location-typed foreground service** that owns the sensing
   pipeline and persists every state change. The UI is a read-only observer that cannot perturb it.
2. Because the UI must render at 60 fps from live position and heading while the service runs on its
   own cadence, there are **two distinct data paths**: a slow, persisted path (parking state + parked
   location, written by the service, read by the UI) and a fast, ephemeral path (current fix +
   compass heading, read by the UI only while it is visible). Both feed a pure shared-module
   calculator that produces the view state, satisfying FR-042.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (existing), targeting JVM 11 bytecode (existing
`compileOptions`). Kotlin Multiplatform plugin to be added at the same Kotlin version.

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01, existing), AGP 9.3.3 (existing),
kotlinx-coroutines (new, for `StateFlow` in shared), Google Play Services Location / Fused Location
Provider (new), AndroidX DataStore Proto (new), AndroidX Lifecycle (existing). No networking
dependency of any kind — see SC-010.

**Storage**: Proto DataStore, single record, Android-side only behind a shared `ParkedLocationRepository`
interface. Rationale and the Room/SQLDelight migration path are in [research.md](./research.md) (R-04).

**Testing**: `kotlin.test` for shared common unit tests; JUnit4 for Android-side adapter tests against
fakes; Compose `createComposeRule` with semantics-based assertions for UI tests. A custom
`@Requirement("FR-###")` annotation carries requirement IDs (FR-039).

**Target Platform**: Android, minSdk 24 / targetSdk 37 / compileSdk 37 (existing). iOS is not built
in this feature but the module boundary is structured so adding it is additive — see R-02.

**Project Type**: Mobile application (Android), multi-module: `:app` (Android UI + adapters) and
`:shared` (KMP domain core).

**Performance Goals**: Guidance display holds 60 fps with no dropped frames during continuous
position and heading updates (SC-004, FR-027). Heading updates arrive at sensor cadence (~50 Hz raw)
and must be throttled before reaching recomposition.

**Constraints**: Fully offline-capable (SC-010) — no core capability may require network. Background
sensing must stay within a defensible battery budget: low-power monitoring while DRIVING/PARKED, the
5-second elevated cadence only during PARKING (FR-005). Parked location and state must survive
process death (FR-011).

**Scale/Scope**: One user, one device, one current parked location (FR-012). No history, no accounts,
no server. Roughly: 1 foreground service, ~8 shared domain types, 4 platform adapters, 1 screen with
4 view states, 1 development-time traceability script.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| # | Principle | Gate | Pre-Phase-0 | Post-Phase-1 |
|---|-----------|------|-------------|--------------|
| I | Test-first coverage of shared domain logic | State machine, convergence, uncertainty, and bearing/distance/cone math all live in `:shared` with `commonTest` unit tests; tests reference `ParkingConstants` members, never literals; Compose UI tests cover cone geometry, the feet/miles switch, and the arrival prompt | PASS | PASS |
| II | Forward requirement traceability | `@Requirement("FR-###")` annotation on implementing code and verifying tests; `Get-TraceabilityReport.ps1` emits the per-requirement report and flags untraced requirements and orphaned annotations; no commit-diff/backward mechanism introduced | PASS | PASS |
| III | Platform adapters tested via fakes | Every adapter is defined as an interface in `:shared` with a `Fake*` implementation in test source; no test requires a real sensor, real GPS, or a real device fix | PASS | PASS |
| IV | Compose-only UI driven by hoisted state | All four views are Composables; zero View-system classes; `GuidanceViewState` is computed by `:shared` and hoisted into the Composables, which are pure functions of it | PASS | PASS |
| V | Shared core, adapted at the edges | `commonMain` cannot reference `android.*` — compiler-enforced by the source-set classpath; `PermissionController` normalizes the permission flow without forcing iOS-shaped symmetry | PASS | PASS |

**Technology & platform constraints**

| Constraint | Status |
|-----------|--------|
| On-device first, no network dependency | PASS — Fused Location Provider resolves fixes without network; no other dependency needs it |
| Android first, iOS not precluded | PASS — see R-02; adding an iOS target is additive, requiring `iosMain` actuals and no `commonMain` change |
| Proportionate sensor use | PASS with a tracked tension — see Complexity Tracking |
| Future directions not foreclosed | PASS — `ParkedLocationRepository` isolates storage so history is a repository change (R-04); guidance geometry is pure and reusable by a future Phone Finder |

**Workflow gates**

| Gate | Status |
|------|--------|
| `/speckit-clarify` before `/speckit-plan` | SATISFIED — three clarifications resolved 2026-09-18; checklist 16/16 |
| `/speckit-analyze` before `/speckit-implement` | PENDING — required after `/speckit-tasks` |

## Project Structure

### Documentation (this feature)

```text
specs/001-park-detect-guidance/
├── plan.md                             # This file
├── research.md                         # Phase 0 output
├── data-model.md                       # Phase 1 output
├── quickstart.md                       # Phase 1 output
├── contracts/                          # Phase 1 output
│   ├── shared-domain-api.md
│   ├── platform-adapters.md
│   ├── guidance-view-contract.md
│   └── traceability-report.md
├── checklists/
│   └── requirements.md
└── tasks.md                            # Phase 2 — NOT created by /speckit-plan
```

### Source Code (repository root)

```text
settings.gradle.kts                     # MODIFIED: include(":shared")
gradle/libs.versions.toml               # MODIFIED: KMP plugin, coroutines, play-services-location, datastore

shared/                                 # NEW — Kotlin Multiplatform domain core
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/packmuleforge/carfinder/shared/
    │   ├── annotation/Requirement.kt              # FR-038, FR-039
    │   ├── constants/ParkingConstants.kt          # FR-034 — single source of all eight
    │   ├── model/
    │   │   ├── ParkingState.kt                    # FR-001, FR-009
    │   │   ├── ParkedLocation.kt                  # FR-007, FR-012
    │   │   ├── LocationSample.kt
    │   │   ├── GeoPoint.kt
    │   │   └── GuidanceViewState.kt               # FR-018..FR-022, FR-030
    │   ├── state/
    │   │   ├── ParkingStateMachine.kt             # FR-001..FR-010
    │   │   └── ConvergenceWindow.kt               # FR-006, FR-008
    │   ├── geo/
    │   │   ├── Geodesy.kt                         # distance + bearing (reused prior impl)
    │   │   ├── UncertaintyCalculator.kt           # FR-015, FR-016
    │   │   ├── ConeGeometry.kt                    # FR-023, FR-024, FR-031
    │   │   └── DistanceFormatter.kt               # FR-028, FR-029
    │   ├── view/GuidanceViewStateCalculator.kt    # FR-018..FR-022, FR-030, FR-042
    │   ├── repository/ParkedLocationRepository.kt # interface only — FR-011, FR-013
    │   └── platform/
    │       ├── LocationProvider.kt                # interface (CR-8)
    │       ├── HeadingProvider.kt                 # interface (CR-8)
    │       ├── PermissionController.kt            # interface — Principle III/V (CR-8)
    │       └── PlatformCapabilities.kt            # (not built; see ledger R1-G5)
    ├── commonTest/kotlin/…                        # FR-035, FR-037 + Fake adapters (FR-III)
    └── androidMain/kotlin/com/packmuleforge/carfinder/shared/platform/
        └── *.android.kt                           # Android* implementation classes of the interfaces

app/                                    # EXISTING — Android UI + adapters
└── src/
    ├── main/
    │   ├── AndroidManifest.xml                    # MODIFIED: permissions + FGS declaration
    │   └── java/com/packmuleforge/carfinder_mvp/
    │       ├── MainActivity.kt                    # REPLACED: hosts DefaultView
    │       ├── CarFinderApplication.kt            # NEW: manual DI container
    │       ├── service/
    │       │   ├── ParkingDetectionService.kt     # FR-014 — owns the state machine
    │       │   └── ServiceBootReceiver.kt         # FR-011 — restart after device reboot
    │       ├── adapter/
    │       │   ├── FusedLocationAdapter.kt        # R-03
    │       │   ├── CompassHeadingAdapter.kt       # R-05
    │       │   ├── AndroidPermissionController.kt # R-06
    │       │   └── DataStoreParkedLocationRepository.kt  # R-04
    │       └── ui/
    │           ├── DefaultView.kt                 # FR-017, FR-018
    │           ├── GuidanceDisplay.kt             # FR-023..FR-029
    │           ├── ArrivalPrompt.kt               # FR-031..FR-033
    │           ├── StatusMessage.kt               # FR-019..FR-021
    │           ├── GuidanceViewModel.kt           # wiring only — no calculation (FR-042)
    │           └── theme/                         # EXISTING
    ├── test/                                      # adapter tests against fakes (FR-III)
    └── androidTest/                               # Compose semantics UI tests (FR-036)

tools/traceability/
└── Get-TraceabilityReport.ps1                     # FR-040 — development-time only
```

**Structure Decision**: Two Gradle modules. `:shared` is a Kotlin Multiplatform library configured
with **only an Android target at MVP** (R-02) — `commonMain` purity is still compiler-enforced because
Android APIs exist only on the `androidMain` classpath, so Principle V holds without the cost of
writing iOS actuals now. `:app` keeps the existing `com.packmuleforge.carfinder_mvp` namespace and
gains the service, adapters, and UI. `tools/traceability/` sits outside both modules so the
traceability tooling is never compiled into the shipped APK, as the source prompt requires.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Continuous background location monitoring, in tension with the constitution's "sensor use limited to what each capability actually needs, with reasonable battery consumption" | FR-014 makes the state machine background-only and SC-011 requires capture when the app is never opened. A park cannot be detected if nothing is watching when it happens. | Foreground-only detection (spec Q2 option B) was explicitly rejected by the user. It would require the user to open the app while parking, which defeats the automatic-capture premise in SC-001. **Mitigation**: three-tier cadence — low-power/activity-gated while DRIVING and PARKED, the 5 s cadence (FR-005) only during PARKING, which is a short window. Budget and measurement method in R-07. |
| A foreground service with an ongoing notification | Android will not permit sustained background location access any other way at targetSdk 37. | `WorkManager` (15-minute floor — far too coarse for a 5 s convergence window) and passive location listening (no guaranteed cadence) both fail FR-005. |
| Two separate data paths (persisted slow path, ephemeral fast path) rather than one | FR-011 requires state to survive process death; SC-004 requires 60 fps guidance. Routing 60 fps heading through DataStore would be both slow and destructive to flash. | A single persisted path cannot meet the frame budget; a single ephemeral path cannot survive process death. |
| `ACCESS_BACKGROUND_LOCATION` | Required by FR-014 on API 29+. | None — it is the only mechanism. **Risk**: Play Store requires a written justification and review for this permission. Flagged in R-08; it is a release-process risk, not a technical one. |
