package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val latitude: Double, val longitude: Double)

/**
 * Great-circle distance, initial bearing and centroid on a spherical Earth.
 *
 * @requirement FR-020, FR-021, FR-025
 */
object GeoMath {
    /** WGS-84 mean Earth radius. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Haversine distance in meters. */
    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = (bLat - aLat).toRadians()
        val dLon = (bLon - aLon).toRadians()
        val h = sin(dLat / 2).squared() + cos(aLat.toRadians()) * cos(bLat.toRadians()) * sin(dLon / 2).squared()
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }

    /** Forward azimuth from the first point to the second, in [0, 360). */
    fun initialBearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val phi1 = fromLat.toRadians()
        val phi2 = toLat.toRadians()
        val dLon = (toLon - fromLon).toRadians()
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return normalizeDegrees(atan2(y, x).toDegrees())
    }

    /**
     * Arithmetic-mean centroid, exact enough at convergence scale. Longitudes are averaged as deltas from the
     * first point so points straddling the antimeridian do not average to the opposite side of the globe.
     */
    fun centroid(points: List<LatLon>): LatLon {
        require(points.isNotEmpty()) { "centroid of no points" }
        val reference = points.first().longitude
        val meanLat = points.sumOf { it.latitude } / points.size
        val meanDelta = points.sumOf { wrapLongitude(it.longitude - reference) } / points.size
        return LatLon(meanLat, wrapLongitude(reference + meanDelta))
    }

    /** Normalizes an angle to [0, 360). */
    fun normalizeDegrees(degrees: Double): Double {
        val r = degrees % 360.0
        return if (r < 0.0) r + 360.0 else r
    }

    /** Wraps a longitude or longitude delta into [-180, 180). */
    private fun wrapLongitude(degrees: Double): Double = normalizeDegrees(degrees + 180.0) - 180.0

    private fun Double.toRadians() = this * PI / 180.0

    private fun Double.toDegrees() = this * 180.0 / PI

    private fun Double.squared() = this * this
}
