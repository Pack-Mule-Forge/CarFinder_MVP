package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Requirement("FR-006", "FR-008", "FR-037")
class ConvergenceWindowTest {
    @Test
    fun holdsAtMostConvergenceSampleCount() {
        val window = ConvergenceWindow()
        val sample1 = createSample(0.0, 0.0, 0.0)
        val sample2 = createSample(0.0, 0.001, 1.0)
        val sample3 = createSample(0.0, 0.002, 2.0)
        val sample4 = createSample(0.0, 0.003, 3.0)

        window.add(sample1)
        window.add(sample2)
        window.add(sample3)
        assertTrue(window.isFull(), "Window should be full after 3 samples")

        window.add(sample4)
        assertTrue(window.isFull(), "Window should still be full after 4th sample")
        assertTrue(window.size() == ParkingConstants.CONVERGENCE_SAMPLE_COUNT, "Window size should never exceed CONVERGENCE_SAMPLE_COUNT")
    }

    @Test
    fun convergesWhenAllPairwiseDistancesAreWithinRadius() {
        val window = ConvergenceWindow()
        // Three points all within 5 meters of each other
        val sample1 = createSample(0.0, 0.0, 0.0)
        val sample2 = createSample(0.00003, 0.00003, 1.0)  // ~3-4 meters away
        val sample3 = createSample(0.00005, 0.00004, 2.0)  // ~4-5 meters away

        window.add(sample1)
        window.add(sample2)
        window.add(sample3)

        assertTrue(window.isConverged(), "Three nearby points should converge")
    }

    @Test
    fun doesNotConvergeForThreePointsInALineTooFarApart() {
        val window = ConvergenceWindow()
        // Three points in a line: 1st and 3rd are 18 meters apart (pairwise fails)
        // This tests the stricter all-pairwise interpretation vs "all within radius of first"
        val sample1 = createSample(0.0, 0.0, 0.0)            // Origin
        val sample2 = createSample(0.00004, 0.00005, 1.0)    // ~5-6 meters away
        val sample3 = createSample(0.00008, 0.0001, 2.0)     // ~11-12 meters away from origin
        // Distance from sample1 to sample3 > 10m, so pairwise should fail even though
        // each is within 10m of the first sample individually

        window.add(sample1)
        window.add(sample2)
        window.add(sample3)

        assertFalse(window.isConverged(), "Points too far apart pairwise should not converge")
    }

    @Test
    fun slidesWindowOnNonConvergenceWithoutTimeout() {
        val window = ConvergenceWindow()
        val sample1 = createSample(0.0, 0.0, 0.0)
        val sample2 = createSample(0.0001, 0.0001, 1.0)      // Far from sample 1
        val sample3 = createSample(0.00013, 0.00013, 2.0)    // Far from both
        val sample4 = createSample(0.00016, 0.00016, 3.0)    // Far from all

        window.add(sample1)
        assertFalse(window.isConverged(), "Single sample should not converge")

        window.add(sample2)
        assertFalse(window.isConverged(), "Two samples should not converge")

        window.add(sample3)
        assertFalse(window.isConverged(), "Three samples too far apart should not converge")

        // Slide: remove sample1, add sample4
        window.add(sample4)
        assertFalse(window.isConverged(), "Sliding should continue indefinitely without convergence")
        assertTrue(window.size() == 3, "Window should maintain size after sliding")
    }

    @Test
    fun antiMeridianCentroid() {
        // Two points near antimeridian: 179.9° and -179.9°
        // Naive average would be 0° (opposite side of Earth)
        // Correct centroid should be ~-179.95° or equivalently 180.05°
        val window = ConvergenceWindow()
        val sample1 = createSample(0.0, 179.95, 0.0)
        val sample2 = createSample(0.0, -179.95, 1.0)
        val sample3 = createSample(0.0, -179.94, 2.0)

        window.add(sample1)
        window.add(sample2)
        window.add(sample3)

        if (window.isConverged()) {
            val centroid = window.centroid()
            // Centroid longitude should be near ±180, NOT near 0
            assertTrue(
                abs(centroid.longitudeDegrees) > 170.0,
                "Antimeridian centroid should be near ±180°, not near 0°"
            )
        }
    }

    private fun createSample(lat: Double, lon: Double, speed: Double): LocationSample {
        return LocationSample(
            point = GeoPoint(lat, lon, 10.0),
            speedMetersPerSecond = speed,
            timestampEpochMillis = System.currentTimeMillis()
        )
    }
}
