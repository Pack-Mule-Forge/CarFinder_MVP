package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geodetic calculations: distance and bearing between two points on Earth.
 * Uses the haversine formula for distance and the forward azimuth formula for bearing.
 */
@Requirement("FR-023", "FR-024", "FR-028")
object Geodesy {
    private const val EARTH_RADIUS_METERS = 6_371_008.8  // WGS84 mean radius

    /**
     * Great-circle distance between two points using the haversine formula.
     *
     * @param from Starting point
     * @param to Destination point
     * @return Distance in meters
     */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1Rad = from.latitudeDegrees.toRadians()
        val lat2Rad = to.latitudeDegrees.toRadians()
        val dLatRad = (to.latitudeDegrees - from.latitudeDegrees).toRadians()
        val dLonRad = (to.longitudeDegrees - from.longitudeDegrees).toRadians()

        val a = sin(dLatRad / 2) * sin(dLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLonRad / 2) * sin(dLonRad / 2)
        val c = 2 * asin(kotlin.math.sqrt(a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * True (north) bearing from one point to another, in degrees [0, 360).
     * Returns the initial bearing when traveling along the great circle from 'from' to 'to'.
     *
     * @param from Starting point
     * @param to Destination point
     * @return Bearing in degrees [0, 360)
     */
    fun trueBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1Rad = from.latitudeDegrees.toRadians()
        val lat2Rad = to.latitudeDegrees.toRadians()
        val dLonRad = (to.longitudeDegrees - from.longitudeDegrees).toRadians()

        val x = sin(dLonRad) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearingRad = atan2(x, y)
        var bearingDeg = bearingRad.toDegrees()

        // Normalize to [0, 360)
        bearingDeg = (bearingDeg + 360) % 360

        return bearingDeg
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
