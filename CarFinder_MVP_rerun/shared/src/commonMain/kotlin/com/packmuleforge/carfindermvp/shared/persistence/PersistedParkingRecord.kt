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
    companion object {
        val CURRENT_SCHEMA_VERSION: Int = 1
        val DEFAULT = PersistedParkingRecord(state = LifecycleState.FINDING, parkedLocation = null)
    }
}
