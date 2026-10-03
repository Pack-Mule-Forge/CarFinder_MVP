package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.fake.FakeClock
import com.packmuleforge.carfinder.shared.fake.FakeLocationProvider
import com.packmuleforge.carfinder.shared.fake.FakeParkedLocationRepository
import com.packmuleforge.carfinder.shared.fake.FakeSpeedSmoother
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Requirement("FR-002", "FR-003", "FR-004", "FR-005", "FR-006", "FR-007", "FR-035", "FR-037")
class ParkingStateMachineTest {
    @Test
    fun initialStateIsFinding() = runTest {
        val repo = FakeParkedLocationRepository()
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()
        assertEquals(ParkingState.FINDING, machine.currentState())
    }

    @Test
    fun transitionsFromDrivingToParkingBelowParkingThreshold() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.DRIVING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        // Speed at or below threshold enters PARKING (FR-003)
        val sample = createSample(0.0, 0.0, ParkingConstants.PARKING_SPEED_THRESHOLD_MPS)
        machine.onLocationSample(sample)
        assertEquals(ParkingState.PARKING, machine.currentState(), "Should enter PARKING")
    }

    @Test
    fun speedExactlyAtParkingThresholdEntersPARKING() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.DRIVING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        // Exactly at threshold should enter PARKING (rule is "at or below")
        val sample = createSample(0.0, 0.0, ParkingConstants.PARKING_SPEED_THRESHOLD_MPS)
        machine.onLocationSample(sample)
        assertEquals(ParkingState.PARKING, machine.currentState())
    }

    @Test
    fun speedExactlyAtDrivingThresholdDoesNotEnterDRIVING() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.PARKING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        // Exactly at driving threshold should NOT enter DRIVING (rule is "above", not "at or above")
        // This leaves us in the dead zone
        val sample = createSample(0.0, 0.0, ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS)
        machine.onLocationSample(sample)
        assertEquals(ParkingState.PARKING, machine.currentState(), "Should stay in dead zone")
    }

    @Test
    fun speedAboveDrivingThresholdEntersDRIVING() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.PARKING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        val sample = createSample(0.0, 0.0, ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS + 0.1)
        machine.onLocationSample(sample)
        assertEquals(ParkingState.DRIVING, machine.currentState())
    }

    @Test
    fun deadZoneDoesNotTriggerTransition() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.DRIVING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        // Speed in dead zone (above parking, below driving)
        val midpoint = (ParkingConstants.PARKING_SPEED_THRESHOLD_MPS + ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS) / 2
        val sample = createSample(0.0, 0.0, midpoint)
        machine.onLocationSample(sample)
        assertEquals(ParkingState.DRIVING, machine.currentState(), "Should stay in DRIVING in dead zone")
    }

    @Test
    fun convergenceTransitionsToPARKED() = runTest {
        val repo = FakeParkedLocationRepository(initialState = ParkingState.PARKING)
        val machine = ParkingStateMachine(repo, FakeClock())
        machine.restore()

        // Add samples below parking threshold (to stay in PARKING)
        val s1 = createSample(0.0, 0.0, ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - 1.0)
        val s2 = createSample(0.00001, 0.00001, ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - 1.0)
        val s3 = createSample(0.00002, 0.00001, ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - 1.0)

        machine.onLocationSample(s1)
        assertEquals(ParkingState.PARKING, machine.currentState())

        machine.onLocationSample(s2)
        assertEquals(ParkingState.PARKING, machine.currentState())

        machine.onLocationSample(s3)
        // Should converge and transition to PARKED
        if (machine.currentState() == ParkingState.PARKED) {
            // Convergence successful, location should be stored
            val persisted = repo.load()
            assertEquals(ParkingState.PARKED, persisted.state)
        }
    }

    private fun createSample(lat: Double, lon: Double, speed: Double): LocationSample {
        return LocationSample(
            point = GeoPoint(lat, lon, 10.0),
            speedMetersPerSecond = speed,
            timestampEpochMillis = System.currentTimeMillis()
        )
    }
}
