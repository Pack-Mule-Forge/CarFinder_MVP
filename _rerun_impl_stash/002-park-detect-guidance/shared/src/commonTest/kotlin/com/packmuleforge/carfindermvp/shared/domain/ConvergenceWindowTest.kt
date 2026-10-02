package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Pairwise convergence over the most recent PARKING readings.
 * @requirement QR-001
 */
class ConvergenceWindowTest {

    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private val count = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT

    private fun ConvergenceWindow.addAll(readings: List<LocationReading>) = readings.fold(this) { w, r -> w.add(r) }

    /** Readings spread in a small circle, every pair well inside the radius. */
    private fun cluster(n: Int = count) = List(n) { i ->
        val angle = 2 * kotlin.math.PI * i / n
        Readings.readingOffset(radius * 0.2 * kotlin.math.cos(angle), radius * 0.2 * kotlin.math.sin(angle))
    }

    private fun distance(a: LocationReading, b: LocationReading) =
        GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** @requirement FR-007 */
    @Test
    fun readingsInALine_withinRadiusOfCentroid_butNotPairwise_doNotConverge() {
        val line = List(count) { i -> Readings.readingOffset(northMeters = radius * 0.9 * i, eastMeters = 0.0) }
        val centroid = GeoMath.centroid(line.map { LatLon(it.latitude, it.longitude) })
        // Precondition from the spec's worked example: each reading is within the radius of the centroid...
        line.forEach {
            assertTrue(GeoMath.distanceMeters(it.latitude, it.longitude, centroid.latitude, centroid.longitude) <= radius)
        }
        // ...but the ends are further apart than the radius, so the pairwise test fails.
        assertTrue(distance(line.first(), line.last()) > radius)
        assertFalse(ConvergenceWindow.empty().addAll(line).isConverged)
    }

    /** @requirement FR-007 */
    @Test
    fun readingsPairwiseWithinRadius_converge() {
        assertTrue(ConvergenceWindow.empty().addAll(cluster()).isConverged)
    }

    /** @requirement FR-008 */
    @Test
    fun window_slides_droppingOldestReading() {
        val far = Readings.readingOffset(northMeters = radius * 5, eastMeters = 0.0)
        val withOutlier = ConvergenceWindow.empty().addAll(listOf(far) + cluster(count - 1))
        assertFalse(withOutlier.isConverged)
        // One more clustered reading pushes the outlier out of the window.
        assertTrue(withOutlier.add(cluster().last()).isConverged)
    }

    /** @requirement FR-007 */
    @Test
    fun readingWithoutAccuracy_isIgnored() {
        val window = ConvergenceWindow.empty().addAll(cluster(count - 1))
        val noAccuracy = Readings.readingOffset(0.0, 0.0, accuracy = null)
        assertFalse(window.add(noAccuracy).isConverged)
        assertEquals(window, window.add(noAccuracy))
    }

    /** @requirement FR-007 */
    @Test
    fun window_isNotConverged_beforeSampleCountReadings() {
        assertFalse(ConvergenceWindow.empty().addAll(cluster(count - 1)).isConverged)
    }

    /** @requirement FR-012 */
    @Test
    fun toParkedLocation_isCentroid_withMaxOfAccuracyPlusDistance() {
        val readings = cluster().mapIndexed { i, r -> r.copy(accuracyMeters = Readings.GOOD_ACCURACY_METERS + i) }
        val window = ConvergenceWindow.empty().addAll(readings)
        val captured = 1_234L
        val parked = window.toParkedLocation(captured)

        val centroid = GeoMath.centroid(readings.map { LatLon(it.latitude, it.longitude) })
        assertEquals(centroid.latitude, parked.latitude, 1e-12)
        assertEquals(centroid.longitude, parked.longitude, 1e-12)
        val expectedAccuracy = readings.maxOf {
            it.accuracyMeters!! + GeoMath.distanceMeters(centroid.latitude, centroid.longitude, it.latitude, it.longitude)
        }
        assertEquals(expectedAccuracy, parked.accuracyMeters, 1e-9)
        assertEquals(captured, parked.capturedAtEpochMillis)
    }

    /** @requirement FR-008 */
    @Test
    fun empty_isUnconverged() {
        assertFalse(ConvergenceWindow.empty().isConverged)
    }

    /** @requirement FR-008 */
    @Test
    fun add_returnsNewWindow_andLeavesReceiverUnchanged() {
        val before = ConvergenceWindow.empty().addAll(cluster(count - 1))
        val after = before.add(cluster().last())
        assertNotSame(before, after)
        assertFalse(before.isConverged)
        assertTrue(after.isConverged)
    }
}
