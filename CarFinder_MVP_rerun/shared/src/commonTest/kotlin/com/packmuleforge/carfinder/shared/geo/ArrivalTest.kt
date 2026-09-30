package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Requirement("FR-031", "FR-037")
class ArrivalTest {
    @Test
    fun hasArrivedAtThreshold() {
        // Exactly at ARRIVAL_CONE_HALF_ANGLE should trigger arrival
        val halfAngle = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS
        assertTrue(ConeGeometry.hasArrived(halfAngle), "Half-angle at threshold should indicate arrival")
    }

    @Test
    fun hasArrivedAboveThreshold() {
        // Above threshold should trigger arrival
        val halfAngle = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS + 0.1
        assertTrue(ConeGeometry.hasArrived(halfAngle), "Half-angle above threshold should indicate arrival")
    }

    @Test
    fun hasNotArrivedBelowThreshold() {
        // Just below threshold should NOT trigger arrival
        val halfAngle = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS - 0.01
        assertFalse(ConeGeometry.hasArrived(halfAngle), "Half-angle below threshold should not indicate arrival")
    }

    @Test
    fun hasArrivedAtZeroDistance() {
        // Zero distance yields π/2 (90°), which is well above 45°
        val halfAngle = PI / 2.0
        assertTrue(ConeGeometry.hasArrived(halfAngle), "Zero distance (π/2) should indicate arrival")
    }

    @Test
    fun thresholdIs45Degrees() {
        // Verify the threshold constant is 45°
        val threshold = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_DEGREES
        assertTrue(abs(threshold - 45.0) < 0.01, "Arrival threshold should be 45 degrees")
    }
}
