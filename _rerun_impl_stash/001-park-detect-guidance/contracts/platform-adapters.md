# Contract: Platform Adapters (interface in `commonMain`, implementation per platform)

**Module**: `:shared` | **Principle**: V (shared core, adapted at the edges), III (tested via fakes)

Every platform capability the domain needs is declared here as an **`interface`** in `commonMain`, with an
Android implementation class in `androidMain` (`AndroidLocationProvider`, `AndroidHeadingProvider`,
`AndroidPermissionController`, `AndroidActivityRecognizer`, `AndroidClock`), each constructed with an Android
`Context`. Adding iOS later means adding `iosMain` implementations and changing nothing in `commonMain` (R-02).

> **Amended 2026-09-24 (finding CR-8).** These were originally `expect class` declarations. A final
> `expect class` cannot be subclassed, so no `Fake*` could implement it, which violated Principle III
> (adapters tested via fakes). Interfaces satisfy Principle III and still keep `commonMain` free of
> `android.*` (Principle V). Use `expect`/`actual` only for small stateless platform functions that never
> need a fake. The constitution's "`expect`/`actual` boundary" wording is read as "a boundary between
> `commonMain` and platform code"; an interface is such a boundary.

**Testing contract (Principle III)**: every adapter below has a `Fake*` counterpart in shared test
source. No test in this feature may require a real sensor, a real GPS fix, or a real device
permission dialog. Real-device testing is valuable for integration validation but never substitutes
for these.

---

## `LocationProvider`

```kotlin
interface LocationProvider {
    fun samples(request: LocationRequestTier): Flow<LocationSample>
}

enum class LocationRequestTier { DRIVING, PARKING, PARKED }
```

**Contract**:

- The tier maps to the cadence/priority table in [research.md](../research.md) R-03. `PARKING` must
  deliver at `PARKING_SAMPLE_INTERVAL_MILLIS` (FR-005).
- Switching tiers **reconfigures one underlying request**; it must not stop-then-start, which would
  risk dropping a sample from the convergence window.
- Emits `speedMetersPerSecond` and `accuracyRadiusMeters` on every sample — both are required, and a
  sample missing accuracy cannot feed FR-015 and must be dropped rather than defaulted.
- Never requires network (SC-010).

**Fake**: `FakeLocationProvider` accepts a scripted list of samples and emits on demand, letting
tests drive an entire drive-park-return cycle deterministically.

---

## `HeadingProvider`

```kotlin
interface HeadingProvider {
    /** True-north-corrected heading in degrees, 0…360. */
    fun headingDegrees(): Flow<Double>
}
```

**Contract**:

- Emits **true** north, not magnetic. The Android implementation applies
  `GeomagneticField.getDeclination()` before emitting (R-05). This is load-bearing: `Geodesy`
  returns true bearings, and mixing frames points the cone up to ~20° off.
- Compensates for display rotation so FR-027 holds in landscape.
- Emission is rate-capped (~20 Hz) so recomposition stays inside the SC-004 frame budget.
- Active only while the UI is visible — it is the fast ephemeral path and must never feed the state
  machine (FR-014).

**Fake**: `FakeHeadingProvider` emits a caller-controlled sequence, including the 359°→1° wrap case.

---

## `PermissionController` *(Principle V, R-06)*

```kotlin
enum class Capability { FOREGROUND_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, MOTION }

sealed interface PermissionResult {
    data object Granted : PermissionResult
    data object Denied : PermissionResult
    data object PermanentlyDenied : PermissionResult   // must be sent to Settings
}

enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_APPLICABLE }

interface PermissionController {
    /** One suspend function per platform, as the constitution requires. */
    suspend fun request(capability: Capability): PermissionResult

    /** For capabilities with no explicit request on this platform. */
    fun status(capability: Capability): Flow<PermissionStatus>
}
```

**Why two methods rather than one** — this is the "don't force artificial symmetry" clause of
Principle V made concrete. Android location is genuinely request/response and uses `request()`. On
iOS, raw accelerometer and gyroscope via `CMMotionManager` need no permission at all, and
`CMMotionActivityManager`/`CMPedometer` are gated behind `NSMotionUsageDescription` and requested
*implicitly on first use* — there is no API meaning "ask now". An iOS `request(MOTION)` would have to
invent a response. `status()` reports it honestly, returning `NOT_APPLICABLE` where a capability is
ungated.

**Android implementation contract**:

- `FOREGROUND_LOCATION` and `BACKGROUND_LOCATION` **cannot be requested in one call** on API 30+.
  Foreground must be granted first; the background request then opens Settings rather than a dialog.
  `request(BACKGROUND_LOCATION)` while foreground is ungranted returns `Denied` without prompting.
- On API 24–28 `ACCESS_BACKGROUND_LOCATION` does not exist; foreground grant implies it.
  `request(BACKGROUND_LOCATION)` returns `Granted` there. **minSdk is 24, so this path is live and
  must be tested.**
- `ACTIVITY_RECOGNITION` is a runtime permission only at API 29+; below that, `NOT_APPLICABLE`.
- `request` must be safe to call when no `Activity` is available (e.g. from the service) — returning
  `Denied` rather than throwing.

**Fake**: `FakePermissionController` returns scripted results per capability, including the
API-24-vs-30 background divergence and the `PermanentlyDenied` path.

---

## `ActivityRecognizer` *(R-07, battery gating)*

```kotlin
interface ActivityRecognizer {
    fun inVehicleTransitions(): Flow<VehicleTransition>
}

enum class VehicleTransition { ENTERED_VEHICLE, EXITED_VEHICLE }
```

**Contract**: transition-based, not polling — this is the largest battery lever available (R-07).
Used only to decide when to start and stop the location tiers; it must **not** be a state-machine
input, because speed from the location provider is the spec's authority for transitions (spec
Assumptions). Treat it as advisory: if activity recognition is unavailable or permission is denied,
the app falls back to continuous low-power location monitoring with a worse battery profile but
identical correctness.

**Fake**: `FakeActivityRecognizer` emits scripted transitions.

---

## `Clock`

```kotlin
interface Clock {
    fun nowEpochMillis(): Long
}
```

Injected into the state machine so convergence-window and interval tests run instantly rather than
waiting real seconds. **Fake**: `FakeClock` with caller-advanced time.

---

## Background execution *(FR-014, `:app` side)*

Not a platform adapter — it is Android-only host wiring, deliberately outside `:shared` because it
has no domain meaning. `ParkingDetectionService` (location-typed foreground service, R-08) owns the
`ParkingStateMachine`, feeds it from `LocationProvider`, and persists every transition through
`ParkedLocationRepository`.

**Contract with the UI**: strictly one-directional. The service writes; the UI reads through
`repository.observe()`. The UI holds no reference to the service, cannot bind to it, and cannot call
into the state machine. This is how FR-014's "foreground must not affect the state machine" is
enforced structurally rather than by discipline.
