package com.packmuleforge.carfindermvp.shared.platform.android

import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.platform.LocationSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one Fused Location subscription, shared by the engine and guidance. A profile change removes the current
 * request and issues a new one, so there is never more than one active subscription (research R2).
 *
 * @requirement FR-006, FR-033
 */
class FusedLocationSource(private val port: FusedClientPort) : LocationSource {

    private val flow = MutableSharedFlow<LocationReading>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val readings: SharedFlow<LocationReading> = flow.asSharedFlow()

    private var profile = SamplingProfile.IDLE_WATCH
    private var started = false

    /** True after the platform refused location access; readings stop until the next successful request. */
    var isPermissionDenied = false
        private set

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location -> toReading(location)?.let(flow::tryEmit) }
        }
    }

    override fun setProfile(profile: SamplingProfile) {
        this.profile = profile
        if (started) subscribe()
    }

    override fun start() {
        started = true
        subscribe()
    }

    override fun stop() {
        started = false
        port.removeUpdates(callback)
    }

    private fun subscribe() {
        port.removeUpdates(callback)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, profile.intervalMillis)
            .setMinUpdateIntervalMillis(profile.intervalMillis)
            .setMaxUpdateDelayMillis(0)
            .build()
        try {
            port.requestUpdates(request, callback)
            isPermissionDenied = false
        } catch (e: SecurityException) {
            isPermissionDenied = true
            Log.w(TAG, "Location permission missing; no readings until it is granted", e)
        }
    }

    companion object {
        private const val TAG = "FusedLocationSource"
        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * Maps a platform fix. Missing or invalid accuracy and speed become null (never a default), and
         * out-of-range coordinates drop the reading.
         */
        fun toReading(location: Location): LocationReading? {
            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
                Log.w(TAG, "Dropping fix with out-of-range coordinates")
                return null
            }
            val accuracy = location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() && it >= 0f }
            val speed = location.speed.takeIf { location.hasSpeed() && it.isFinite() && it >= 0f }
            return LocationReading(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = accuracy?.toDouble(),
                speedMetersPerSecond = speed?.toDouble(),
                elapsedRealtimeMillis = location.elapsedRealtimeNanos / NANOS_PER_MILLI,
                epochMillis = location.time,
            )
        }
    }
}
