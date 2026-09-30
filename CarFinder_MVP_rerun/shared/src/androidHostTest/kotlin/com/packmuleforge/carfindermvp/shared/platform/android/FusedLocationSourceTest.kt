package com.packmuleforge.carfindermvp.shared.platform.android

import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeFusedClientPort : FusedClientPort {
    val requests = mutableListOf<LocationRequest>()
    val active = mutableSetOf<LocationCallback>()
    var removals = 0
    var throwOnRequest: Exception? = null

    override fun requestUpdates(request: LocationRequest, callback: LocationCallback) {
        throwOnRequest?.let { throw it }
        requests += request
        active += callback
    }

    override fun removeUpdates(callback: LocationCallback) {
        removals++
        active -= callback
    }

    fun deliver(vararg locations: Location) = active.toList().forEach { it.onLocationResult(LocationResult.create(locations.toList())) }
}

/**
 * Location mapping and the single-subscription rule, driven through a fake Fused client.
 * @requirement QR-004
 */
@RunWith(RobolectricTestRunner::class)
class FusedLocationSourceTest {

    private val port = FakeFusedClientPort()
    private val source = FusedLocationSource(port)

    private fun location(
        accuracy: Float? = 4f,
        speed: Float? = 3f,
        lat: Double = 37.42,
        lon: Double = -122.08,
    ) = Location("fused").apply {
        latitude = lat
        longitude = lon
        accuracy?.let { this.accuracy = it }
        speed?.let { this.speed = it }
        elapsedRealtimeNanos = 7_654_321_000_000L
        time = 1_790_000_000_123L
    }

    private fun map(location: Location): LocationReading? = FusedLocationSource.toReading(location)

    /** @requirement FR-006 */
    @Test
    fun locationWithoutAccuracy_mapsToNullAccuracy() {
        assertNull(map(location(accuracy = null))!!.accuracyMeters)
    }

    /** @requirement FR-006 */
    @Test
    fun locationWithoutSpeed_mapsToNullSpeed() {
        assertNull(map(location(speed = null))!!.speedMetersPerSecond)
    }

    /** @requirement FR-006 */
    @Test
    fun negativeOrNaNAccuracyOrSpeed_mapsToNull() {
        assertNull(map(location(accuracy = -1f))!!.accuracyMeters)
        assertNull(map(location(accuracy = Float.NaN))!!.accuracyMeters)
        assertNull(map(location(speed = -1f))!!.speedMetersPerSecond)
        assertNull(map(location(speed = Float.NaN))!!.speedMetersPerSecond)
    }

    /** @requirement FR-006 */
    @Test
    fun outOfRangeCoordinates_areDropped() {
        assertNull(map(location(lat = 91.0)))
        assertNull(map(location(lon = -181.0)))
    }

    /** @requirement FR-006 */
    @Test
    fun elapsedRealtimeNanos_mapsToMillis() {
        val reading = map(location())!!
        assertEquals(7_654_321L, reading.elapsedRealtimeMillis)
        assertEquals(1_790_000_000_123L, reading.epochMillis)
    }

    /** @requirement FR-006 */
    @Test
    fun setProfile_reissuesExactlyOneHighAccuracyRequest() {
        source.start()
        source.setProfile(SamplingProfile.PARKING)

        val request = port.requests.last()
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.priority)
        assertEquals(CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS, request.intervalMillis)
        assertEquals(request.intervalMillis, request.minUpdateIntervalMillis)
        // No batching: Play Services reports any requested delay at or below the interval as the interval itself,
        // so "set to 0" reads back as maxUpdateDelayMillis == intervalMillis.
        assertTrue(request.maxUpdateDelayMillis <= request.intervalMillis, "batching enabled: ${request.maxUpdateDelayMillis}")
        assertEquals(1, port.active.size)
        assertTrue(port.removals >= 1, "previous subscription must be removed before re-requesting")
    }

    /** @requirement FR-033 */
    @Test
    fun deliveredLocations_areEmittedAsReadings() = runTest {
        source.start()
        val collected = async(start = CoroutineStart.UNDISPATCHED) { source.readings.take(1).toList() }
        port.deliver(location())
        assertEquals(1, collected.await().size)
    }

    /** @requirement FR-033 */
    @Test
    fun securityException_isSwallowed_andNothingIsEmitted() {
        port.throwOnRequest = SecurityException("location permission revoked")
        source.start()
        source.setProfile(SamplingProfile.DRIVING)
        assertTrue(port.active.isEmpty())
        assertTrue(source.isPermissionDenied)
    }
}
