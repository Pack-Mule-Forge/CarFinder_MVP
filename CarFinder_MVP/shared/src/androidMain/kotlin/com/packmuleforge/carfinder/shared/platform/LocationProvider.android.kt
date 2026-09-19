package com.packmuleforge.carfinder.shared.platform

import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import android.content.Context

/**
 * Android implementation of LocationProvider using Fused Location Provider.
 * Adapts the three request tiers (DRIVING, PARKING, PARKED) to FLP priority levels
 * and update intervals per research.md R-03.
 */
@Requirement("FR-005", "FR-014", "SC-010")
actual class LocationProvider(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    actual fun samples(request: LocationRequestTier): Flow<LocationSample> = callbackFlow {
        val locationRequest = when (request) {
            LocationRequestTier.DRIVING -> {
                // Balanced accuracy, 15-30 second interval (low power)
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L).build()
            }

            LocationRequestTier.PARKING -> {
                // High accuracy, 5 second interval (PARKING_SAMPLE_INTERVAL from FR-005)
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    ParkingConstants.PARKING_SAMPLE_INTERVAL_MILLIS
                ).build()
            }

            LocationRequestTier.PARKED -> {
                // Balanced accuracy, 30-60 second interval (low power, infrequent)
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L).build()
            }
        }

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                for (location in result.locations) {
                    if (location.accuracy >= 0) {  // Drop locations with invalid accuracy
                        val sample = LocationSample(
                            point = GeoPoint(
                                latitudeDegrees = location.latitude,
                                longitudeDegrees = location.longitude,
                                accuracyRadiusMeters = location.accuracy.toDouble()
                            ),
                            speedMetersPerSecond = location.speed.toDouble(),
                            timestampEpochMillis = location.time
                        )
                        trySend(sample)
                    }
                }
            }
        }

        try {
            @Suppress("MissingPermission")  // Permissions are checked by the service before calling
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
