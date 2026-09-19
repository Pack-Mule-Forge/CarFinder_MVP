package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.geo.Geodesy
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import kotlin.math.PI

/**
 * A sliding window of the last [ParkingConstants.CONVERGENCE_SAMPLE_COUNT] location samples.
 * Detects convergence using the all-pairwise criterion: all samples' pairwise distances must
 * be at most [ParkingConstants.CONVERGENCE_RADIUS_METERS] apart (FR-006).
 *
 * On non-convergence, the window slides by one sample and re-evaluates indefinitely, with no
 * timeout or failure state (FR-008).
 */
@Requirement("FR-006", "FR-007", "FR-008")
class ConvergenceWindow {
    private val samples = mutableListOf<LocationSample>()

    fun add(sample: LocationSample) {
        samples.add(sample)
        if (samples.size > ParkingConstants.CONVERGENCE_SAMPLE_COUNT) {
            samples.removeAt(0)  // Slide the window
        }
    }

    fun isFull(): Boolean = samples.size == ParkingConstants.CONVERGENCE_SAMPLE_COUNT

    fun isConverged(): Boolean {
        if (!isFull()) return false

        // All-pairwise convergence: each sample's distance to every other sample
        // must be at most CONVERGENCE_RADIUS_METERS
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                val distance = Geodesy.distanceMeters(samples[i].point, samples[j].point)
                if (distance > ParkingConstants.CONVERGENCE_RADIUS_METERS) {
                    return false
                }
            }
        }
        return true
    }

    fun size(): Int = samples.size

    fun centroid(): GeoPoint {
        require(isFull()) { "Cannot compute centroid without full window" }

        // Handle antimeridian wrapping: convert to 3D Cartesian, average, convert back
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        var sumAccuracy = 0.0

        for (sample in samples) {
            val lat = sample.point.latitudeDegrees * PI / 180.0
            val lon = sample.point.longitudeDegrees * PI / 180.0

            sumX += kotlin.math.cos(lat) * kotlin.math.cos(lon)
            sumY += kotlin.math.cos(lat) * kotlin.math.sin(lon)
            sumZ += kotlin.math.sin(lat)
            sumAccuracy += sample.point.accuracyRadiusMeters
        }

        val count = samples.size.toDouble()
        sumX /= count
        sumY /= count
        sumZ /= count

        val centroidLat = kotlin.math.atan2(sumZ, kotlin.math.sqrt(sumX * sumX + sumY * sumY)) * 180.0 / PI
        val centroidLon = kotlin.math.atan2(sumY, sumX) * 180.0 / PI

        return GeoPoint(
            latitudeDegrees = centroidLat,
            longitudeDegrees = centroidLon,
            accuracyRadiusMeters = sumAccuracy / count
        )
    }

    fun clear() {
        samples.clear()
    }
}
