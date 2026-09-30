package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading

/**
 * A live fix is current when it was received no more than the fix-staleness timeout ago, on the monotonic clock.
 *
 * @requirement FR-034
 */
object FixCurrency {
    fun isCurrent(fix: LocationReading, nowElapsedMillis: Long): Boolean =
        nowElapsedMillis - fix.elapsedRealtimeMillis <= CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
}
