package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * Rolling median over the most recent speed samples.
 * @requirement QR-001
 */
class SpeedMedianFilterTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val slow = Readings.PARKED_MPH
    private val fast = Readings.DRIVING_MPH

    private fun SpeedMedianFilter.addAll(vararg speeds: Double) = speeds.fold(this) { f, s -> f.add(s) }

    /** @requirement FR-032 */
    @Test
    fun filteredIsNull_untilWindowIsFull() {
        var filter = SpeedMedianFilter.empty()
        repeat(window - 1) {
            filter = filter.add(fast)
            assertNull(filter.filtered)
        }
        assertEquals(fast, filter.add(fast).filtered)
    }

    /** @requirement FR-032 */
    @Test
    fun singleSpikeAmongSlowSamples_isAbsorbed() {
        val speeds = DoubleArray(window) { slow }.also { it[window / 2] = fast }
        assertEquals(slow, SpeedMedianFilter.empty().addAll(*speeds).filtered)
    }

    /** @requirement FR-032 */
    @Test
    fun window_dropsOldestSampleFirst() {
        val full = SpeedMedianFilter.empty().addAll(*DoubleArray(window) { slow })
        assertEquals(slow, full.filtered)
        // Pushing a full window of fast samples must evict every slow one.
        assertEquals(fast, full.addAll(*DoubleArray(window) { fast }).filtered)
    }

    /** @requirement FR-032 */
    @Test
    fun evenWindowMedian_isMeanOfMiddleTwo() {
        val filter = SpeedMedianFilter.empty(windowSize = 4).addAll(1.0, 2.0, 3.0, 8.0)
        assertEquals(2.5, filter.filtered)
    }

    /** @requirement FR-032 */
    @Test
    fun filterIsAMedian_notAConsecutiveCount() {
        // fast, slow, fast: a "N consecutive" rule would not fire, but the median is fast.
        val speeds = DoubleArray(window) { if (it % 2 == 0) fast else slow }
        assertEquals(fast, SpeedMedianFilter.empty().addAll(*speeds).filtered)
    }

    /** @requirement FR-032 */
    @Test
    fun add_returnsNewFilter_andLeavesReceiverUnchanged() {
        val before = SpeedMedianFilter.empty().addAll(*DoubleArray(window) { slow })
        val after = before.add(fast)
        assertNotSame(before, after)
        assertEquals(slow, before.filtered)
        assertEquals(SpeedMedianFilter.empty().addAll(*DoubleArray(window) { slow }), before)
    }
}
