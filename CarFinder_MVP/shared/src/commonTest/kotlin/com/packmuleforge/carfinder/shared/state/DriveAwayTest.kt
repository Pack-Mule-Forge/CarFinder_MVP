package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.fake.FakeClock
import com.packmuleforge.carfinder.shared.fake.FakeParkedLocationRepository
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Requirement("FR-010", "FR-013")
class DriveAwayTest {

    @Test
    fun driveAwayFromParkedDeletesLocationAndTransitionsToDriving() = runTest {
        // FR-010: From PARKED state, speed above DRIVING_SPEED_THRESHOLD transitions
        // to DRIVING and deletes the parked location
        val clock = FakeClock()
        val repository = FakeParkedLocationRepository()
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 100L
        )
        repository.save(ParkingState.PARKED, parkedLoc)

        val samples = listOf(
            LocationSample(GeoPoint(40.0, -74.0, 5.0), 1.0, 200L),  // Slow, parked
            LocationSample(GeoPoint(40.01, -74.0, 5.0), 15.0, 300L)  // Fast, driving threshold
        )

        val stateMachine = ParkingStateMachine(
            repository = repository,
            clock = clock
        )

        // Start in PARKED state
        stateMachine.restore()
        assertEquals(ParkingState.PARKED, stateMachine.currentState())

        // Feed location samples
        samples.forEach { sample ->
            stateMachine.onLocationSample(sample)
        }

        // Should transition to DRIVING when speed exceeds threshold
        assertEquals(ParkingState.DRIVING, stateMachine.currentState())

        // Location should be deleted
        val loadedData = repository.load()
        assertNull(loadedData.parkedLocation)
    }

    @Test
    fun driveAwayFromFindingIsNoOpIfNoLocation() = runTest {
        // FR-010: From FINDING state with no location, drive-away is a no-op (not an error)
        val clock = FakeClock()
        val repository = FakeParkedLocationRepository()
        // Start with no location (FINDING, null)

        val stateMachine = ParkingStateMachine(
            repository = repository,
            clock = clock
        )

        // Start in FINDING state with no location
        stateMachine.restore()
        assertEquals(ParkingState.FINDING, stateMachine.currentState())

        // Feed high-speed sample (drive-away)
        stateMachine.onLocationSample(LocationSample(GeoPoint(40.01, -74.0, 5.0), 15.0, 300L))

        // Should transition to DRIVING
        assertEquals(ParkingState.DRIVING, stateMachine.currentState())

        // No location to delete, but clearLocation() should be no-op
        val loadedData = repository.load()
        assertNull(loadedData.parkedLocation)
    }

    @Test
    fun loadingAfterDriveAwayReturnsNull() = runTest {
        // FR-010: After deletion via drive-away, load() returns null
        val repository = FakeParkedLocationRepository()
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 100L
        )
        repository.save(ParkingState.PARKED, parkedLoc)

        // Simulate drive-away deletion
        repository.clearLocation()

        val loadedLoc = repository.load()
        assertNull(loadedLoc)
    }
}
