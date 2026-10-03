package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-001 */
class SpeedFilterTest {

    private fun filterOf(vararg speeds: Double) = speeds.fold(SpeedFilter()) { filter, speed -> filter.add(speed) }

    /** @requirement FR-008 */
    @Test
    fun givenFewerSpeedsThanTheWindow_thenNoSmoothedSpeed() {
        val partial = DoubleArray(SPEED_FILTER_WINDOW_SIZE - 1) { DRIVING_MPH * 4 }
        assertNull(filterOf(*partial).smoothed)
        assertNull(SpeedFilter().smoothed)
    }

    /** @requirement FR-007 */
    @Test
    fun givenAFullWindowWithOneFarOutlier_thenSmoothedIsTheMedianNotTheMean() {
        val speeds = DoubleArray(SPEED_FILTER_WINDOW_SIZE) { PARKED_MPH }
        speeds[SPEED_FILTER_WINDOW_SIZE / 2] = DRIVING_MPH * 4
        assertEquals(PARKED_MPH, filterOf(*speeds).smoothed)
    }

    /** @requirement FR-007 */
    @Test
    fun givenAFullWindow_whenANewSpeedArrives_thenTheOldestLeaves() {
        val slow = DoubleArray(SPEED_FILTER_WINDOW_SIZE) { PARKED_MPH }
        val fast = DoubleArray(SPEED_FILTER_WINDOW_SIZE) { DRIVING_MPH }
        assertEquals(DRIVING_MPH, filterOf(*slow, *fast).smoothed)
    }

    /** @requirement FR-007 */
    @Test
    fun whenASpeedIsAdded_thenANewFilterIsReturnedAndTheReceiverIsUnchanged() {
        val original = filterOf(*DoubleArray(SPEED_FILTER_WINDOW_SIZE) { PARKED_MPH })
        val before = original.smoothed
        val added = original.add(DRIVING_MPH)
        assertEquals(before, original.smoothed)
        assertEquals(original, filterOf(*DoubleArray(SPEED_FILTER_WINDOW_SIZE) { PARKED_MPH }))
        assertTrue(added !== original)
    }
}
