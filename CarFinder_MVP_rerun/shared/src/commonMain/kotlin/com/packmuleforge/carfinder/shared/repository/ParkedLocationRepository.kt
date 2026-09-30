package com.packmuleforge.carfinder.shared.repository

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlinx.coroutines.flow.Flow

/**
 * Persisted parking data: the current state and optionally the stored Parked Location.
 * Both fields are updated atomically to satisfy FR-011.
 */
@Requirement("FR-011")
data class PersistedParkingData(
    val state: ParkingState,
    val parkedLocation: ParkedLocation? = null
)

/**
 * Interface for persisting the parking state and Parked Location. Implemented only on the
 * platform side (Android DataStore in Phase 3). The shared module depends only on this interface.
 *
 * @requirement FR-011 System MUST persist state and location such that both survive process death
 * @requirement FR-012 System MUST hold at most one Parked Location at any time
 * @requirement FR-013 After deletion, no read may return the location
 */
@Requirement("FR-011", "FR-012", "FR-013")
interface ParkedLocationRepository {
    /**
     * Observe the current state and location. Emits immediately on subscription with the last
     * saved or default value (FINDING, null).
     */
    fun observe(): Flow<PersistedParkingData>

    /**
     * Load the current state and location synchronously. Returns (FINDING, null) if nothing has
     * been saved.
     */
    suspend fun load(): PersistedParkingData

    /**
     * Save state and location atomically. Both fields are written together; partial writes are not
     * permitted (FR-011).
     */
    suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?)

    /**
     * Delete the parked location and enter FINDING. After deletion, subsequent reads must not
     * return the deleted location (FR-013).
     */
    suspend fun clearLocation()
}
