# Contract: Shared Domain API (`:shared` `commonMain`)

This is the public Kotlin surface that `:app` (and later an iOS app) consumes. Everything not listed here is
`internal`. The entity fields are defined in [../data-model.md](../data-model.md). Requirement IDs show which
parts of the spec each item implements. Each implementing declaration carries a matching KDoc `@requirement` tag.

## Pure domain functions and types

```kotlin
object CarFinderConstants { /* FR-030 values; see data-model */ }
object TuningConstants     { /* plan-level knobs; see data-model */ }

// Immutable value: add() returns a new filter and never changes the receiver.
class SpeedMedianFilter private constructor(private val speeds: List<Double>, val windowSize: Int) {
    val filtered: Double?                                   // median once full, else null            — FR-032
    fun add(speedMph: Double): SpeedMedianFilter
    companion object {
        fun empty(windowSize: Int = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE): SpeedMedianFilter
    }
}

// Immutable value: add() returns a new window and never changes the receiver.
class ConvergenceWindow private constructor(
    private val readings: List<LocationReading>,
    val sampleCount: Int,
    val radiusMeters: Double,
) {
    val isConverged: Boolean                                // pairwise test                          — FR-007
    val lastElapsedRealtimeMillis: Long?                    // newest reading's monotonic time        — FR-035
    fun add(reading: LocationReading): ConvergenceWindow    // ignores readings with null accuracy
    fun toParkedLocation(capturedAtEpochMillis: Long): ParkedLocation   // centroid + accuracy    — FR-012
    companion object {
        fun empty(
            sampleCount: Int = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT,
            radiusMeters: Double = CarFinderConstants.CONVERGENCE_RADIUS_METERS,
        ): ConvergenceWindow
    }
}

object GeoMath {
    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double    // haversine
    fun initialBearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double // [0,360)
}

object GuidanceCalculator {
    fun uncertaintyMeters(parked: ParkedLocation, fix: LocationReading): Double           // FR-019
    fun coneHalfAngleDegrees(uncertaintyMeters: Double, distanceMeters: Double): Double   // FR-020
    fun displayBearingDegrees(deviceHeading: Double, bearingToCar: Double): Double        // FR-021
    fun isArrived(halfAngleDegrees: Double): Boolean                                      // FR-028
    fun distanceDisplay(distanceMeters: Double): DistanceDisplay                          // FR-025, FR-026
    fun compute(parked: ParkedLocation, fix: LocationReading, heading: HeadingReading): GuidanceState
}

object ConeGeometryCalculator {
    fun compute(displayBearingDegrees: Double, halfAngleDegrees: Double): ConeGeometry    // FR-022–FR-024
}

object FixCurrency {
    fun isCurrent(fix: LocationReading, nowElapsedMillis: Long): Boolean                  // FR-034
}

object DefaultViewSelector {
    fun select(lifecycle: LifecycleState, guidance: GuidanceState?): ViewKind             // FR-016, FR-031
}

object ParkingStateMachine {
    fun reduce(snapshot: MachineSnapshot, event: MachineEvent): Transition
}
```

`ParkingStateMachine.reduce` covers FR-001 through FR-011, FR-013, FR-015 and FR-018.

### State machine I/O

```kotlin
data class MachineSnapshot(
    val lifecycle: LifecycleState,
    val parkedLocation: ParkedLocation?,
    val speedFilter: SpeedMedianFilter,       // immutable value
    val window: ConvergenceWindow,            // immutable value
)

sealed interface MachineEvent {
    data class Reading(val reading: LocationReading, val nowEpochMillis: Long) : MachineEvent
    data class Restored(val record: PersistedParkingRecord) : MachineEvent
}

data class Transition(
    val from: LifecycleState,
    val snapshot: MachineSnapshot,            // the complete next state
    val persist: Boolean,                     // true if lifecycle or parkedLocation changed
) {
    val to: LifecycleState get() = snapshot.lifecycle
    val parkedLocation: ParkedLocation? get() = snapshot.parkedLocation
}
```

**Guarantees**:
- `reduce` is deterministic. It performs no I/O and reads no clock, because the event supplies the time.
- `reduce` never mutates its input. Calling it twice with the same snapshot and event gives equal results. The
  engine keeps the current snapshot and replaces it with `transition.snapshot` after each call.
- After every `reduce`, `(to == PARKED) == (parkedLocation != null)`.
- `Reading` events with null speed never cause a speed transition.
- A `Reading` in PARKED changes `parkedLocation` only while `parkedLocation.isWithinRecoveryWindow(nowEpochMillis)`
  and only on a new convergence of thinned readings. The corrected location keeps `capturedAtEpochMillis`, and
  `from == to == PARKED` with `persist == true` (FR-035).

## Engine and presenter

```kotlin
class ParkingEngine(adapters: PlatformAdapters, scope: CoroutineScope) {
    val state: StateFlow<EngineState>
    val transitions: SharedFlow<Transition>          // future telemetry/history hook
    val isRunning: Boolean
    suspend fun restore()                            // UI only: read-only; publishes persisted state, starts nothing
    fun start()                                      // service only: restore → actor + location/activity; idempotent
    fun stop()
    fun setGuidanceVisible(visible: Boolean)         // switches IDLE_WATCH ↔ GUIDANCE profile in PARKED
}
```

`ParkingEngine` covers FR-006, FR-014, FR-018 and FR-033 (together with the service), and FR-035's sampling
rule: in PARKED with guidance not visible it requests the `PARKING` profile while the recovery window is open.
A recovery correction is published through `state` and persisted, but is not emitted on `transitions`.

**Entry-point ownership**: `start()` runs detection (the actor loop, location sampling, activity recognition) and
is called only from `ParkingDetectionService`'s startup path. `restore()` is the UI's read-only entry point: it
reads and publishes the persisted state for display, never starts any subscription or the actor, never writes
the store, and has no effect once the engine is running. `setGuidanceVisible` only records visibility until the
engine has restored, so the UI can call it before the service starts without starting anything.

```kotlin
class HomeScreenPresenter(
    engine: ParkingEngine,
    heading: HeadingSource,
    clock: MonotonicClock,
    scope: CoroutineScope,
) {
    val state: StateFlow<HomeScreenState>            // FR-016, FR-017, FR-031, QR-009
    fun onArrivalAnswered(sawCar: Boolean)           // FR-029: dismiss only
}
```

**Presenter guarantees**:
- It recomputes on every engine emission, every heading emission, and every
  `AVAILABILITY_RECHECK_INTERVAL_MILLIS` tick, so staleness is detected without a new fix (FR-034, SC-010).
- `state` is conflated, and equal consecutive states are not re-emitted (`distinctUntilChanged`).
- `onArrivalAnswered` never touches `ParkingEngine` or `ParkingStore`.

## Persistence model

```kotlin
@Serializable data class PersistedParkingRecord(
    val schemaVersion: Int = 1,
    val state: LifecycleState = LifecycleState.FINDING,
    val parkedLocation: ParkedLocation? = null,
) { fun normalized(): PersistedParkingRecord }        // FR-018 invariant

interface ParkingStore {                               // actual mechanism is platform (see platform-adapters)
    suspend fun read(): PersistedParkingRecord         // never throws; corrupted → default record
    suspend fun write(record: PersistedParkingRecord)  // atomic
}
```
