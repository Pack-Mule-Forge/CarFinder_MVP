package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.DenialConfirmation
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals

/** @requirement QR-001 */
class DefaultViewSelectorTest {

    private val now = 1_000_000L
    private val parked = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS, 1L)
    private val freshFix = Readings.readingOffset(-100.0, 0.0, receivedElapsedMillis = now)
    private val freshHeading = HeadingReading(12.0, now)

    private fun select(
        lifecycle: LifecycleState,
        denial: DenialConfirmation? = null,
        permissions: PermissionState = PermissionState.ALL_GRANTED,
        parkedLocation: ParkedLocation? = if (lifecycle == LifecycleState.PARKED) parked else null,
        fix: LocationReading? = freshFix,
        heading: HeadingReading? = freshHeading,
    ) = DefaultViewSelector.select(denial, permissions, lifecycle, parkedLocation, fix, heading, now)

    /** @requirement FR-042, FR-056 */
    @Test
    fun rule1_aPendingDenialShowsTheConfirmationOrTheClosingMessageInEveryState() {
        for (lifecycle in LifecycleState.entries) for (capability in PermissionState.REQUIRED) {
            for (permissions in listOf(PermissionState.ALL_GRANTED, PermissionState())) {
                assertEquals(
                    ViewKind.PermissionRequired(capability),
                    select(lifecycle, DenialConfirmation(capability, isClosing = false), permissions),
                )
                assertEquals(ViewKind.Closing, select(lifecycle, DenialConfirmation(capability, isClosing = true), permissions))
            }
        }
    }

    /** @requirement FR-042, FR-049 */
    @Test
    fun rule2_aMissingRequiredPermissionShowsUnavailableInEveryState() {
        for (lifecycle in LifecycleState.entries) for (required in PermissionState.REQUIRED) {
            for (status in listOf(PermissionStatus.NOT_REQUESTED, PermissionStatus.DENIED)) {
                val permissions = PermissionState.ALL_GRANTED.with(required, status)
                assertEquals(ViewKind.Unavailable, select(lifecycle, permissions = permissions))
            }
        }
    }

    /** @requirement FR-049 */
    @Test
    fun optionalPermissionsDoNotChangeTheView() {
        val permissions = PermissionState.ALL_GRANTED
            .with(Capability.BACKGROUND_LOCATION, PermissionStatus.DENIED)
            .with(Capability.ACTIVITY_RECOGNITION, PermissionStatus.DENIED)
        assertEquals(ViewKind.Driving, select(LifecycleState.DRIVING, permissions = permissions))
        assertEquals(ViewKind.Guidance, select(LifecycleState.PARKED, permissions = permissions))
    }

    /** @requirement FR-042 */
    @Test
    fun rules3To5_drivingFindingAndParking() {
        assertEquals(ViewKind.Driving, select(LifecycleState.DRIVING))
        assertEquals(ViewKind.Unavailable, select(LifecycleState.FINDING))
        assertEquals(ViewKind.Parking, select(LifecycleState.PARKING))
    }

    /** @requirement FR-040 */
    @Test
    fun rule6_noFixGivesUnavailable() {
        assertEquals(ViewKind.Unavailable, select(LifecycleState.PARKED, fix = null))
    }

    /** @requirement FR-040 */
    @Test
    fun rule6_aFixJustPastTheStalenessTimeoutGivesUnavailableAndOneExactlyAtItIsFresh() {
        val stale = freshFix.copy(receivedElapsedMillis = now - FIX_STALENESS_TIMEOUT_MILLIS - 1)
        val edge = freshFix.copy(receivedElapsedMillis = now - FIX_STALENESS_TIMEOUT_MILLIS)
        assertEquals(ViewKind.Unavailable, select(LifecycleState.PARKED, fix = stale))
        assertEquals(ViewKind.Guidance, select(LifecycleState.PARKED, fix = edge))
    }

    /** @requirement FR-010 */
    @Test
    fun rule6_aFixWithoutAccuracyGivesUnavailable() {
        assertEquals(ViewKind.Unavailable, select(LifecycleState.PARKED, fix = freshFix.copy(accuracyMeters = null)))
    }

    /** @requirement FR-041 */
    @Test
    fun rule6_aNullHeadingGivesUnavailable() {
        assertEquals(ViewKind.Unavailable, select(LifecycleState.PARKED, heading = null))
    }

    /** @requirement FR-041 */
    @Test
    fun rule6_aHeadingJustPastItsTimeoutGivesUnavailable() {
        val stale = freshHeading.copy(receivedElapsedMillis = now - HEADING_STALENESS_TIMEOUT_MILLIS - 1)
        val edge = freshHeading.copy(receivedElapsedMillis = now - HEADING_STALENESS_TIMEOUT_MILLIS)
        assertEquals(ViewKind.Unavailable, select(LifecycleState.PARKED, heading = stale))
        assertEquals(ViewKind.Guidance, select(LifecycleState.PARKED, heading = edge))
    }

    /** @requirement FR-042 */
    @Test
    fun rule7_parkedWithAFreshFixAndHeadingGivesGuidance() {
        assertEquals(ViewKind.Guidance, select(LifecycleState.PARKED))
    }
}
