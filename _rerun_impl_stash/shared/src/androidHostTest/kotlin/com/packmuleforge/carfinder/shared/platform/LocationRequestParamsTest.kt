package com.packmuleforge.carfinder.shared.platform

import com.google.android.gms.location.Priority
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure JVM test for the tier-to-request-parameters mapping extracted from
 * LocationProvider.android.kt (T029). No Context, no Robolectric, no MockK.
 */
@Requirement("FR-005", "FR-037")
class LocationRequestParamsTest {

    private val drivingMinIntervalMillis = 15_000L
    private val drivingMaxIntervalMillis = 30_000L
    private val parkedMinIntervalMillis = 30_000L
    private val parkedMaxIntervalMillis = 60_000L

    @Test
    fun drivingTierIsBalancedAccuracyWithinFifteenToThirtySeconds() {
        val params = LocationRequestTier.DRIVING.toRequestParams()
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, params.priority)
        assertTrue(
            params.intervalMillis in drivingMinIntervalMillis..drivingMaxIntervalMillis,
            "DRIVING interval ${params.intervalMillis}ms should be within " +
                "$drivingMinIntervalMillis..$drivingMaxIntervalMillis"
        )
    }

    @Test
    fun parkingTierIsHighAccuracyAtParkingSampleInterval() {
        val params = LocationRequestTier.PARKING.toRequestParams()
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, params.priority)
        assertEquals(ParkingConstants.PARKING_SAMPLE_INTERVAL_MILLIS, params.intervalMillis)
    }

    @Test
    fun parkedTierIsBalancedAccuracyWithinThirtyToSixtySeconds() {
        val params = LocationRequestTier.PARKED.toRequestParams()
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, params.priority)
        assertTrue(
            params.intervalMillis in parkedMinIntervalMillis..parkedMaxIntervalMillis,
            "PARKED interval ${params.intervalMillis}ms should be within " +
                "$parkedMinIntervalMillis..$parkedMaxIntervalMillis"
        )
    }
}
