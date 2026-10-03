# Contract: Shared Domain API (`:shared` `commonMain`)

The Kotlin surface `:app` consumes. Field definitions are in [../data-model.md](../data-model.md).
Each declaration carries a KDoc `@requirement` tag for the IDs shown.

## Pure functions and values

```kotlin
object CarFinderConstants                                    // FR-055

class SpeedFilter {                                          // FR-007, FR-008
    val smoothed: Double?                                    // median when full, else null
    fun add(speedMph: Double): SpeedFilter
}

class ConvergenceWindow {                                    // FR-011, FR-016
    val isConverged: Boolean
    val lastReceivedElapsedMillis: Long?
    fun add(reading: LocationReading): ConvergenceWindow     // ignores readings without accuracy — FR-010
    fun toParkedLocation(declaredAtEpochMillis: Long): ParkedLocation
}

object ParkingStateMachine {                                 // FR-001 to FR-006, FR-009, FR-012 to FR-015,
    fun reduce(snapshot: MachineSnapshot, event: MachineEvent): Transition   // FR-017, FR-019 to FR-026
}
sealed interface MachineEvent {
    data class Reading(val reading: LocationReading, val nowEpochMillis: Long) : MachineEvent
    data class Restored(val record: PersistedParkingRecord) : MachineEvent
    data class RecoveryWindowElapsed(val nowEpochMillis: Long) : MachineEvent
}
data class Transition(val from: LifecycleState, val snapshot: MachineSnapshot,
                      val persist: Boolean, val normalizedOnRestore: Boolean)

object SamplingPolicy {                                      // FR-027, FR-028
    fun intervalFor(lifecycle: LifecycleState, isRecoveryOpen: Boolean,
                    isGuidanceVisible: Boolean, isInVehicle: Boolean): Long
}

object GeoMath {
    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double
    fun initialBearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double
}

object GuidanceCalculator {
    fun uncertaintyMeters(parked: ParkedLocation, fix: LocationReading): Double          // FR-030
    fun coneHalfAngleDegrees(uncertaintyMeters: Double, distanceMeters: Double): Double  // FR-031
    fun displayBearingDegrees(deviceHeading: Double, bearingToCar: Double): Double       // FR-032
    fun isArrived(halfAngleDegrees: Double): Boolean                                     // FR-038
    fun distanceText(distanceMeters: Double): String                                     // FR-037
    fun compute(parked: ParkedLocation, fix: LocationReading, heading: HeadingReading): GuidanceState
}

object ConeGeometryCalculator {                                                          // FR-035, FR-036
    fun compute(displayBearingDegrees: Double, halfAngleDegrees: Double): ConeGeometry
}

// The one place the launch order and the FR-056 confirm-or-ask-again loop live.
// A plain function, not part of PermissionController.
sealed interface PermissionSequenceResult {
    data class Completed(val state: PermissionState) : PermissionSequenceResult
    data class CloseConfirmed(val capability: Capability) : PermissionSequenceResult
}
suspend fun requestPermissionsInOrder(                                                    // FR-048, FR-056
    controller: PermissionController,
    confirmDenial: suspend (Capability) -> Boolean,   // true: the user confirmed closing
): PermissionSequenceResult

object DefaultViewSelector {                                                             // FR-040 to FR-042, FR-056
    fun select(denial: DenialConfirmation?, permissions: PermissionState, lifecycle: LifecycleState,
               parked: ParkedLocation?, fix: LocationReading?, heading: HeadingReading?,
               nowElapsedMillis: Long): ViewKind
}
```

**Guarantees**:

- `reduce` is deterministic, performs no I/O, reads no clock and never modifies its input.
- After every `reduce`, `(lifecycle == PARKED) == (parkedLocation != null)`.
- A `Reading` without speed never causes a speed-based transition, whatever the filter holds.
- A `Reading` in PARKED changes `parkedLocation` only under data-model transition rule 6, and the new
  location has the old `declaredAtEpochMillis`.
