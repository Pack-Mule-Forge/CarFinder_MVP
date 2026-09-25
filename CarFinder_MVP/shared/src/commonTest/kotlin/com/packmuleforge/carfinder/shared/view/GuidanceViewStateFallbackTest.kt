package com.packmuleforge.carfinder.shared.view

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlin.test.Test
import kotlin.test.assertIs

@Requirement("FR-030", "FR-043")
class GuidanceViewStateFallbackTest {
    private val parkedLocation = ParkedLocation(
        point = GeoPoint(37.7749, -122.4194, 15.0),
        capturedAtEpochMillis = 0L
    )
    private val currentFix = GeoPoint(37.7750, -122.4195, 10.0)
    private val heading = 45.0

    @Test
    fun parkedWithCurrentFixAndHeadingYieldsGuidance() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLocation,
            currentFix = currentFix,
            currentFixAgeMillis = 0L,
            deviceHeading = heading
        )
        assertIs<GuidanceViewState.Guidance>(result)
    }

    @Test
    fun parkedWithNoCurrentFixFallsBackToNoParkedLocation() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLocation,
            currentFix = null,
            currentFixAgeMillis = 0L,
            deviceHeading = heading
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun parkedWithStaleFixFallsBackToNoParkedLocation() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLocation,
            currentFix = currentFix,
            currentFixAgeMillis = ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1L,
            deviceHeading = heading
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun parkedWithNoHeadingFallsBackToNoParkedLocation() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLocation,
            currentFix = currentFix,
            currentFixAgeMillis = 0L,
            deviceHeading = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun fixAgeExactlyAtTimeoutStillCountsAsCurrent() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLocation,
            currentFix = currentFix,
            currentFixAgeMillis = ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS,
            deviceHeading = heading
        )
        assertIs<GuidanceViewState.Guidance>(result)
    }

    @Test
    fun drivingOutranksFallback() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.DRIVING,
            parkedLocation = parkedLocation,
            currentFix = null,
            currentFixAgeMillis = ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1L,
            deviceHeading = null
        )
        assertIs<GuidanceViewState.Driving>(result)
    }

    @Test
    fun parkingOutranksFallback() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKING,
            parkedLocation = parkedLocation,
            currentFix = null,
            currentFixAgeMillis = ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1L,
            deviceHeading = null
        )
        assertIs<GuidanceViewState.ParkingSoon>(result)
    }

    @Test
    fun allNullInputsStillYieldAVariant() {
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.FINDING,
            parkedLocation = null,
            currentFix = null,
            currentFixAgeMillis = null,
            deviceHeading = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }
}
