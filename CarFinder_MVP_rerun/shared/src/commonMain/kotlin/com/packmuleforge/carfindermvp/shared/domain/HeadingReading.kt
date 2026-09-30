package com.packmuleforge.carfindermvp.shared.domain

/**
 * A compass heading: true north, remapped so it is the direction the top of the screen points.
 *
 * @property trueHeadingDegrees in [0, 360).
 */
data class HeadingReading(
    val trueHeadingDegrees: Double,
    val elapsedRealtimeMillis: Long,
) {
    init {
        require(trueHeadingDegrees >= 0.0 && trueHeadingDegrees < 360.0) {
            "trueHeadingDegrees must be in [0, 360), was $trueHeadingDegrees"
        }
    }
}
