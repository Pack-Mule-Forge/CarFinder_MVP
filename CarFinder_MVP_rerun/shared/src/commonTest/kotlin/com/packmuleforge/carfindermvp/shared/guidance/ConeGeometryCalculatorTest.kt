package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Normalized cone geometry: origin at screen center, +y up, 1.0 = minimum display dimension.
 * @requirement QR-001
 */
class ConeGeometryCalculatorTest {

    private val length = TuningConstants.CONE_LENGTH_FRACTION
    private val tolerance = 1e-9

    private fun unit(bearingDegrees: Double) =
        Point(sin(bearingDegrees * PI / 180), cos(bearingDegrees * PI / 180))

    /** @requirement FR-022 */
    @Test
    fun apexAndCarAnchor_areHalfAConeLengthEitherSideOfCenter_alongBearing() {
        for (bearing in listOf(0.0, 37.0, 90.0, 180.0, 271.0)) {
            val cone = ConeGeometryCalculator.compute(displayBearingDegrees = bearing, halfAngleDegrees = 12.0)
            val d = unit(bearing)
            assertEquals(-length / 2 * d.x, cone.apex.x, tolerance)
            assertEquals(-length / 2 * d.y, cone.apex.y, tolerance)
            assertEquals(length / 2 * d.x, cone.carAnchor.x, tolerance)
            assertEquals(length / 2 * d.y, cone.carAnchor.y, tolerance)
        }
    }

    /** @requirement FR-020, FR-021 */
    @Test
    fun sweep_startsAtBearingMinusHalfAngle_andSpansTwiceHalfAngle() {
        val cone = ConeGeometryCalculator.compute(displayBearingDegrees = 100.0, halfAngleDegrees = 15.0)
        assertEquals(85.0, cone.sweepStartDegrees, tolerance)
        assertEquals(30.0, cone.sweepDegrees, tolerance)
        assertEquals(15.0, cone.halfAngleDegrees)
        assertEquals(100.0, cone.displayBearingDegrees)
    }

    /** @requirement FR-024 */
    @Test
    fun wholeSector_staysInsideTheMinimumDimensionSquare_upToArrivalAngle() {
        var half = 0.0
        while (half <= CarFinderConstants.ARRIVAL_HALF_ANGLE_DEGREES) {
            for (bearing in 0 until 360 step 5) {
                val cone = ConeGeometryCalculator.compute(bearing.toDouble(), half)
                val points = listOf(cone.apex, cone.carAnchor) + (0..20).map { i ->
                    val edge = unit(cone.sweepStartDegrees + cone.sweepDegrees * i / 20)
                    Point(cone.apex.x + length * edge.x, cone.apex.y + length * edge.y)
                }
                points.forEach { p ->
                    assertTrue(hypot(p.x, p.y) <= 0.5 + tolerance, "point $p escapes at bearing $bearing, half $half")
                }
            }
            half += 2.5
        }
    }

    /** @requirement FR-023 */
    @Test
    fun geometry_hasNoCenterline() {
        val cone = ConeGeometryCalculator.compute(displayBearingDegrees = 0.0, halfAngleDegrees = 12.0)
        // The documented fields are the only ones: two anchors, a sweep and the echoed inputs, with no line.
        assertEquals(
            ConeGeometry(cone.apex, cone.carAnchor, cone.sweepStartDegrees, cone.sweepDegrees, 12.0, 0.0),
            cone,
        )
        assertTrue("centerline" !in cone.toString().lowercase())
    }
}
