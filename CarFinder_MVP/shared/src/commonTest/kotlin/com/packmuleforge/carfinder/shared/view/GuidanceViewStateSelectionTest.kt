package com.packmuleforge.carfinder.shared.view

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Requirement("FR-018", "FR-019", "FR-020", "FR-021", "FR-030", "FR-035")
class GuidanceViewStateSelectionTest {

    @Test
    fun drivingWithNoStoredLocationYieldsDrivingState() {
        // FR-019: DRIVING state (with no location ever stored) yields Driving
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.DRIVING,
            parkedLocation = null,
            currentFix = GeoPoint(40.0, -74.0, 5.0),
            deviceHeading = 0.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.Driving>(result)
    }

    @Test
    fun findingWithNoLocationYieldsNoParkedLocation() {
        // FR-020: FINDING state without a parked location yields NoParkedLocation
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.FINDING,
            parkedLocation = null,
            currentFix = GeoPoint(40.0, -74.0, 5.0),
            deviceHeading = 0.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun parkingStateYieldsParkingSoon() {
        // FR-020: PARKING state yields ParkingSoon
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKING,
            parkedLocation = null,
            currentFix = GeoPoint(40.0, -74.0, 5.0),
            deviceHeading = 0.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.ParkingSoon>(result)
    }

    @Test
    fun parkedWithLocationYieldsGuidance() {
        // FR-021: PARKED state with a location yields Guidance
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 900L
        )
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLoc,
            currentFix = GeoPoint(40.01, -74.01, 5.0),
            deviceHeading = 45.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.Guidance>(result)
    }

    @Test
    fun priorityOrderDrivingOutranksParkedLocation() {
        // FR-018 (strict priority order): DRIVING outranks having a parked location
        // Even with a location, DRIVING → Driving (not Guidance)
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 900L
        )
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.DRIVING,
            parkedLocation = parkedLoc,
            currentFix = GeoPoint(40.01, -74.01, 5.0),
            deviceHeading = 45.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.Driving>(result)
    }

    @Test
    fun nullCurrentFixFallsBackToNoParkedLocation() {
        // FR-030: null currentFix resolves to NoParkedLocation
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 900L
        )
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLoc,
            currentFix = null,
            deviceHeading = 45.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun nullHeadingFallsBackToNoParkedLocation() {
        // FR-030: null heading resolves to NoParkedLocation
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 900L
        )
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = parkedLoc,
            currentFix = GeoPoint(40.01, -74.01, 5.0),
            deviceHeading = null,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }

    @Test
    fun findingOutranksParkedLocationWhenDriving() {
        // FR-018: FINDING without location yields NoParkedLocation (not Guidance),
        // even if a location was previously stored
        val parkedLoc = ParkedLocation(
            point = GeoPoint(40.0, -74.0, 5.0),
            capturedAtEpochMillis = 900L
        )
        val result = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.FINDING,
            parkedLocation = parkedLoc,
            currentFix = GeoPoint(40.01, -74.01, 5.0),
            deviceHeading = 45.0,
            currentFixAgeMillis = null
        )
        assertIs<GuidanceViewState.NoParkedLocation>(result)
    }
}
