package com.packmuleforge.carfindermvp.shared.platform.android

import android.location.Location
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.testing.FakeMonotonicClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.RecordingDiagnosticLog
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
class FusedLocationSourceTest {

    private class FakeFusedClientPort(var hasPermission: Boolean = true) : FusedClientPort {
        val requests = mutableListOf<FusedRequest>()
        var activeSubscriptions = 0
        var maxActiveSubscriptions = 0
        private var callback: ((Location) -> Unit)? = null

        override fun hasFineLocationPermission() = hasPermission

        override fun requestLocationUpdates(request: FusedRequest, onLocation: (Location) -> Unit) {
            requests += request
            activeSubscriptions++
            maxActiveSubscriptions = maxOf(maxActiveSubscriptions, activeSubscriptions)
            callback = onLocation
        }

        override fun removeLocationUpdates() {
            if (activeSubscriptions > 0) activeSubscriptions--
            callback = null
        }

        fun deliver(location: Location) = checkNotNull(callback) { "not subscribed" }(location)
    }

    private val port = FakeFusedClientPort()
    private val clock = FakeMonotonicClock(startMillis = 123_456L)
    private val log = RecordingDiagnosticLog()
    private val source = FusedLocationSource(port, clock, log)

    private fun location(configure: Location.() -> Unit = {}) = Location("fused").apply {
        latitude = Readings.BASE_LATITUDE
        longitude = Readings.BASE_LONGITUDE
        configure()
    }

    private fun collectWhile(block: () -> Unit): List<LocationReading> {
        val received = mutableListOf<LocationReading>()
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.readings.collect { received += it } }
            block()
        }
        return received
    }

    /** @requirement FR-010 */
    @Test
    fun givenAFixWithNoAccuracy_thenAccuracyIsNullEvenThoughThePlatformReadsZero() {
        val fix = location()
        assertEquals(0f, fix.accuracy)
        val received = collectWhile { source.start(); port.deliver(fix) }
        assertNull(received.single().accuracyMeters)
    }

    /** @requirement FR-009 */
    @Test
    fun givenAFixWithNoSpeed_thenSpeedIsNullEvenThoughThePlatformReadsZero() {
        val fix = location()
        assertEquals(0f, fix.speed)
        val received = collectWhile { source.start(); port.deliver(fix) }
        assertNull(received.single().speedMetersPerSecond)
    }

    /** @requirement FR-009, FR-010 */
    @Test
    fun givenNonFiniteOrNegativeValues_thenTheyMapToNull() {
        val fixes = listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).map { bad -> location { accuracy = bad; speed = bad } }
        val received = collectWhile { source.start(); fixes.forEach(port::deliver) }
        assertEquals(3, received.size)
        assertTrue(received.all { it.accuracyMeters == null && it.speedMetersPerSecond == null })
    }

    /** @requirement FR-009, FR-010 */
    @Test
    fun givenPresentValues_thenTheyAreMapped() {
        val received = collectWhile {
            source.start()
            port.deliver(location { accuracy = 4f; speed = 0f })
        }
        assertEquals(4.0, received.single().accuracyMeters)
        assertEquals(0.0, received.single().speedMetersPerSecond)
    }

    /** @requirement FR-010 */
    @Test
    fun givenOutOfRangeCoordinates_thenTheReadingIsDroppedAndLogged() {
        val received = collectWhile {
            source.start()
            port.deliver(location { latitude = 91.0 })
            port.deliver(location { longitude = -181.0 })
            port.deliver(location { latitude = Double.NaN })
        }
        assertTrue(received.isEmpty())
        assertEquals(3, log.events.count { it == DiagnosticEvent.ReadingDropped })
    }

    /** @requirement FR-027, FR-029 */
    @Test
    fun setIntervalReissuesOneHighAccuracyRequestWithNoBatching() {
        source.start()
        source.setIntervalMillis(SAMPLING_INTERVAL_PARKING_MILLIS)
        source.setIntervalMillis(SAMPLING_INTERVAL_GUIDANCE_MILLIS)
        val last = port.requests.last()
        assertEquals(SAMPLING_INTERVAL_GUIDANCE_MILLIS, last.intervalMillis)
        assertEquals(SAMPLING_INTERVAL_GUIDANCE_MILLIS, last.minUpdateIntervalMillis)
        assertEquals(0L, last.maxUpdateDelayMillis)
        assertTrue(port.requests.all { it.isHighAccuracy })
        assertEquals(1, port.activeSubscriptions)
        assertEquals(1, port.maxActiveSubscriptions)
    }

    /** @requirement FR-027 */
    @Test
    fun theSameIntervalTwiceIssuesOneRequest() {
        source.start()
        val before = port.requests.size
        source.setIntervalMillis(SAMPLING_INTERVAL_PARKING_MILLIS)
        source.setIntervalMillis(SAMPLING_INTERVAL_PARKING_MILLIS)
        assertEquals(before + 1, port.requests.size)
    }

    /** @requirement FR-049, FR-052 */
    @Test
    fun givenNoFineLocationPermission_whenStarted_thenNoRequestAndNoThrow() {
        port.hasPermission = false
        source.start()
        source.setIntervalMillis(SAMPLING_INTERVAL_PARKING_MILLIS)
        assertTrue(port.requests.isEmpty())
        assertEquals(0, port.activeSubscriptions)
    }

    /** @requirement FR-040 */
    @Test
    fun eachReadingCarriesTheMonotonicReceiptTime() {
        val received = collectWhile {
            source.start()
            port.deliver(location())
            clock.advanceBy(SAMPLING_INTERVAL_PARKING_MILLIS)
            port.deliver(location())
        }
        assertEquals(listOf(123_456L, 123_456L + SAMPLING_INTERVAL_PARKING_MILLIS), received.map { it.receivedElapsedMillis })
    }

    /** @requirement FR-029 */
    @Test
    fun stopRemovesTheSubscription() {
        source.start()
        source.stop()
        assertEquals(0, port.activeSubscriptions)
    }
}
