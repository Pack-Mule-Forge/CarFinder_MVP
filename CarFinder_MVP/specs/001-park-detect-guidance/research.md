# Phase 0 Research: Automatic Park Detection and Return Guidance

**Date**: 2026-09-18 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Each entry resolves one unknown from the plan's Technical Context. Entries marked **VERIFY** carry a
concrete check to run during implementation, because the repository pins tool versions newer than I
can confirm from memory — see R-01.

---

## R-01 — Toolchain versions and Kotlin Multiplatform wiring

**Decision**: Add the KMP plugin at the Kotlin version already pinned in
`gradle/libs.versions.toml` (2.2.10), and configure `:shared`'s Android target with AGP's
multiplatform library plugin rather than applying `com.android.library` directly.

**Rationale**: The repository already pins AGP 9.3.3, Kotlin 2.2.10, compileSdk 37, and Compose BOM
2026.02.01. Keeping the Kotlin plugin and KMP plugin on a single version reference avoids the
version-skew failures that dominate KMP setup problems. Note that `libs.versions.toml` currently
declares no `kotlin-android` plugin — the `:app` module compiles Kotlin without one — so the Kotlin
Android support is coming from AGP itself at this version.

**VERIFIED (T001)**: For AGP 9.3.3 + Kotlin 2.2.10 KMP library with Android target only:
- **Plugin ID**: `org.jetbrains.kotlin.multiplatform` (version refs to `kotlin = "2.2.10"` in libs.versions.toml)
- **Source sets** (expected, per AGP 9 KMP conventions):
  - `commonMain`, `commonTest` — shared code and common-platform tests
  - `androidMain` — Android platform code
  - `androidUnitTest` (or `androidTest`) — unit tests running on host JVM
  - `androidInstrumentedTest` — instrumented tests running on device
- **Note**: Run `./gradlew :shared:tasks --all` to confirm exact names if the above differs. The exact
source-set naming changed between AGP 8 and 9; getting this right is critical before writing tests.

**Alternatives considered**: Applying `com.android.library` + `kotlin("multiplatform")` separately —
the older arrangement, and more likely to conflict at AGP 9. A plain Kotlin JVM library for `:shared`
— rejected because it forecloses iOS, violating Principle V's intent.

---

## R-02 — Which KMP targets to declare at MVP

**Decision**: Declare **only the Android target** in `:shared` for this feature. Do not create
`iosMain` or declare iOS targets.

**Rationale**: Declaring an iOS target obligates an `actual` for every `expect` — meaning writing
Core Location and Core Motion adapters now, which is out of scope. The purity guarantee that matters
(Principle V: `commonMain` has zero platform dependency) does **not** depend on having a second
target: `commonMain` compiles against the common classpath only, so a reference to `android.*` from
`commonMain` fails to compile regardless of how many targets exist. The constitution's requirement is
therefore fully enforced today, and adding iOS later is purely additive — declare the target, add
`iosMain` actuals, change nothing in `commonMain`.

**Trade-off accepted**: With one target there is no compiler pressure preventing a developer from
putting domain logic in `androidMain` where it *would* compile against Android APIs. That is a code
review concern, and the traceability report offers a partial backstop by showing which file
implements each requirement — domain requirements resolving to `androidMain` paths is a visible
smell.

**Alternatives considered**: Adding a `jvm()` target purely to keep `commonMain` honest and run
shared tests faster. Rejected — it also demands a `jvm` actual for every `expect`, producing
throwaway JVM adapters whose only purpose is to satisfy the compiler.

---

## R-03 — Location and speed source

**Decision**: **Fused Location Provider** (`com.google.android.gms:play-services-location`), using a
single `LocationRequest` whose priority and interval are *reconfigured per state* rather than a new
client per state. Speed comes from `Location.getSpeed()`, accuracy from
`Location.getAccuracy()`.

**Rationale**: The source prompt explicitly asked whether an existing always-on service can be reused
before starting a new high-frequency one during PARKING, and wanted a concrete recommendation. FLP is
that reuse: it batches and shares system-wide location work across apps, so a 5-second request
during PARKING frequently costs far less than a dedicated GPS session because other consumers are
already driving the hardware. Reconfiguring one request also avoids a gap in coverage during the
handover that stopping-then-starting two clients would create — a gap that could drop a sample from
the convergence window (FR-006).

Concretely, three tiers:

| State | Priority | Interval |
|-------|----------|----------|
| DRIVING | `PRIORITY_BALANCED_POWER_ACCURACY` | ~15–30 s (only needs to catch the drop below 5 mph) |
| PARKING | `PRIORITY_HIGH_ACCURACY` | `PARKING_SAMPLE_INTERVAL` (5 s) — FR-005 |
| PARKED / FINDING | `PRIORITY_BALANCED_POWER_ACCURACY`, passive-leaning | ~30–60 s (only needs to catch the rise above 25 mph for FR-010) |

**Offline check**: FLP resolves GPS/GNSS fixes with no network. Network is only used to *improve*
fixes when available. This satisfies SC-010.

