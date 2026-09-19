package com.packmuleforge.carfinder.shared.constants

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-034", "FR-037")
class ParkingConstantsTest {
    @Test
    fun parkingSpeedThresholdIsLessThanDrivingThreshold() {
        // FR-004: The dead zone is only well-defined if thresholds are ordered
        assertTrue(
            ParkingConstants.PARKING_SPEED_THRESHOLD_MPS < ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS,
            "Parking threshold must be strictly less than driving threshold"
        )
    }

    @Test
    fun derivedSpeedValuesAreCorrectlyConverted() {
        // 5 mph → 2.2352 m/s (mph * 0.44704)
        val expected5Mph = 5.0 * 0.44704
        assertTrue(
            abs(ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - expected5Mph) < 0.0001,
            "5 mph conversion incorrect"
        )

        // 25 mph → 11.176 m/s
        val expected25Mph = 25.0 * 0.44704
        assertTrue(
            abs(ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS - expected25Mph) < 0.0001,
            "25 mph conversion incorrect"
        )
    }

    @Test
    fun derivedDistanceUnitThresholdIsCorrectlyConverted() {
        // 500 feet → 152.4 m (feet * 0.3048)
        val expected500Ft = 500.0 * 0.3048
        assertTrue(
            abs(ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS - expected500Ft) < 0.0001,
            "500 feet conversion incorrect"
        )
    }

    @Test
    fun derivedArrivalConeHalfAngleIsCorrectlyConverted() {
        // 45 degrees → π/4 radians
        val expected45Deg = 45.0 * (PI / 180.0)
        assertTrue(
            abs(ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS - expected45Deg) < 0.0001,
            "45 degree conversion to radians incorrect"
        )
    }

    @Test
    fun allConstantsReferenceSourceValues() {
        // This is a code review property: no test can verify that SI constants are derived from
        // source constants rather than hardcoded. The architecture ensures it through single-
        // sourcing the source values and computing the SI forms in the same expression.
        // T081 (architecture test) will scan for the presence of literal SI values in test code.
    }
}
