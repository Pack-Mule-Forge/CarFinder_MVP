package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.guidance.GeoMath

/**
 * The most recent [CarFinderConstants.CONVERGENCE_SAMPLE_COUNT] readings that have an accuracy value. Immutable.
 *
 * @requirement FR-010, FR-011, FR-016
 */
data class ConvergenceWindow(val readings: List<LocationReading> = emptyList()) {

    /** Full, and every pair at most the convergence radius apart (FR-011). */
    val isConverged: Boolean
        get() = readings.size == CarFinderConstants.CONVERGENCE_SAMPLE_COUNT &&
            readings.indices.all { i ->
                (i + 1 until readings.size).all { j -> distance(readings[i], readings[j]) <= CarFinderConstants.CONVERGENCE_RADIUS_METERS }
            }

    val lastReceivedElapsedMillis: Long? get() = readings.lastOrNull()?.receivedElapsedMillis

    val centroid: Pair<Double, Double> get() = GeoMath.centroid(readings.map { it.latitude to it.longitude })

    /** The largest of each reading's accuracy plus its distance from the centroid (FR-016), never a mean. */
    val accuracyRadiusMeters: Double
        get() {
            val (lat, lon) = centroid
            return readings.maxOf { checkNotNull(it.accuracyMeters) + GeoMath.distanceMeters(it.latitude, it.longitude, lat, lon) }
        }

    /** Adds [reading] and drops the oldest when full. A reading without accuracy is ignored (FR-010). */
    fun add(reading: LocationReading): ConvergenceWindow =
        if (reading.accuracyMeters == null) this
        else ConvergenceWindow((readings + reading).takeLast(CarFinderConstants.CONVERGENCE_SAMPLE_COUNT))

    fun toParkedLocation(declaredAtEpochMillis: Long): ParkedLocation {
        val (lat, lon) = centroid
        return ParkedLocation(lat, lon, accuracyRadiusMeters, declaredAtEpochMillis)
    }

    private fun distance(a: LocationReading, b: LocationReading) =
        GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}
