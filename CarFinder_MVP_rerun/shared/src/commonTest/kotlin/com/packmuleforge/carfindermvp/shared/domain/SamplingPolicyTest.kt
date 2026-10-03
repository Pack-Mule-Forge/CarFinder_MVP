package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-001 */
class SamplingPolicyTest {

    private val booleans = listOf(false, true)

    /** The five rows of data-model.md "Sampling interval", first match. */
    private fun expected(lifecycle: LifecycleState, recoveryOpen: Boolean, visible: Boolean, inVehicle: Boolean) =
        when {
            lifecycle == DRIVING || lifecycle == PARKING -> SAMPLING_INTERVAL_PARKING_MILLIS
            lifecycle == PARKED && recoveryOpen -> SAMPLING_INTERVAL_PARKING_MILLIS
            lifecycle == PARKED && visible -> SAMPLING_INTERVAL_GUIDANCE_MILLIS
            inVehicle -> SAMPLING_INTERVAL_PARKING_MILLIS
            else -> SAMPLING_INTERVAL_IDLE_MILLIS
        }

    /** @requirement FR-027, FR-028 */
    @Test
    fun everyCombinationFollowsTheTableInOrder() {
        for (lifecycle in LifecycleState.entries) for (open in booleans) for (visible in booleans) for (inVehicle in booleans) {
            assertEquals(
                expected(lifecycle, open, visible, inVehicle),
                SamplingPolicy.intervalFor(lifecycle, open, visible, inVehicle),
                "$lifecycle open=$open visible=$visible inVehicle=$inVehicle",
            )
        }
    }

    /** @requirement FR-027 */
    @Test
    fun givenParkedWithRecoveryOpenAndGuidanceVisible_thenTheParkingInterval() {
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, SamplingPolicy.intervalFor(PARKED, true, true, false))
    }

    /** @requirement FR-027 */
    @Test
    fun givenParkedWithGuidanceVisibleAfterTheWindow_thenTheGuidanceInterval() {
        assertEquals(SAMPLING_INTERVAL_GUIDANCE_MILLIS, SamplingPolicy.intervalFor(PARKED, false, true, true))
    }

    /** @requirement FR-027, FR-028 */
    @Test
    fun givenFinding_thenIdleUnlessInAVehicle() {
        assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, SamplingPolicy.intervalFor(FINDING, false, false, false))
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, SamplingPolicy.intervalFor(FINDING, false, false, true))
    }

    /** @requirement FR-028 */
    @Test
    fun theInVehicleFlagNeverLowersTheSamplingRate() {
        for (lifecycle in LifecycleState.entries) for (open in booleans) for (visible in booleans) {
            val without = SamplingPolicy.intervalFor(lifecycle, open, visible, false)
            val with = SamplingPolicy.intervalFor(lifecycle, open, visible, true)
            assertTrue(with <= without, "$lifecycle open=$open visible=$visible")
        }
    }
}
