package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.ARRIVAL_CONE_HALF_ANGLE_DEGREES
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONE_LENGTH_FRACTION
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ConeGeometryCalculatorTest {

    private val bearings = listOf(0.0, 37.0, 90.0, 181.0, 270.0, 359.0)

    private fun rad(degrees: Double) = degrees * PI / 180

    /** @requirement FR-035, FR-036 */
    @Test
    fun theAnchorsAreHalfTheConeLengthEitherSideOfTheCenterAlongTheBearing() {
        for (bearing in bearings) {
            val cone = ConeGeometryCalculator.compute(bearing, 12.0)
            val half = CONE_LENGTH_FRACTION / 2
            assertEquals(-half * sin(rad(bearing)), cone.apex.x, 1e-12)
            assertEquals(-half * cos(rad(bearing)), cone.apex.y, 1e-12)
            assertEquals(half * sin(rad(bearing)), cone.carAnchor.x, 1e-12)
            assertEquals(half * cos(rad(bearing)), cone.carAnchor.y, 1e-12)
            assertEquals(CONE_LENGTH_FRACTION, hypot(cone.carAnchor.x - cone.apex.x, cone.carAnchor.y - cone.apex.y), 1e-12)
            assertEquals(CONE_LENGTH_FRACTION, cone.lengthFraction)
        }
    }

    /** @requirement FR-031, FR-036 */
    @Test
    fun theSweepStartsAtBearingMinusHalfAngleAndSpansTwiceIt() {
        val cone = ConeGeometryCalculator.compute(100.0, 13.5)
        assertEquals(86.5, cone.sweepStartDegrees, 1e-12)
        assertEquals(27.0, cone.sweepDegrees, 1e-12)
        assertEquals(13.5, cone.halfAngleDegrees)
        assertEquals(100.0, cone.displayBearingDegrees)
    }

    /** @requirement FR-036 */
    @Test
    fun everyPointOfTheSectorStaysWithinHalfOfTheMinimumDimension() {
        var half = 0.0
        while (half < ARRIVAL_CONE_HALF_ANGLE_DEGREES) {
            for (bearing in bearings) {
                val cone = ConeGeometryCalculator.compute(bearing, half)
                for (step in 0..20) {
                    val angle = rad(cone.sweepStartDegrees + cone.sweepDegrees * step / 20)
                    val x = cone.apex.x + cone.lengthFraction * sin(angle)
                    val y = cone.apex.y + cone.lengthFraction * cos(angle)
                    assertTrue(abs(x) <= 0.5 && abs(y) <= 0.5, "($x, $y) at bearing $bearing half $half")
                }
            }
            half += 0.5
        }
    }

    /** @requirement FR-035 */
    @Test
    fun theGeometryHasNoCenterline() {
        // A data class's toString lists every property, so no property name mentions a centerline.
        val description = ConeGeometryCalculator.compute(0.0, 1.0).toString()
        assertTrue(description.startsWith("ConeGeometry("))
        assertTrue(!description.contains("centerline", ignoreCase = true), description)
    }
}
