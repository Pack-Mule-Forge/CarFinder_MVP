package com.packmuleforge.carfindermvp.shared.persistence

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import kotlinx.serialization.Serializable

/**
 * The one stored record: lifecycle state and the optional Parked Location, written as one unit.
 *
 * @requirement FR-017, FR-018
 */
@Serializable
data class PersistedParkingRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val state: LifecycleState,
    val parkedLocation: ParkedLocation?,
) {
    /**
     * The record made consistent with FR-017: PARKED without a location becomes FINDING, and a location held in
     * any other state is dropped. A consistent record is returned unchanged.
     *
     * @requirement FR-017, FR-020
     */
    fun normalized(): PersistedParkingRecord = when {
        state == LifecycleState.PARKED && parkedLocation == null -> DEFAULT
        state != LifecycleState.PARKED && parkedLocation != null -> copy(parkedLocation = null)
        else -> this
    }

    companion object {
        val CURRENT_SCHEMA_VERSION: Int = 1
        val DEFAULT = PersistedParkingRecord(state = LifecycleState.FINDING, parkedLocation = null)
    }
}
