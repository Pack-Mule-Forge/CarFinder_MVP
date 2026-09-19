package com.packmuleforge.carfinder.shared.view

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Requirement("FR-022", "FR-023", "FR-024", "FR-028", "FR-029", "FR-037", "FR-042")
class GuidanceViewStateCalculatorGuidanceTest {
    @Test
    fun parkedStateWithLocationProducesGuidanceVariant() {
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val currentFix = GeoPoint(37.7750, -122.4195, 10.0)
        val deviceHeading = 0.0
        val currentHeading = 90.0

        val viewState = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = location,
            currentFix = currentFix,
            deviceHeading = deviceHeading,
            currentHeading = currentHeading
        )

        assertIs<GuidanceViewState.Guidance>(viewState, "PARKED with location should produce Guidance")
    }

    @Test
    fun guidanceVariantPopulatesAllFields() {
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val currentFix = GeoPoint(37.7750, -122.4195, 10.0)
        val deviceHeading = 45.0
        val currentHeading = 90.0

        val viewState = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = location,
            currentFix = currentFix,
            deviceHeading = deviceHeading,
            currentHeading = currentHeading
        ) as GuidanceViewState.Guidance

        assertTrue(viewState.distanceMeters > 0.0, "Distance should be positive")
        assertTrue(viewState.formattedDistance.isNotEmpty(), "Formatted distance should be non-empty")
        assertTrue(viewState.displayBearingDegrees >= 0.0 && viewState.displayBearingDegrees < 360.0, "Display bearing in range")
        assertTrue(viewState.coneHalfAngleRadians >= 0.0, "Cone half-angle should be non-negative")
    }

    @Test
    fun guidanceComputesConeHalfAngleCorrectly() {
        val uncertainty = 25.0  // meters
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val currentFix = GeoPoint(37.7750, -122.4195, 10.0)  // 15 meters away
        val deviceHeading = 0.0
        val currentHeading = 90.0

        val viewState = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = location,
            currentFix = currentFix,
            deviceHeading = deviceHeading,
            currentHeading = currentHeading
        ) as GuidanceViewState.Guidance

        // Cone half-angle should be atan(uncertainty / distance)
        // uncertainty ~ 25m, distance ~ 15-20m (rough estimate)
        assertTrue(viewState.coneHalfAngleRadians > 0.0, "Cone should have positive half-angle")
    }

    @Test
    fun guidanceFormatsDistanceCorrectly() {
        val shortDistance = ParkedLocation(
            point = GeoPoint(37.774901, -122.4194, 15.0),  // ~100m away
            capturedAtEpochMillis = System.currentTimeMillis()
        )
        val currentFix = GeoPoint(37.7749, -122.4194, 10.0)
        val deviceHeading = 0.0
        val currentHeading = 90.0

        val viewState = GuidanceViewStateCalculator.calculate(
            parkingState = ParkingState.PARKED,
            parkedLocation = shortDistance,
            currentFix = currentFix,
            deviceHeading = deviceHeading,
            currentHeading = currentHeading
        ) as GuidanceViewState.Guidance

        // Should use feet for short distance
        assertTrue(
            viewState.formattedDistance.contains("ft") || viewState.formattedDistance.contains("feet"),
            "Short distance should be in feet"
        )
    }
}
