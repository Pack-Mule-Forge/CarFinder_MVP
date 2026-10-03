package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical distance and position math on WGS-84 degrees. */
object GeoMath {
    /** The mean Earth radius used by the haversine formula; part of the formula, not a tunable value. */
    val EARTH_RADIUS_METERS: Double = 6_371_008.8

    private val HALF_TURN_DEGREES: Double = 180.0

    fun toRadians(degrees: Double): Double = degrees * PI / HALF_TURN_DEGREES
    fun toDegrees(radians: Double): Double = radians * HALF_TURN_DEGREES / PI

    /** Haversine great-circle distance in meters. */
    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val phi1 = toRadians(aLat)
        val phi2 = toRadians(bLat)
        val dPhi = toRadians(bLat - aLat)
        val dLambda = toRadians(bLon - aLon)
        val h = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }

    /**
     * The initial (forward-azimuth) true bearing from the first point to the second, in [0, 360).
     *
     * @requirement FR-032
     */
    fun initialBearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val phi1 = toRadians(fromLat)
        val phi2 = toRadians(toLat)
        val dLambda = toRadians(toLon - fromLon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeDegrees(toDegrees(atan2(y, x)))
    }

    /** Wraps any angle into [0, 360). */
    fun normalizeDegrees(degrees: Double): Double {
        val wrapped = ((degrees % FULL_TURN_DEGREES) + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
        return if (wrapped >= FULL_TURN_DEGREES) 0.0 else wrapped
    }

    private val FULL_TURN_DEGREES: Double = 360.0

    /** The mean latitude and longitude of [points] (data-model ConvergenceWindow). */
    fun centroid(points: List<Pair<Double, Double>>): Pair<Double, Double> {
        require(points.isNotEmpty()) { "centroid of no points" }
        return points.sumOf { it.first } / points.size to points.sumOf { it.second } / points.size
    }
}
