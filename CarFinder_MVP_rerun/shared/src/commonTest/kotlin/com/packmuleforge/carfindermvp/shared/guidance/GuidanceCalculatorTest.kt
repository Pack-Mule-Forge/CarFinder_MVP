package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.ARRIVAL_CONE_HALF_ANGLE_DEGREES
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FEET_PER_METER
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FEET_PER_MILE
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.math.PI
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class GuidanceCalculatorTest {

    /** @requirement FR-030 */
    @Test
    fun uncertaintyIsTheSumOfTheStoredAndLiveRadii() {
        val parked = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, 7.5, 1L)
        val fix = Readings.readingOffset(100.0, 0.0, accuracyMeters = 3.25)
        assertEquals(7.5 + 3.25, GuidanceCalculator.uncertaintyMeters(parked, fix))
    }

    /** @requirement FR-031 */
    @Test
    fun theHalfAngleIsTheArctangentOfUncertaintyOverDistance() {
        for ((u, d) in listOf(1.0 to 100.0, 12.0 to 30.0, 20.0 to 20.0, 50.0 to 3.0)) {
            assertEquals(atan(u / d) * 180 / PI, GuidanceCalculator.coneHalfAngleDegrees(u, d), 1e-9)
        }
    }

    /** @requirement FR-031 */
    @Test
    fun aDistanceOfZeroGivesNinetyDegrees() {
        assertEquals(90.0, GuidanceCalculator.coneHalfAngleDegrees(4.0, 0.0), 1e-9)
    }

    /** @requirement FR-032 */
    @Test
    fun theDisplayBearingFollowsTheFormulaAndWrapsOnBothSides() {
        assertEquals(26.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 350.0, bearingToCar = 16.0), 1e-9)
        assertEquals(335.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 15.0, bearingToCar = 350.0), 1e-9)
        assertEquals(0.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 0.0, bearingToCar = 0.0), 1e-9)
        assertEquals(0.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 123.0, bearingToCar = 123.0), 1e-9)
        assertEquals(270.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 90.0, bearingToCar = 0.0), 1e-9)
    }

    /** @requirement FR-037 */
    @Test
    fun atTheThresholdTheDistanceIsWholeFeet() {
        val meters = DISTANCE_UNIT_THRESHOLD_FEET / FEET_PER_METER
        assertEquals("${DISTANCE_UNIT_THRESHOLD_FEET.toLong()} ft", GuidanceCalculator.distanceText(meters))
        assertEquals("37 ft", GuidanceCalculator.distanceText(37.2 / FEET_PER_METER))
    }

    /** @requirement FR-037 */
    @Test
    fun justAboveTheThresholdTheDistanceIsMilesToTwoDecimals() {
        val meters = (DISTANCE_UNIT_THRESHOLD_FEET + 1) / FEET_PER_METER
        assertTrue(Regex("""^\d+\.\d{2} mi$""").matches(GuidanceCalculator.distanceText(meters)))
        assertEquals("2.00 mi", GuidanceCalculator.distanceText(2 * FEET_PER_MILE / FEET_PER_METER))
        assertEquals("1.25 mi", GuidanceCalculator.distanceText(1.25 * FEET_PER_MILE / FEET_PER_METER))
    }

    /** @requirement FR-038 */
    @Test
    fun arrivalIsFalseJustBelowTheArrivalHalfAngleAndTrueAtIt() {
        assertFalse(GuidanceCalculator.isArrived(ARRIVAL_CONE_HALF_ANGLE_DEGREES - 0.001))
        assertTrue(GuidanceCalculator.isArrived(ARRIVAL_CONE_HALF_ANGLE_DEGREES))
        assertTrue(GuidanceCalculator.isArrived(ARRIVAL_CONE_HALF_ANGLE_DEGREES + 0.001))
    }

    /** @requirement FR-030, FR-031, FR-032, FR-038 */
    @Test
    fun computeCombinesTheParts() {
        val parked = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, 4.0, 1L)
        val fix = Readings.readingOffset(-200.0, 0.0, accuracyMeters = 6.0)
        val state = GuidanceCalculator.compute(parked, fix, HeadingReading(90.0, 0L))
        assertEquals(4.0 + 6.0, state.uncertaintyMeters)
        assertEquals(200.0, state.distanceMeters, 0.01)
        assertEquals(0.0, state.bearingToCarDegrees, 1e-6)
        assertEquals(270.0, state.displayBearingDegrees, 1e-6)
        assertEquals(GuidanceCalculator.coneHalfAngleDegrees(state.uncertaintyMeters, state.distanceMeters), state.coneHalfAngleDegrees)
        assertFalse(state.isArrived)
        assertEquals(state.displayBearingDegrees, state.cone.displayBearingDegrees)
    }
}
