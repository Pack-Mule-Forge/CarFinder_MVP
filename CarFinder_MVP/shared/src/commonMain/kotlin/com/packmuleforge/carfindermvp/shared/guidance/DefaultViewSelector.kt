package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.DenialConfirmation
import com.packmuleforge.carfindermvp.shared.platform.PermissionState

/** Which view FR-042 selects. Guidance is split into guidance and arrival by the presenter. */
sealed interface ViewKind {
    data class PermissionRequired(val capability: Capability) : ViewKind
    data object Closing : ViewKind
    data object Unavailable : ViewKind
    data object Driving : ViewKind
    data object Parking : ViewKind
    data object Guidance : ViewKind
}

/**
 * FR-042's first-match rules. Every combination of inputs falls under exactly one rule.
 *
 * @requirement FR-040, FR-041, FR-042, FR-049, FR-056
 */
object DefaultViewSelector {
    fun select(
        denial: DenialConfirmation?,
        permissions: PermissionState,
        lifecycle: LifecycleState,
        parked: ParkedLocation?,
        fix: LocationReading?,
        heading: HeadingReading?,
        nowElapsedMillis: Long,
    ): ViewKind = when {
        denial != null -> if (denial.isClosing) ViewKind.Closing else ViewKind.PermissionRequired(denial.capability)
        !permissions.areRequiredGranted -> ViewKind.Unavailable
        lifecycle == LifecycleState.DRIVING -> ViewKind.Driving
        lifecycle == LifecycleState.FINDING -> ViewKind.Unavailable
        lifecycle == LifecycleState.PARKING -> ViewKind.Parking
        parked == null || !isFresh(fix, nowElapsedMillis) || !isFresh(heading, nowElapsedMillis) -> ViewKind.Unavailable
        else -> ViewKind.Guidance
    }

    /** A fix is usable when it has an accuracy value and is not older than the fix-staleness timeout (FR-040). */
    private fun isFresh(fix: LocationReading?, now: Long): Boolean =
        fix != null && fix.accuracyMeters != null &&
            now - fix.receivedElapsedMillis <= CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS

    /** A heading is usable when present and not older than the heading-staleness timeout (FR-041). */
    private fun isFresh(heading: HeadingReading?, now: Long): Boolean =
        heading != null && now - heading.receivedElapsedMillis <= CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS
}
