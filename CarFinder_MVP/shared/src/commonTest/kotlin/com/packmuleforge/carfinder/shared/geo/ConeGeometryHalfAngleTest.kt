package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-023", "FR-031", "FR-035", "FR-037")
class ConeGeometryHalfAngleTest {
    @Test
    fun coneHalfAngleEqualsAtanOfUncertaintyOverDistance() {
        val uncertainty = 20.0  // meters
        val distance = 100.0    // meters

        val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
        val expected = atan(uncertainty / distance)

        assertTrue(abs(halfAngle - expected) < 0.0001, "Half-angle should equal atan(uncertainty/distance)")
    }

    @Test
    fun zeroDistanceReturnsNinetyDegrees() {
        val uncertainty = 10.0
        val distance = 0.0

        val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
        val expected = PI / 2.0  // 90 degrees in radians

        assertTrue(abs(halfAngle - expected) < 0.0001, "Zero distance should return π/2 radians (90°)")
    }

    @Test
    fun uncertaintyExceedingDistanceYieldsGreaterThan45Degrees() {
        val uncertainty = 100.0
        val distance = 50.0

        val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
        val threshold = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS

        assertTrue(halfAngle > threshold, "When uncertainty > distance, half-angle should exceed arrival threshold (45°)")
    }

    @Test
    fun smallUncertaintyYieldsSmallCone() {
        val uncertainty = 5.0
        val distance = 1000.0

        val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
        val threshold = ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS

        assertTrue(halfAngle < threshold, "Small uncertainty should yield half-angle below 45°")
    }

    @Test
    fun certaintyEqualToDistanceYieldsExactly45Degrees() {
        val uncertainty = 100.0
        val distance = 100.0

        val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
        val expected = PI / 4.0  // 45 degrees

        assertTrue(abs(halfAngle - expected) < 0.0001, "Equal uncertainty and distance should yield π/4 (45°)")
    }
}
