package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord

/**
 * Everything the reducer needs. [lifecycle] is PARKED if and only if [parkedLocation] is not `null` (FR-017).
 *
 * @requirement FR-001, FR-017
 */
data class MachineSnapshot(
    val lifecycle: LifecycleState = FINDING,
    val parkedLocation: ParkedLocation? = null,
    val speedFilter: SpeedFilter = SpeedFilter(),
    val window: ConvergenceWindow = ConvergenceWindow(),
) {
    fun toRecord() = PersistedParkingRecord(state = lifecycle, parkedLocation = parkedLocation)

    companion object {
        val INITIAL = MachineSnapshot()
    }
}

sealed interface MachineEvent {
    data class Reading(val reading: LocationReading, val nowEpochMillis: Long) : MachineEvent
    data class Restored(val record: PersistedParkingRecord) : MachineEvent
}

/**
 * The result of one reduction. [persist] is true when the stored record (state or location) changed.
 * [normalizedOnRestore] is true when a restored record had to be corrected (FR-020).
 */
data class Transition(
    val from: LifecycleState,
    val snapshot: MachineSnapshot,
    val persist: Boolean,
    val normalizedOnRestore: Boolean = false,
)

/**
 * The lifecycle reducer: pure, deterministic, no clock and no I/O. Events carry their own times.
 *
 * @requirement FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-009, FR-012, FR-013, FR-014, FR-015, FR-017
 */
object ParkingStateMachine {

    fun reduce(snapshot: MachineSnapshot, event: MachineEvent): Transition = when (event) {
        is MachineEvent.Reading -> onReading(snapshot, event)
        is MachineEvent.Restored -> onRestored(snapshot, event.record)
    }

    private fun onRestored(snapshot: MachineSnapshot, record: PersistedParkingRecord): Transition {
        val normalized = record.normalized()
        val changed = normalized != record
        return Transition(
            from = snapshot.lifecycle,
            snapshot = MachineSnapshot(lifecycle = normalized.state, parkedLocation = normalized.parkedLocation),
            persist = changed,
            normalizedOnRestore = changed,
        )
    }

    private fun onReading(snapshot: MachineSnapshot, event: MachineEvent.Reading): Transition {
        val reading = event.reading
        val speedMph = reading.speedMph
        val filter = if (speedMph != null) snapshot.speedFilter.add(speedMph) else snapshot.speedFilter
        // A speed rule is evaluated only on a reading that itself carries a speed (FR-009).
        val smoothed = if (speedMph != null) filter.smoothed else null
        val current = snapshot.copy(speedFilter = filter)

        // Rule 1: drive check first.
        if (smoothed != null && smoothed > CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH &&
            (current.lifecycle == FINDING || current.lifecycle == PARKING)
        ) {
            return changed(snapshot, current.copy(lifecycle = DRIVING, parkedLocation = null, window = ConvergenceWindow()))
        }

        return when (current.lifecycle) {
            // Rule 2: the reading that causes the entry is not added to the window.
            DRIVING -> if (smoothed != null && smoothed <= CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH) {
                changed(snapshot, current.copy(lifecycle = PARKING, window = ConvergenceWindow()))
            } else {
                unchanged(snapshot, current)
            }

            PARKING -> {
                val window = current.window.add(reading)
                if (window.isConverged) {
                    // Rule 3.
                    changed(
                        snapshot,
                        current.copy(
                            lifecycle = PARKED,
                            parkedLocation = window.toParkedLocation(event.nowEpochMillis),
                            window = ConvergenceWindow(),
                        ),
                    )
                } else {
                    // Rule 4: the window slides, with no timeout.
                    unchanged(snapshot, current.copy(window = window))
                }
            }

            FINDING, PARKED -> unchanged(snapshot, current)
        }
    }

    private fun changed(before: MachineSnapshot, after: MachineSnapshot) =
        Transition(from = before.lifecycle, snapshot = after, persist = after.toRecord() != before.toRecord())

    private fun unchanged(before: MachineSnapshot, after: MachineSnapshot) =
        Transition(from = before.lifecycle, snapshot = after, persist = false)
}
