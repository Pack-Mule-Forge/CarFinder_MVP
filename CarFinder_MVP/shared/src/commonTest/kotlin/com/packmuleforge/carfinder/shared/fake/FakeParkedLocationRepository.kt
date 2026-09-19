package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.repository.PersistedParkingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test fake for ParkedLocationRepository. Keeps state in memory; provides no persistence.
 * Allows scripting the initial state and observing updates.
 */
class FakeParkedLocationRepository(
    initialState: ParkingState = ParkingState.FINDING,
    initialLocation: ParkedLocation? = null
) : ParkedLocationRepository {
    private val stateFlow: MutableStateFlow<PersistedParkingData> = MutableStateFlow(
        PersistedParkingData(initialState, initialLocation)
    )

    override fun observe(): Flow<PersistedParkingData> = stateFlow

    override suspend fun load(): PersistedParkingData = stateFlow.value

    override suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?) {
        stateFlow.value = PersistedParkingData(state, parkedLocation)
    }

    override suspend fun clearLocation() {
        stateFlow.value = PersistedParkingData(stateFlow.value.state, null)
    }
}
