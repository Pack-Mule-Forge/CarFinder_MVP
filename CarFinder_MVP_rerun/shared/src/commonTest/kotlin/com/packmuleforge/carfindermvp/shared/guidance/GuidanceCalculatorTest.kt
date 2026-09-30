package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Uncertainty, cone half-angle, display bearing and distance text.
 * @requirement QR-001
 */
class GuidanceCalculatorTest {

    private val threshold = CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET
    private fun feetToMeters(feet: Double) = feet / CarFinderConstants.METERS_TO_FEET
    private fun milesToMeters(miles: Double) = feetToMeters(miles * CarFinderConstants.FEET_PER_MILE)

    private val parked = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, accuracyMeters = 7.5, capturedAtEpochMillis = 0L)

    /** @requirement FR-019 */
    @Test
    fun uncertainty_isParkedAccuracyPlusFixAccuracy() {
        val fix = Readings.readingAt(accuracy = 3.25)
        assertEquals(parked.accuracyMeters + 3.25, GuidanceCalculator.uncertaintyMeters(parked, fix))
    }

    /** @requirement FR-020 */
    @Test
    fun halfAngle_isAtan2OfUncertaintyOverDistance_inDegrees() {
        val u = 12.0
        val d = 70.0
        assertEquals(atan2(u, d) * 180 / PI, GuidanceCalculator.coneHalfAngleDegrees(u, d), 1e-12)
    }

    /** @requirement FR-020 */
    @Test
    fun halfAngle_atZeroDistance_isNinetyDegrees() {
        assertEquals(90.0, GuidanceCalculator.coneHalfAngleDegrees(uncertaintyMeters = 3.0, distanceMeters = 0.0))
    }

    /** @requirement FR-021 */
    @Test
    fun displayBearing_wrapsAround() {
        assertEquals(21.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 350.0, bearingToCar = 11.0), 1e-9)
        assertEquals(339.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 11.0, bearingToCar = 350.0), 1e-9)
        assertEquals(0.0, GuidanceCalculator.displayBearingDegrees(deviceHeading = 90.0, bearingToCar = 90.0), 1e-9)
        for (heading in 0 until 360 step 15) for (bearing in 0 until 360 step 15) {
            val result = GuidanceCalculator.displayBearingDegrees(heading.toDouble(), bearing.toDouble())
            assertTrue(result >= 0.0 && result < 360.0, "display bearing $result out of range")
        }
    }

    /** @requirement FR-026 */
    @Test
    fun unitSelection_feetAtOrBelowThreshold_milesAbove() {
        assertEquals(DistanceUnit.FEET, GuidanceCalculator.distanceDisplay(feetToMeters(threshold - 1)).unit)
        assertEquals(DistanceUnit.FEET, GuidanceCalculator.distanceDisplay(feetToMeters(threshold)).unit)
        assertEquals(DistanceUnit.MILES, GuidanceCalculator.distanceDisplay(feetToMeters(threshold + 1)).unit)
    }

    /** @requirement FR-025, FR-026 */
    @Test
    fun feetText_isWholeFeet() {
        val feet = (threshold - 1).toInt()
        assertEquals("$feet ft", GuidanceCalculator.distanceDisplay(feetToMeters(feet.toDouble())).text)
    }

    /** @requirement FR-025, FR-026 */
    @Test
    fun milesText_hasTwoDecimals_roundedHalfUp() {
        assertEquals("0.37 mi", GuidanceCalculator.distanceDisplay(milesToMeters(0.37)).text)
        assertEquals("1.25 mi", GuidanceCalculator.distanceDisplay(milesToMeters(1.2451)).text)
        assertEquals("12.00 mi", GuidanceCalculator.distanceDisplay(milesToMeters(11.999)).text)
    }

    /** @requirement FR-019, FR-020, FR-021, FR-025 */
    @Test
    fun compute_combinesDistanceBearingUncertaintyAndHeading() {
        val north = 60.0
        val fix = Readings.readingOffset(northMeters = -north, eastMeters = 0.0, accuracy = 4.0)
        val heading = HeadingReading(trueHeadingDegrees = 90.0, elapsedRealtimeMillis = 0L)

        val g = GuidanceCalculator.compute(parked, fix, heading)

        assertEquals(north, g.distanceMeters, 0.5)
        assertEquals(0.0, g.bearingToCarDegrees.let { if (it > 180) it - 360 else it }, 0.01)
        assertEquals(270.0, g.displayBearingDegrees, 0.01)
        assertEquals(parked.accuracyMeters + 4.0, g.uncertaintyMeters)
        assertEquals(atan2(g.uncertaintyMeters, g.distanceMeters) * 180 / PI, g.coneHalfAngleDegrees, 1e-9)
        assertEquals(DistanceUnit.FEET, g.distanceDisplay.unit)
    }
}
