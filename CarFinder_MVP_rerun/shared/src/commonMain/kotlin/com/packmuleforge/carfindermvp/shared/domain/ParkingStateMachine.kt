package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord

/** The complete machine state. Every field is an immutable value. */
data class MachineSnapshot(
    val lifecycle: LifecycleState,
    val parkedLocation: ParkedLocation?,
    val speedFilter: SpeedMedianFilter,
    val window: ConvergenceWindow,
) {
    companion object {
        fun initial() = MachineSnapshot(FINDING, null, SpeedMedianFilter.empty(), ConvergenceWindow.empty())
    }
}

sealed interface MachineEvent {
    /** A location reading; [nowEpochMillis] timestamps a Parked Location created by this reading. */
    data class Reading(val reading: LocationReading, val nowEpochMillis: Long) : MachineEvent

    /** The record read back from storage at process start. */
    data class Restored(val record: PersistedParkingRecord) : MachineEvent
}

/**
 * The result of one reduce step: the complete next snapshot, and whether the persisted record must be rewritten.
 */
data class Transition(
    val from: LifecycleState,
    val snapshot: MachineSnapshot,
    val persist: Boolean,
) {
    val to: LifecycleState get() = snapshot.lifecycle
    val parkedLocation: ParkedLocation? get() = snapshot.parkedLocation
}

/**
 * The parking lifecycle as a pure reducer: no clock, no I/O, and the input snapshot is never modified.
 *
 * @requirement FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-013,
 *   FR-015, FR-018, FR-032
 */
object ParkingStateMachine {

    fun reduce(snapshot: MachineSnapshot, event: MachineEvent): Transition = when (event) {
        is MachineEvent.Reading -> onReading(snapshot, event)
        is MachineEvent.Restored -> onRestored(snapshot, event.record)
    }

    private fun onRestored(snapshot: MachineSnapshot, record: PersistedParkingRecord): Transition {
        val normalized = record.normalized()
        val next = MachineSnapshot(
            lifecycle = normalized.state,
            parkedLocation = normalized.parkedLocation,
            speedFilter = SpeedMedianFilter.empty(snapshot.speedFilter.windowSize),
            window = ConvergenceWindow.empty(),
        )
        return Transition(from = snapshot.lifecycle, snapshot = next, persist = normalized != record)
    }

    private fun onReading(snapshot: MachineSnapshot, event: MachineEvent.Reading): Transition {
        val reading = event.reading
        val speedMph = reading.speedMph
        val filter = if (speedMph != null) snapshot.speedFilter.add(speedMph) else snapshot.speedFilter
        // A reading without speed never causes a speed transition, even if an older filtered value exists.
        val v = if (speedMph != null) filter.filtered else null
        val base = snapshot.copy(speedFilter = filter)

        val next = when {
            v != null && v > CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH && snapshot.lifecycle.canEnterDriving() ->
                base.copy(lifecycle = DRIVING, parkedLocation = null, window = ConvergenceWindow.empty())

            snapshot.lifecycle == DRIVING && v != null && v <= CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH ->
                base.copy(lifecycle = PARKING, window = ConvergenceWindow.empty())

            snapshot.lifecycle == PARKING -> {
                val window = snapshot.window.add(reading)
                if (window.isConverged) {
                    base.copy(
                        lifecycle = PARKED,
                        parkedLocation = window.toParkedLocation(event.nowEpochMillis),
                        window = ConvergenceWindow.empty(),
                    )
                } else {
                    base.copy(window = window)
                }
            }

            else -> base
        }
        val persist = next.lifecycle != snapshot.lifecycle || next.parkedLocation != snapshot.parkedLocation
        return Transition(from = snapshot.lifecycle, snapshot = next, persist = persist)
    }

    /**
     * Any state except DRIVING itself enters DRIVING on filtered speed. From PARKED this is drive-away, the only
     * exit from PARKED, and it deletes the Parked Location (the transition sets parkedLocation = null).
     *
     * @requirement FR-003, FR-009, FR-015
     */
    private fun LifecycleState.canEnterDriving() = this != DRIVING
}
