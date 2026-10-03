package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** The platform request the location source issues: one subscription, no batching when the delay is zero. */
data class FusedRequest(
    val intervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val maxUpdateDelayMillis: Long,
    val isHighAccuracy: Boolean,
)

/** A thin seam over the fused location client so host tests drive [FusedLocationSource] with a test double. */
internal interface FusedClientPort {
    fun hasFineLocationPermission(): Boolean
    fun requestLocationUpdates(request: FusedRequest, onLocation: (Location) -> Unit)
    fun removeLocationUpdates()
}

internal class PlayServicesFusedClientPort(private val context: Context) : FusedClientPort {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    override fun hasFineLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Permission is checked by FusedLocationSource before every request.
    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(request: FusedRequest, onLocation: (Location) -> Unit) {
        val priority = if (request.isHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val platformRequest = LocationRequest.Builder(priority, request.intervalMillis)
            .setMinUpdateIntervalMillis(request.minUpdateIntervalMillis)
            .setMaxUpdateDelayMillis(request.maxUpdateDelayMillis)
            .build()
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(onLocation)
            }
        }
        try {
            client.requestLocationUpdates(platformRequest, newCallback, Looper.getMainLooper())
            callback = newCallback
        } catch (_: SecurityException) {
            callback = null
        }
    }

    override fun removeLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
