package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @requirement QR-001
 */
class FixCurrencyTest {

    private val timeout = CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
    private val receivedAt = 1_000_000L

    /** @requirement FR-034 */
    @Test
    fun fixExactlyAtTimeout_isCurrent() {
        assertTrue(FixCurrency.isCurrent(Readings.readingAt(elapsedMillis = receivedAt), receivedAt + timeout))
    }

    /** @requirement FR-034 */
    @Test
    fun fixPastTimeout_isNotCurrent() {
        assertFalse(FixCurrency.isCurrent(Readings.readingAt(elapsedMillis = receivedAt), receivedAt + timeout + 1))
    }

    /** @requirement FR-034 */
    @Test
    fun currency_usesMonotonicTime_notEpochTime() {
        val fix = Readings.readingAt(elapsedMillis = receivedAt, epochMillis = 0L)
        assertTrue(FixCurrency.isCurrent(fix, receivedAt + 1))
    }
}
