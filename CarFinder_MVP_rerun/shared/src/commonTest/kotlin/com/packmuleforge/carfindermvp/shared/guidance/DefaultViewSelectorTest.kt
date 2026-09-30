package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FR-016's view priority order, and FR-031's definition of "guidance available".
 * @requirement QR-001
 */
class DefaultViewSelectorTest {

    private val now = 1_000_000L
    private val parked = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)
    private val fix = Readings.readingOffset(northMeters = 40.0, eastMeters = 0.0, elapsedMillis = now)
    private val heading = HeadingReading(0.0, now)

    private fun available(
        lifecycle: LifecycleState = LifecycleState.PARKED,
        location: ParkedLocation? = parked,
        latestFix: com.packmuleforge.carfindermvp.shared.domain.LocationReading? = fix,
        latestHeading: HeadingReading? = heading,
    ) = GuidanceAvailability.isAvailable(lifecycle, location, latestFix, latestHeading, now)

    /** @requirement FR-016 */
    @Test
    fun priorityOrder_acrossAllStatesAndAvailability() {
        for (guidance in listOf(true, false)) {
            assertEquals(ViewKind.DRIVING, DefaultViewSelector.select(LifecycleState.DRIVING, guidance))
            assertEquals(ViewKind.UNAVAILABLE, DefaultViewSelector.select(LifecycleState.FINDING, guidance))
            assertEquals(ViewKind.PARKING, DefaultViewSelector.select(LifecycleState.PARKING, guidance))
        }
        assertEquals(ViewKind.GUIDANCE, DefaultViewSelector.select(LifecycleState.PARKED, guidanceAvailable = true))
        assertEquals(ViewKind.UNAVAILABLE, DefaultViewSelector.select(LifecycleState.PARKED, guidanceAvailable = false))
    }

    /** @requirement FR-031 */
    @Test
    fun guidanceAvailable_whenParkedWithCurrentFixAccuracyAndHeading() {
        assertTrue(available())
    }

    /** @requirement FR-031, FR-034 */
    @Test
    fun staleFix_makesGuidanceUnavailable() {
        val stale = fix.copy(elapsedRealtimeMillis = now - CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS - 1)
        assertFalse(available(latestFix = stale))
    }

    /** @requirement FR-031 */
    @Test
    fun fixWithoutAccuracy_makesGuidanceUnavailable() {
        assertFalse(available(latestFix = fix.copy(accuracyMeters = null)))
    }

    /** @requirement FR-031 */
    @Test
    fun missingOrStaleHeading_makesGuidanceUnavailable() {
        assertFalse(available(latestHeading = null))
        val staleHeading = heading.copy(elapsedRealtimeMillis = now - CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS - 1)
        assertFalse(available(latestHeading = staleHeading))
    }

    /** @requirement FR-031 */
    @Test
    fun noFix_orNoParkedLocation_orNotParked_makesGuidanceUnavailable() {
        assertFalse(available(latestFix = null))
        assertFalse(available(location = null))
        for (state in LifecycleState.entries.filter { it != LifecycleState.PARKED }) {
            assertFalse(available(lifecycle = state))
        }
    }
}
