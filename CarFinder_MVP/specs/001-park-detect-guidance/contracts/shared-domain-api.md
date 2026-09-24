# Contract: Shared Domain API

**Module**: `:shared` / `commonMain` | **Consumers**: `:app` (Android), future `:iosApp`

This is the public surface `:shared` exposes. Everything here is platform-free (Principle V) and
unit-testable without a device (Principle I). Signatures are the contract; bodies are implementation.

---

## Traceability annotation *(FR-038, FR-039)*

```kotlin
package com.packmuleforge.carfinder.shared.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class Requirement(vararg val ids: String)
```

`SOURCE` retention keeps it out of the runtime image — development-time tooling only. Applied to
both implementing declarations and verifying tests:

```kotlin
@Requirement("FR-006", "FR-008")
class ConvergenceWindow(…)

@Requirement("FR-006")
@Test
fun `three samples within convergence radius converge`() { … }
```

---

## Constants *(FR-034)*

```kotlin
object ParkingConstants {
    const val PARKING_SPEED_THRESHOLD_MPH = 5.0
    const val DRIVING_SPEED_THRESHOLD_MPH = 25.0
    const val CONVERGENCE_RADIUS_METERS = 10.0
    const val CONVERGENCE_SAMPLE_COUNT = 3
    const val PARKING_SAMPLE_INTERVAL_MILLIS = 5_000L
    const val ARRIVAL_CONE_HALF_ANGLE_DEGREES = 45.0
    const val DISTANCE_UNIT_THRESHOLD_FEET = 500.0

    // Derived SI forms — single-sourced from the above, never re-typed as literals
    val PARKING_SPEED_THRESHOLD_MPS: Double
    val DRIVING_SPEED_THRESHOLD_MPS: Double
    val ARRIVAL_CONE_HALF_ANGLE_RADIANS: Double
    val DISTANCE_UNIT_THRESHOLD_METERS: Double
}
```

**Contract**: no literal from the left column may appear anywhere else in `shared/src` or `app/src`,
including tests (FR-034, FR-037). The traceability script is not the enforcement mechanism for this;
code review is.

---

## State machine *(FR-001–FR-010)*

```kotlin
class ParkingStateMachine(
    private val repository: ParkedLocationRepository,
    private val clock: Clock,
) {
    val state: StateFlow<ParkingState>

    /** Feeds one observation. The only way state ever changes. */
    suspend fun onLocationSample(sample: LocationSample)

    /** Restores persisted state on cold start (FR-011). */
    suspend fun restore()
}
```

**Contract guarantees**:

- `onLocationSample` is the sole mutator. No public method changes state, which is what makes FR-014's
  "the foreground app cannot perturb the state machine" structurally true rather than a convention.
- Speed smoothing (R-07) is applied *inside* the machine, before threshold comparison — it is domain
  behavior, not adapter behavior.
- Transitions are exactly the table in [data-model.md](../data-model.md). No timeout exists anywhere
  (FR-008).
- `Clock` is injected so tests control time without waiting 5 real seconds.

---

## Convergence *(FR-006, FR-008)*

```kotlin
class ConvergenceWindow(private val capacity: Int = CONVERGENCE_SAMPLE_COUNT) {
    fun add(sample: LocationSample)
    fun isConverged(): Boolean          // all pairwise distances ≤ CONVERGENCE_RADIUS_METERS
    fun centroid(): GeoPoint?           // null unless converged
    fun clear()
}
```

`isConverged` uses **all-pairwise** distance, not distance-from-first — see the note in
[data-model.md](../data-model.md). `centroid()` must guard the antimeridian case.

---

## Geodesy and guidance math *(FR-023, FR-024, FR-028, FR-029, FR-031)*

```kotlin
object Geodesy {
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double
    fun trueBearingDegrees(from: GeoPoint, to: GeoPoint): Double   // 0…360, true north
}

object UncertaintyCalculator {
    @Requirement("FR-015", "FR-016")
    fun uncertaintyRadiusMeters(parked: GeoPoint, current: GeoPoint): Double
}

object ConeGeometry {
    @Requirement("FR-023")
    fun halfAngleRadians(uncertaintyRadiusMeters: Double, distanceMeters: Double): Double

    @Requirement("FR-024")
    fun displayBearingDegrees(deviceHeadingDegrees: Double, bearingToCarDegrees: Double): Double

    @Requirement("FR-031")
    fun hasArrived(halfAngleRadians: Double): Boolean
}

object DistanceFormatter {
    @Requirement("FR-029")
    fun format(distanceMeters: Double): String     // feet at/below threshold, else miles
}
```

**Contract notes**:

- `trueBearingDegrees` returns a bearing relative to **true** north. `displayBearingDegrees` expects
  `deviceHeadingDegrees` to already be true-north corrected. The magnetic-declination conversion is
  the Android adapter's job (R-05) — the shared layer must not silently mix reference frames.
- `halfAngleRadians` returns `π/2` at zero distance rather than NaN (arrival).
- `displayBearingDegrees` always returns a value in `[0, 360)`.

---

## View state calculation *(FR-018–FR-022, FR-030, FR-042)*

```kotlin
sealed interface GuidanceViewState {
    data object Driving : GuidanceViewState
    data object NoParkedLocation : GuidanceViewState
    data object ParkingSoon : GuidanceViewState
    data class Guidance(
        val distanceMeters: Double,
        val formattedDistance: String,
        val displayBearingDegrees: Double,
        val coneHalfAngleRadians: Double,
        val hasArrived: Boolean,
    ) : GuidanceViewState
}

object GuidanceViewStateCalculator {
    @Requirement("FR-018", "FR-019", "FR-020", "FR-021", "FR-022", "FR-030")
    fun calculate(
        state: ParkingState,
        parkedLocation: ParkedLocation?,
        currentFix: GeoPoint?,
        deviceHeadingDegrees: Double?,
    ): GuidanceViewState
}
```

**Contract**: `calculate` is a pure function — same inputs, same output, no I/O, no clock. This is
what FR-042 requires and what makes the four-way selection exhaustively testable. It is total: every
input combination, including all-nulls, yields a variant. Null `currentFix` or null heading resolves
to `NoParkedLocation` per FR-030.

---

## Persistence interface *(FR-011, FR-013)*

```kotlin
data class PersistedParkingData(
    val state: ParkingState,
    val parkedLocation: ParkedLocation?,
)

interface ParkedLocationRepository {
    fun observe(): Flow<PersistedParkingData>
    suspend fun load(): PersistedParkingData
    suspend fun save(data: PersistedParkingData)
    suspend fun clearLocation()
}
```

**Contract guarantees the implementation must honor**:

- `save` is **atomic** across both fields. A crash mid-write must leave the prior record intact, not
  a state/location mismatch — this is the FR-011 failure mode.
- After `clearLocation()`, neither `load()` nor `observe()` may ever surface the cleared location
  (FR-013).
- `load()` on a device with no stored record returns `PersistedParkingData(FINDING, null)` — the
  FR-009 initial state, not an error and not an empty optional.
