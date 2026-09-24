package com.packmuleforge.carfinder.shared.repository

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.fake.FakeParkedLocationRepository
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Requirement("FR-009", "FR-011", "FR-012", "FR-013", "FR-037")
class ParkedLocationRepositoryContractTest {
    @Test
    fun loadWithNoStoredRecordReturnsFindingNull() = runTest {
        val repo = FakeParkedLocationRepository()
        val data = repo.load()
        assertEquals(ParkingState.FINDING, data.state, "Initial state should be FINDING")
        assertNull(data.parkedLocation, "Initial location should be null")
    }

    @Test
    fun loadReturnsInitialStateAndLocation() = runTest {
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val repo = FakeParkedLocationRepository(
            initialState = ParkingState.PARKED,
            initialLocation = location
        )
        val data = repo.load()
        assertEquals(ParkingState.PARKED, data.state)
        assertEquals(location.point.latitudeDegrees, data.parkedLocation?.point?.latitudeDegrees)
    }

    @Test
    fun saveWritesStateAndLocationAtomically() = runTest {
        val repo = FakeParkedLocationRepository()
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = 1000L
        )

        repo.save(ParkingState.PARKED, location)

        val saved = repo.load()
        assertEquals(ParkingState.PARKED, saved.state)
        assertEquals(location.point.latitudeDegrees, saved.parkedLocation?.point?.latitudeDegrees)
        assertEquals(1000L, saved.parkedLocation?.capturedAtEpochMillis)
    }

    @Test
    fun clearLocationTransitionsToFinding() = runTest {
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val repo = FakeParkedLocationRepository(
            initialState = ParkingState.PARKED,
            initialLocation = location
        )

        repo.clearLocation()

        val cleared = repo.load()
        assertEquals(ParkingState.FINDING, cleared.state)
        assertNull(cleared.parkedLocation)
    }

    @Test
    fun observeEmitsCurrentStateOnSubscription() = runTest {
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val repo = FakeParkedLocationRepository(
            initialState = ParkingState.PARKED,
            initialLocation = location
        )

        val data = repo.observe().first()

        assertEquals(ParkingState.PARKED, data.state)
        assertEquals(location.point.latitudeDegrees, data.parkedLocation?.point?.latitudeDegrees)
    }
}