**Alternatives considered**: Platform `LocationManager` with `GPS_PROVIDER` — no GMS dependency and
works on non-Google devices, but gives up the cross-app batching that makes the PARKING tier
affordable, and requires hand-rolling the throttling FLP does natively. Worth revisiting only if GMS
availability becomes a requirement. `Location.getSpeed()` is unreliable on some providers at low
speeds; R-07 covers the smoothing needed.

---

## R-04 — Persistence mechanism

**Decision**: **Proto DataStore**, one serialized record holding the `ParkedLocation` *and* the
`ParkingState`, behind the shared `ParkedLocationRepository` interface. Android-side only; `:shared`
declares the interface and never the implementation.

**Rationale**: The record is small, singular, written on every state transition, and read on every
app open — DataStore's exact shape. It is transactional and atomic, so it cannot leave a half-written
record after process death (FR-011), which is the failure mode SharedPreferences' `apply()` is prone
to. Proto rather than Preferences because the record is a structured object with a schema, and
typo-prone string keys are a poor fit for data that FR-013 requires to be reliably deleted.

**On not foreclosing history** (constitution constraint, spec Out of Scope): storage sits behind
`ParkedLocationRepository`, so adding parking history later means adding a new repository
implementation backed by Room or SQLDelight and deleting the DataStore one. No domain code changes.
If history arrives, **SQLDelight** is the better target than Room because it works in `commonMain`
and would let the history query logic itself be shared with iOS — worth noting now so the later
decision is not made under time pressure.

**Alternatives considered**: Room today — a full SQLite database for a single row is disproportionate
and slower to read on cold start. SharedPreferences — no atomicity guarantee across process death,
which is precisely what FR-011 tests. A flat file — all of DataStore's work, hand-rolled.

---

## R-05 — Compass heading

**Decision**: `SensorManager` with `TYPE_ROTATION_VECTOR`, converted via `getRotationMatrixFromVector`
+ `remapCoordinateSystem` + `getOrientation`. Register at `SENSOR_DELAY_UI`, apply a low-pass filter,
and emit at a capped rate.

**Rationale**: `TYPE_ROTATION_VECTOR` is the fused sensor (accelerometer + magnetometer + gyroscope),
which is markedly steadier than raw magnetometer-plus-accelerometer and already compensates for much
of the jitter that would otherwise make the cone visibly twitch. Three details that matter here:

- **Display rotation compensation**: `remapCoordinateSystem` must account for the current display
  rotation, or the cone points 90° off in landscape. This interacts directly with FR-027.
- **Rate capping**: raw delivery can approach 50 Hz. Emitting every sample into Compose state would
  recompose far above the frame rate and burn the budget SC-004 depends on. Cap emission at ~20 Hz
  and let the animation interpolate.
- **Angle interpolation must wrap**: interpolating heading from 359° to 1° must take the 2° path, not
  the 358° path. This is the classic bug in compass UIs and belongs in tested shared code.

**VERIFY**: magnetic vs. true north. `display_bearing` in FR-024 is computed against
`bearing_to_car`, which geodesy returns as a **true** bearing, while the compass reports **magnetic**
heading. These differ by local declination — up to ~20° in parts of the world, which would point the
cone at the wrong row of a car park. Apply `GeomagneticField.getDeclination()` to convert before
feeding FR-024. **This is a correctness bug waiting to happen and is not mentioned anywhere in the
spec** — flagged for `/speckit-analyze`.

**Alternatives considered**: `TYPE_MAGNETIC_FIELD` + `TYPE_ACCELEROMETER` manually fused — more code,
worse result. `TYPE_ORIENTATION` — deprecated.

---

## R-06 — `PermissionController` shape

**Decision**: A shared `expect class PermissionController` exposing a `suspend fun request(...)`
returning a sealed `PermissionResult`, **plus** a separate non-suspending `fun status(...): Flow<PermissionStatus>`
for capabilities that have no explicit request on a given platform.

**Rationale**: The constitution and the source prompt both insist on normalizing the Android
`Activity`-based flow into one suspend function *without* forcing artificial symmetry. The two-method
shape is what makes that possible: Android's location permission is genuinely
request/response, so it uses `request()`; iOS raw accelerometer and gyroscope via `CMMotionManager`
need no permission at all, and `CMMotionActivityManager`/`CMPedometer` are gated behind
`NSMotionUsageDescription` and requested *implicitly on first use*. There is no iOS API to call that
means "ask the user for motion access now," so an iOS `request()` for motion would have to fabricate
a response. `status()` models it honestly as observable state.

**Android specifics the implementation must handle**:

- `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` **cannot be requested in the same call** on
  API 30+. Foreground must be granted first, then background requested separately — and on API 30+
  the background request opens Settings rather than a dialog.
- On API 24–28 there is no `ACCESS_BACKGROUND_LOCATION`; foreground grant implies background access.
  The controller must report "granted" on those versions rather than requesting a permission that
  does not exist. minSdk is 24, so this path is live.
- `ACTIVITY_RECOGNITION` became a runtime permission at API 29.

