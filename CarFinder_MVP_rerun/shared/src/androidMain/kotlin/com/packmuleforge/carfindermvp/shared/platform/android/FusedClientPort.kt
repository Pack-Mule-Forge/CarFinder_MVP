package com.packmuleforge.carfindermvp.shared.platform.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

/** Thin seam over the Fused Location Provider so the source can be tested with a fake (Constitution III). */
interface FusedClientPort {
    /** @throws SecurityException if location permission is missing. */
    fun requestUpdates(request: LocationRequest, callback: LocationCallback)

    fun removeUpdates(callback: LocationCallback)
}

internal class PlayServicesFusedClientPort(context: Context) : FusedClientPort {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    // Permission is checked by the caller's SecurityException handling; the platform throws if it is missing.
    @SuppressLint("MissingPermission")
    override fun requestUpdates(request: LocationRequest, callback: LocationCallback) {
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun removeUpdates(callback: LocationCallback) {
        client.removeLocationUpdates(callback)
    }
}
