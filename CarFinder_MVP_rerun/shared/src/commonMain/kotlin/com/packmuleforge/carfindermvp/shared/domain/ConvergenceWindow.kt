package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon

/**
 * The most recent PARKING readings that carry an accuracy radius. Converged when the window is full and every
 * pair of readings is at most [radiusMeters] apart; distance to the centroid is deliberately not the test.
 * Immutable: [add] returns a new window.
 *
 * @requirement FR-007, FR-008, FR-012
 */
class ConvergenceWindow private constructor(
    private val readings: List<LocationReading>,
    val sampleCount: Int,
    val radiusMeters: Double,
) {
    val isConverged: Boolean =
        readings.size == sampleCount && readings.indices.all { i ->
            (i + 1 until readings.size).all { j -> distance(readings[i], readings[j]) <= radiusMeters }
        }

    /** Adds a reading, dropping the oldest once full. Readings without accuracy are ignored. */
    fun add(reading: LocationReading): ConvergenceWindow =
        if (reading.accuracyMeters == null) this
        else ConvergenceWindow((readings + reading).takeLast(sampleCount), sampleCount, radiusMeters)

    /**
     * The centroid of the window, with accuracy = max over readings of (reading accuracy + distance from the
     * centroid): the smallest circle around the centroid containing every reading's own accuracy circle.
     *
     * @requirement FR-012
     */
    fun toParkedLocation(capturedAtEpochMillis: Long): ParkedLocation {
        check(readings.isNotEmpty()) { "no readings in window" }
        val centroid = GeoMath.centroid(readings.map { LatLon(it.latitude, it.longitude) })
        val accuracy = readings.maxOf {
            it.accuracyMeters!! +
                GeoMath.distanceMeters(centroid.latitude, centroid.longitude, it.latitude, it.longitude)
        }
        return ParkedLocation(centroid.latitude, centroid.longitude, accuracy, capturedAtEpochMillis)
    }

    override fun equals(other: Any?) = other is ConvergenceWindow && other.readings == readings &&
        other.sampleCount == sampleCount && other.radiusMeters == radiusMeters

    override fun hashCode() = (readings.hashCode() * 31 + sampleCount) * 31 + radiusMeters.hashCode()

    override fun toString() = "ConvergenceWindow(size=${readings.size}/$sampleCount, converged=$isConverged)"

    companion object {
        fun empty(
            sampleCount: Int = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT,
            radiusMeters: Double = CarFinderConstants.CONVERGENCE_RADIUS_METERS,
        ) = ConvergenceWindow(emptyList(), sampleCount, radiusMeters)

        private fun distance(a: LocationReading, b: LocationReading) =
            GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }
}