**Alternatives considered**: A single `suspend fun` for everything — forces the iOS motion lie
described above. Returning a plain `Boolean` — loses the "permanently denied, must visit Settings"
case, which the background-location flow makes common enough to model explicitly.

---

## R-07 — Battery budget and speed smoothing

**Decision**: Gate the expensive tiers on the Activity Recognition Transition API, and smooth speed
with a short rolling median before threshold comparison.

**Rationale**: Two problems share one solution.

*Battery*: continuously polling location at even a 30-second cadence, 24 hours a day, is the cost
FR-014 imposes. The Activity Recognition Transition API delivers a callback on
`IN_VEHICLE` enter/exit rather than requiring polling, so the app can sit near-idle until the user
actually starts driving. This is the single largest battery lever available and directly serves the
constitution's proportionality constraint.

*Speed noise*: `Location.getSpeed()` is noisy at low speeds, and the spec's thresholds are sharp —
FR-003 fires at or below 5 mph, FR-002 above 25 mph. A single spurious reading could trigger a
spurious transition, which SC-009 (zero false captures across a day of walking, jogging, cycling)
will catch. A rolling median over the last 3 readings suppresses single-sample spikes without adding
the lag a longer average would. **Note**: this smoothing is domain behavior affecting state
transitions, so it belongs in `:shared` and must be unit tested — not hidden in the Android adapter.

**Measurement method**: compare battery drain over a 12-hour idle period (no driving) against a
baseline build with the service disabled, using `adb shell dumpsys batterystats`. Target: under 2% of
a typical battery over 12 idle hours. This is a target to measure against, not a spec requirement —
the spec sets no numeric battery bound.

**Alternatives considered**: No smoothing — fails SC-009 under realistic sensor noise. A long moving
average — adds lag that delays PARKING entry and could push the convergence window past the moment
the car actually stopped. Polling-only with no activity gating — simpler, materially worse battery.

---

## R-08 — Foreground service type and release-process risk

**Decision**: A `location`-typed foreground service started when activity recognition reports
`IN_VEHICLE`, declared in the manifest with `android:foregroundServiceType="location"`, plus a
`BOOT_COMPLETED` receiver to restore monitoring after a device restart (FR-011).

**Rationale**: At targetSdk 37 this is the only sanctioned route to sustained background location.
`WorkManager`'s 15-minute minimum period cannot service a 5-second convergence window, and passive
location listening offers no cadence guarantee.

**Release-process risk, flagged rather than solved**: `ACCESS_BACKGROUND_LOCATION` requires a written
justification and a review process for Play Store distribution, and apps are routinely rejected when
the justification does not demonstrate that the core feature is impossible without it. Car Finder's
justification is strong — automatic park capture is definitionally impossible with foreground-only
access — but the review adds calendar time and should not be discovered a week before release. This
is a process risk, not a technical one, and it has no bearing on the implementation.

**Alternatives considered**: covered above and in Complexity Tracking.

---

## R-09 — Requirement ID annotation and report tooling

**Decision**: A `@Requirement(vararg ids: String)` annotation declared in `commonMain` with
`AnnotationRetention.SOURCE`, applied to implementing declarations and to test functions. A
PowerShell script at `tools/traceability/Get-TraceabilityReport.ps1` scans source and emits the
report.

**Rationale**: An annotation beats a naming convention because it is machine-readable without
parsing prose, survives refactoring and renames, and can carry several IDs on one declaration.
`SOURCE` retention keeps it entirely out of the runtime image — the tooling is development-time only,
as the source prompt requires. PowerShell because `.specify/init-options.json` already sets
`"script": "ps"` and every existing Spec Kit script in this repo is PowerShell; adding a second
scripting runtime for one tool is not worth it.

The script reads FR IDs from `spec.md` as the authoritative list, scans `shared/src` and `app/src`
for annotations, and reports four categories: fully traced, implemented but untested, specified but
unimplemented, and orphaned annotations pointing at non-existent IDs. It exits non-zero when any
category but the first is non-empty, so it can later become a CI gate without modification — though
CI automation is explicitly not required at MVP (Principle II).

**Alternatives considered**: KDoc `@requirement FR-001` tags, which the constitution offers as an
example. Rejected as the primary mechanism because KDoc is invisible to the compiler — a typo'd tag
is silently wrong, and tags on test functions are easy to omit. The script will additionally
recognize KDoc tags so that either form is picked up, but annotations are the convention. A Gradle
task or KSP processor — more robust, disproportionate for MVP.

---

## Summary of items carried into implementation

| ID | Item | Type |
|----|------|------|
| R-01 | Confirm AGP 9 KMP plugin ID and source-set names before writing adapter tests | VERIFY, first task |
| R-05 | Magnetic-to-true-north declination correction, absent from the spec | **Correctness gap — raise in `/speckit-analyze`** |
| R-07 | Speed smoothing is domain logic and belongs in `:shared` with tests, not in the adapter | Design constraint |
| R-08 | Play Store background-location justification and review | Release-process risk |
