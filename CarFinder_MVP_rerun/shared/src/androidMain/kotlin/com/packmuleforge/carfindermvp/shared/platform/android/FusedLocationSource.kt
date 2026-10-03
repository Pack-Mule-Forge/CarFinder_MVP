package com.packmuleforge.carfindermvp.shared.platform.android

import android.location.Location
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticLog
import com.packmuleforge.carfindermvp.shared.platform.LocationSource
import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one fused-location subscription. Decides field presence explicitly, because the platform reports `0.0` for
 * an absent accuracy or speed, and drops readings with impossible coordinates.
 *
 * @requirement FR-009, FR-010, FR-027, FR-029, FR-049
 */
class FusedLocationSource internal constructor(
    private val port: FusedClientPort,
    private val clock: MonotonicClock,
    private val log: DiagnosticLog,
) : LocationSource {

    private val flow = MutableSharedFlow<LocationReading>(extraBufferCapacity = 64)
    override val readings: SharedFlow<LocationReading> = flow.asSharedFlow()

    /** The most recent reading, used by the heading source for declination. */
    @Volatile
    var latestReading: LocationReading? = null
        private set

    private var intervalMillis = CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
    private var isSubscribed = false

    override fun setIntervalMillis(intervalMillis: Long) {
        if (intervalMillis == this.intervalMillis) return
        this.intervalMillis = intervalMillis
        if (isSubscribed) {
            port.removeLocationUpdates()
            subscribe()
        }
    }

    override fun start() {
        if (isSubscribed || !port.hasFineLocationPermission()) return
        subscribe()
    }

    override fun stop() {
        if (isSubscribed) port.removeLocationUpdates()
        isSubscribed = false
    }

    private fun subscribe() {
        val request = FusedRequest(
            intervalMillis = intervalMillis,
            minUpdateIntervalMillis = intervalMillis,
            maxUpdateDelayMillis = 0,
            isHighAccuracy = true,
        )
        port.requestLocationUpdates(request, ::onLocation)
        isSubscribed = true
    }

    private fun onLocation(location: Location) {
        val reading = toReading(location)
        if (reading == null) {
            log.record(DiagnosticEvent.ReadingDropped)
            return
        }
        latestReading = reading
        flow.tryEmit(reading)
    }

    private fun toReading(location: Location): LocationReading? {
        val latitude = location.latitude
        val longitude = location.longitude
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude < -MAX_LATITUDE || latitude > MAX_LATITUDE) return null
        if (longitude < -MAX_LONGITUDE || longitude > MAX_LONGITUDE) return null
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble().takeIf { it.isFinite() && it > 0 } else null
        val speed = if (location.hasSpeed()) location.speed.toDouble().takeIf { it.isFinite() && it >= 0 } else null
        return LocationReading(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            speedMetersPerSecond = speed,
            receivedElapsedMillis = clock.elapsedRealtimeMillis(),
        )
    }

    private companion object {
        val MAX_LATITUDE = 90.0
        val MAX_LONGITUDE = 180.0
    }
}
