package com.packmuleforge.carfindermvp.shared.persistence

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import kotlinx.serialization.Serializable

/**
 * The one persisted record: current lifecycle state plus the optional Parked Location. It is always written as a
 * whole, so state and location can never disagree on disk.
 *
 * @requirement FR-011, FR-013, FR-014, FR-018
 */
@Serializable
data class PersistedParkingRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val state: LifecycleState = LifecycleState.FINDING,
    val parkedLocation: ParkedLocation? = null,
) {
    /**
     * Enforces "a Parked Location is held if and only if the state is PARKED": PARKED without a location falls
     * back to FINDING, and any other state drops a stray location.
     *
     * @requirement FR-018
     */
    fun normalized(): PersistedParkingRecord = when {
        state == LifecycleState.PARKED && parkedLocation == null -> DEFAULT
        state != LifecycleState.PARKED && parkedLocation != null -> copy(parkedLocation = null)
        else -> this
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** Fresh install, and the fallback for missing, unreadable or corrupted storage. */
        val DEFAULT = PersistedParkingRecord()
    }
}
