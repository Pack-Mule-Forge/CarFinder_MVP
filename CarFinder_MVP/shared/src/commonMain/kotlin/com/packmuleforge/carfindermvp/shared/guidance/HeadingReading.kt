package com.packmuleforge.carfindermvp.shared.guidance

/**
 * The phone's heading: smoothed, remapped for display rotation and corrected to true north.
 *
 * @requirement FR-033
 */
data class HeadingReading(
    val trueHeadingDegrees: Double,
    val receivedElapsedMillis: Long,
) {
    init {
        require(trueHeadingDegrees >= 0 && trueHeadingDegrees < FULL_TURN_DEGREES) { "heading must be in [0, 360)" }
    }

    private companion object {
        val FULL_TURN_DEGREES = 360.0
    }
}