- `SamplingPolicy.intervalFor` returns only one of the three FR-055 sampling intervals.
- `requestPermissionsInOrder` goes through `Capability` order, one request at a time:
  - `FINE_LOCATION` and `NOTIFICATIONS` (required) are requested whenever not `GRANTED`, whatever
    earlier answers were. On a `false` answer it calls `confirmDenial(capability)`: `false`
    (dismissed) requests the same capability again; `true` (confirmed) returns `CloseConfirmed`
    at once, so no later capability is requested.
  - `BACKGROUND_LOCATION` and `ACTIVITY_RECOGNITION` are requested only when `NOT_REQUESTED`, and
    their denial goes on to the next capability.
  - `BACKGROUND_LOCATION` is skipped while `FINE_LOCATION` is not `GRANTED`.
  - When every capability has been handled it returns `Completed`.

## Engine

```kotlin
class ParkingEngine(adapters: PlatformAdapters, scope: CoroutineScope) {
    val state: StateFlow<EngineState>
    val transitions: SharedFlow<Transition>     // lifecycle changes only; corrections are not emitted
    val isRunning: Boolean
    suspend fun restore()                        // UI: read-only; shows stored state; starts nothing
    fun start()                                  // service only: restore, then actor + sampling
    fun stop()
    fun setGuidanceVisible(visible: Boolean)
}
```

- `start()` is called only from the foreground service (FR-051, FR-052). `restore()` never starts
  sampling, never subscribes to an adapter and never writes the store.
- Every change is persisted before it is published (FR-018).
- After every input the engine applies `SamplingPolicy.intervalFor(...)` and re-issues the location
  request only if the interval changed (FR-027).
- On entering PARKED, on restoring a PARKED record inside its window, and after a correction, the
  engine schedules one `RecoveryWindowElapsed` for the window's end (FR-024, FR-027).

## Presenter

```kotlin
class HomeScreenPresenter(engine: ParkingEngine, adapters: PlatformAdapters, scope: CoroutineScope) {
    val state: StateFlow<HomeScreenState>        // FR-042, FR-043, QR-013
    val isClosePending: StateFlow<Boolean>       // FR-056(a): the app must close; state, not an event
    fun onGuidanceVisible(visible: Boolean)      // starts/stops heading; tells the engine
    fun onArrivalAnswered()                      // FR-039: dismisses the prompt only
    fun runPermissionSequence()                  // FR-048, FR-056: no-op while one runs or a close is pending
    fun onDenialConfirmed()                      // FR-056(a)
    fun onDenialDismissed()                      // FR-056(b): back, outside tap or "Allow"
    fun onClosed()                               // FR-056: the Activity has closed; clears all denial state
    fun cancelPermissionSequence()               // FR-056: the screen went away; clears all denial state
}
```

- State is recomputed on engine state, heading, permission status and a
  `CarFinderConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS` tick (FR-040, FR-041).
- `onArrivalAnswered` never changes the lifecycle state or the Parked Location.
- `runPermissionSequence` launches `requestPermissionsInOrder(adapters.permissions, ::awaitDenialAnswer)`
  in the presenter's scope. `awaitDenialAnswer` sets `DenialConfirmation(capability, false)`, which
  makes the state `PermissionRequired` (FR-042 rule 1), and suspends until `onDenialConfirmed` or
  `onDenialDismissed`. Dismissing clears it and returns `false`. Confirming sets `isClosing`
  (state `Closing`) and returns `true`; on `CloseConfirmed` the presenter waits
  `CarFinderConstants.SHUTDOWN_NOTICE_DURATION_MILLIS` and then sets `isClosePending` to `true`.
  The wait runs in the presenter's scope, so it finishes even if the Activity is stopped.
- `onClosed` and `cancelPermissionSequence` both cancel any running sequence job, clear the
  "running" guard, set `DenialConfirmation` to `null` and `isClosePending` to `false`. After either,
  the next `runPermissionSequence` starts from the beginning and asks again for every required
  permission that is not granted. Nothing from a closed or abandoned screen carries over, even
  though the presenter outlives the Activity.
- The sequence clears its "running" guard in a `finally`, so a cancelled or failed request never
  leaves it set.
- None of these changes the lifecycle state, the Parked Location or the store.
