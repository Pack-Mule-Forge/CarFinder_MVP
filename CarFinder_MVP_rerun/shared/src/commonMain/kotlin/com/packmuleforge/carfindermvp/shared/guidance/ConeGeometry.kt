package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import kotlin.math.cos
import kotlin.math.sin

/** A point in normalized display space: origin at the center, +y up, 1.0 = the minimum display dimension. */
data class NormalizedPoint(val x: Double, val y: Double)

/**
 * The cone, ready to draw with one scale and one translation. The person icon sits at [apex] and the car icon at
 * [carAnchor]; the sector of radius [lengthFraction] starts at [sweepStartDegrees] (clockwise from up) and spans
 * [sweepDegrees]. There is deliberately no centerline.
 *
 * @requirement FR-035, FR-036
 */
data class ConeGeometry(
    val apex: NormalizedPoint,
    val carAnchor: NormalizedPoint,
    val lengthFraction: Double,
    val sweepStartDegrees: Double,
    val sweepDegrees: Double,
    val halfAngleDegrees: Double,
    val displayBearingDegrees: Double,
)

/** @requirement FR-035, FR-036 */
object ConeGeometryCalculator {
    fun compute(displayBearingDegrees: Double, halfAngleDegrees: Double): ConeGeometry {
        val length = CarFinderConstants.CONE_LENGTH_FRACTION
        val bearing = GeoMath.toRadians(displayBearingDegrees)
        val dx = sin(bearing) * length / 2
        val dy = cos(bearing) * length / 2
        return ConeGeometry(
            apex = NormalizedPoint(-dx, -dy),
            carAnchor = NormalizedPoint(dx, dy),
            lengthFraction = length,
            sweepStartDegrees = displayBearingDegrees - halfAngleDegrees,
            sweepDegrees = 2 * halfAngleDegrees,
            halfAngleDegrees = halfAngleDegrees,
            displayBearingDegrees = displayBearingDegrees,
        )
    }
}
