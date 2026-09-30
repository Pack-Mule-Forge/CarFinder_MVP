package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation

enum class ViewKind { DRIVING, UNAVAILABLE, PARKING, GUIDANCE }

/**
 * Picks the one default view, first matching rule wins (FR-016).
 *
 * @requirement FR-016, FR-031
 */
object DefaultViewSelector {
    fun select(lifecycle: LifecycleState, guidanceAvailable: Boolean): ViewKind = when {
        lifecycle == LifecycleState.DRIVING -> ViewKind.DRIVING
        lifecycle == LifecycleState.FINDING -> ViewKind.UNAVAILABLE
        lifecycle == LifecycleState.PARKED && !guidanceAvailable -> ViewKind.UNAVAILABLE
        lifecycle == LifecycleState.PARKING -> ViewKind.PARKING
        else -> ViewKind.GUIDANCE
    }
}

/**
 * Guidance is available only when PARKED with a Parked Location, a current fix that has an accuracy radius, and a
 * heading that is present and not stale. Fix loss and heading loss are treated identically.
 *
 * @requirement FR-031, FR-034
 */
object GuidanceAvailability {
    fun isAvailable(
        lifecycle: LifecycleState,
        parked: ParkedLocation?,
        fix: LocationReading?,
        heading: HeadingReading?,
        nowElapsedMillis: Long,
    ): Boolean = lifecycle == LifecycleState.PARKED &&
        parked != null &&
        fix != null && fix.accuracyMeters != null && FixCurrency.isCurrent(fix, nowElapsedMillis) &&
        heading != null &&
        nowElapsedMillis - heading.elapsedRealtimeMillis <= CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS
}
