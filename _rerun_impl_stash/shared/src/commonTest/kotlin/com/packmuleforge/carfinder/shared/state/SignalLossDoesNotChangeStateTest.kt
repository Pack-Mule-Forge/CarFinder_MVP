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

@Requirement("FR-009", "FR-014", "FR-030")
class SignalLossDoesNotChangeStateTest {
    @Test
    fun signalLossPastStalenessTimeoutLeavesParkedStateAndLocationIntact() = runTest {
        val parkedLocation = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = 0L
        )
        val repo = FakeParkedLocationRepository(
            initialState = ParkingState.PARKED,
            initialLocation = parkedLocation
        )
        val clock = FakeClock()
        val machine = ParkingStateMachine(repo, clock)
        machine.restore()

        // FR-009/FR-014: no samples arrive (signal loss); the machine runs independently
        // of foreground state and must not react to elapsed time on its own.
        clock.advanceBy(ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1L)

        assertEquals(ParkingState.PARKED, machine.currentState(), "State must remain PARKED across signal loss")
        assertEquals(parkedLocation, repo.load().parkedLocation, "Stored location must remain intact across signal loss")

        // FR-030: a later sample (dead zone speed) resumes normal operation with no state change
        val resumedSample = LocationSample(
            point = parkedLocation.point,
            speedMetersPerSecond = ParkingConstants.PARKING_SPEED_THRESHOLD_MPS,
            timestampEpochMillis = clock.nowEpochMillis()
        )
        machine.onLocationSample(resumedSample)

        assertEquals(ParkingState.PARKED, machine.currentState(), "Resumed sample must not change state")
        assertEquals(parkedLocation, repo.load().parkedLocation, "Resumed sample must not change stored location")
    }
}
