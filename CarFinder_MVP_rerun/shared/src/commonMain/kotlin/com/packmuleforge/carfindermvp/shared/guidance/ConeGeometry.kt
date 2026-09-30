package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A point in normalized display space: origin at screen center, +y up, 1.0 = minimum display dimension. */
data class Point(val x: Double, val y: Double)

/**
 * Everything needed to draw the cone. The UI only scales by the minimum dimension and translates to center.
 * There is deliberately no centerline (FR-023).
 *
 * @property apex person-icon anchor, the cone's point.
 * @property carAnchor car-icon anchor, at the far end.
 * @property sweepStartDegrees start of the sector, clockwise from screen-up.
 * @property sweepDegrees angular width of the sector (twice the half-angle).
 */
data class ConeGeometry(
    val apex: Point,
    val carAnchor: Point,
    val sweepStartDegrees: Double,
    val sweepDegrees: Double,
    val halfAngleDegrees: Double,
    val displayBearingDegrees: Double,
)

/**
 * Cone geometry in normalized units, identical in every orientation because it is relative to the minimum
 * display dimension and centered on the screen.
 *
 * @requirement FR-022, FR-023, FR-024
 */
object ConeGeometryCalculator {
    fun compute(displayBearingDegrees: Double, halfAngleDegrees: Double): ConeGeometry {
        val length = TuningConstants.CONE_LENGTH_FRACTION
        val radians = displayBearingDegrees * PI / 180.0
        val dx = sin(radians)
        val dy = cos(radians)
        return ConeGeometry(
            apex = Point(-length / 2 * dx, -length / 2 * dy),
            carAnchor = Point(length / 2 * dx, length / 2 * dy),
            sweepStartDegrees = displayBearingDegrees - halfAngleDegrees,
            sweepDegrees = 2 * halfAngleDegrees,
            halfAngleDegrees = halfAngleDegrees,
            displayBearingDegrees = displayBearingDegrees,
        )
    }
}
