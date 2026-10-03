package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Reading builders whose values derive from [CarFinderConstants], so no test repeats a constant (QR-005). */
object Readings {
    /** Comfortably above the driving-speed threshold. */
    const val DRIVING_MPH = CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH * 2

    /** Comfortably at or below the parking-speed threshold. */
    const val PARKED_MPH = CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH / 2

    /** Between the parking and driving thresholds. */
    const val DEAD_ZONE_MPH =
        (CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH + CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH) / 2

    /** An accuracy radius well inside the convergence radius. */
    const val GOOD_ACCURACY_METERS = CarFinderConstants.CONVERGENCE_RADIUS_METERS / 4

    const val BASE_LATITUDE = 37.4220
    const val BASE_LONGITUDE = -122.0841

    /** Must match the radius used by the shared distance math. */
    const val EARTH_RADIUS_METERS = 6_371_008.8

    fun mphToMetersPerSecond(mph: Double): Double = mph / CarFinderConstants.MPH_PER_METER_PER_SECOND

    fun readingAt(
        latitude: Double = BASE_LATITUDE,
        longitude: Double = BASE_LONGITUDE,
        accuracyMeters: Double? = GOOD_ACCURACY_METERS,
        speedMph: Double? = null,
        receivedElapsedMillis: Long = 0,
    ) = LocationReading(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMph?.let(::mphToMetersPerSecond),
        receivedElapsedMillis = receivedElapsedMillis,
    )

    /**
     * A reading [northMeters] then [eastMeters] from the base point. The north leg runs along a meridian and the
     * east leg along a parallel, each placed exactly on the sphere.
     */
    fun readingOffset(
        northMeters: Double,
        eastMeters: Double,
        accuracyMeters: Double? = GOOD_ACCURACY_METERS,
        speedMph: Double? = null,
        receivedElapsedMillis: Long = 0,
        fromLatitude: Double = BASE_LATITUDE,
        fromLongitude: Double = BASE_LONGITUDE,
    ): LocationReading {
        val (latitude, longitude) = offset(fromLatitude, fromLongitude, northMeters, eastMeters)
        return readingAt(latitude, longitude, accuracyMeters, speedMph, receivedElapsedMillis)
    }

    /**
     * Three readings whose pairwise distances are [d12], [d23] and [d13], received [spacingMillis] apart.
     */
    fun pairwiseTriangle(
        d12: Double,
        d23: Double,
        d13: Double,
        accuracyMeters: Double? = GOOD_ACCURACY_METERS,
        speedMph: Double? = null,
        startElapsedMillis: Long = 0,
        spacingMillis: Long = CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS,
        northOriginMeters: Double = 0.0,
        eastOriginMeters: Double = 0.0,
    ): List<LocationReading> {
        val along = (d12 * d12 + d13 * d13 - d23 * d23) / (2 * d12)
        val across = sqrt(max(0.0, d13 * d13 - along * along))
        val points = listOf(0.0 to 0.0, d12 to 0.0, along to across)
        return points.mapIndexed { index, (north, east) ->
            readingOffset(
                northMeters = northOriginMeters + north,
                eastMeters = eastOriginMeters + east,
                accuracyMeters = accuracyMeters,
                speedMph = speedMph,
                receivedElapsedMillis = startElapsedMillis + index * spacingMillis,
            )
        }
    }

    private fun offset(latitude: Double, longitude: Double, north: Double, east: Double): Pair<Double, Double> {
        val newLatitude = latitude + toDegrees(north / EARTH_RADIUS_METERS)
        val halfAngle = sin(east / (2 * EARTH_RADIUS_METERS)) / cos(toRadians(newLatitude))
        val deltaLongitude = 2 * asin(halfAngle)
        return newLatitude to longitude + toDegrees(deltaLongitude)
    }

    private fun toDegrees(radians: Double) = radians * HALF_TURN_DEGREES / PI
    private fun toRadians(degrees: Double) = degrees * PI / HALF_TURN_DEGREES

    private const val HALF_TURN_DEGREES = 180.0
}
