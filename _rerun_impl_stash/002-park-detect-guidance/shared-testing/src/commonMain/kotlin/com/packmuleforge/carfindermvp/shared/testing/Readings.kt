package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import kotlin.math.PI
import kotlin.math.cos

/** Fixture builders whose values derive from the named constants (Constitution I). */
object Readings {
    /** A fix accuracy comfortably inside the convergence radius. */
    val GOOD_ACCURACY_METERS = CarFinderConstants.CONVERGENCE_RADIUS_METERS / 4

    /** Just above the driving threshold. */
    val DRIVING_MPH = CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH + 1.0

    /** Just below the parking threshold. */
    val PARKED_MPH = CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH - 1.0

    /** Midway through the dead zone between the two thresholds. */
    val DEAD_ZONE_MPH =
        (CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH + CarFinderConstants.DRIVING_SPEED_THRESHOLD_MPH) / 2

    const val BASE_LAT = 37.4220
    const val BASE_LON = -122.0841

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    fun mphToMetersPerSecond(mph: Double) = mph / CarFinderConstants.METERS_PER_SECOND_TO_MPH

    fun readingAt(
        lat: Double = BASE_LAT,
        lon: Double = BASE_LON,
        accuracy: Double? = GOOD_ACCURACY_METERS,
        speedMph: Double? = null,
        elapsedMillis: Long = 0L,
        epochMillis: Long = 0L,
    ) = LocationReading(
        latitude = lat,
        longitude = lon,
        accuracyMeters = accuracy,
        speedMetersPerSecond = speedMph?.let(::mphToMetersPerSecond),
        elapsedRealtimeMillis = elapsedMillis,
        epochMillis = epochMillis,
    )

    /** A reading displaced from the base point by the given meters north and east. */
    fun readingOffset(
        northMeters: Double,
        eastMeters: Double,
        accuracy: Double? = GOOD_ACCURACY_METERS,
        speedMph: Double? = PARKED_MPH,
        elapsedMillis: Long = 0L,
    ): LocationReading {
        val lat = BASE_LAT + northMeters / METERS_PER_DEGREE_LAT
        val lon = BASE_LON + eastMeters / (METERS_PER_DEGREE_LAT * cos(BASE_LAT * PI / 180.0))
        return readingAt(lat, lon, accuracy, speedMph, elapsedMillis)
    }

    fun speed(mph: Double, elapsedMillis: Long = 0L) = readingAt(speedMph = mph, elapsedMillis = elapsedMillis)
}
