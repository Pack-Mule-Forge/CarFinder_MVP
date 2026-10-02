package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Arrival fires when the cone can no longer honestly point anywhere: half-angle at or above the arrival angle.
 * @requirement QR-001
 */
class ArrivalTest {

    private val arrival = CarFinderConstants.ARRIVAL_HALF_ANGLE_DEGREES

    /** @requirement FR-028 */
    @Test
    fun arrived_atExactlyTheArrivalAngle_notJustBelow() {
        assertTrue(GuidanceCalculator.isArrived(arrival))
        assertFalse(GuidanceCalculator.isArrived(arrival - 1e-6))
    }

    /** @requirement FR-028 */
    @Test
    fun arrived_iffUncertaintyAtLeastDistance() {
        val pairs = listOf(3.0 to 1.0, 12.0 to 12.0, 7.0 to 6.9, 1.0 to 3.0, 6.9 to 7.0, 20.0 to 400.0)
        for ((uncertainty, distance) in pairs) {
            val half = GuidanceCalculator.coneHalfAngleDegrees(uncertainty, distance)
            assertEquals(uncertainty >= distance, GuidanceCalculator.isArrived(half), "u=$uncertainty d=$distance")
        }
    }

    /** @requirement FR-028 */
    @Test
    fun arrived_atZeroDistance() {
        assertTrue(GuidanceCalculator.isArrived(GuidanceCalculator.coneHalfAngleDegrees(4.0, 0.0)))
    }
}
