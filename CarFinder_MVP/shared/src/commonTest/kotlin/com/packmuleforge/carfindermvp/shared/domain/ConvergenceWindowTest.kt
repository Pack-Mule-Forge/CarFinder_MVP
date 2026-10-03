package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_SAMPLE_COUNT
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.testing.Readings.GOOD_ACCURACY_METERS
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlin.math.nextDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ConvergenceWindowTest {

    private val r = CONVERGENCE_RADIUS_METERS

    private fun windowOf(readings: List<LocationReading>) = readings.fold(ConvergenceWindow()) { w, x -> w.add(x) }

    /** @requirement FR-011 */
    @Test
    fun givenPairwiseDistancesWithinTheRadius_thenConverged() {
        assertTrue(windowOf(pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10)).isConverged)
    }

    /** @requirement FR-011 */
    @Test
    fun givenOnePairBeyondTheRadius_thenNotConverged() {
        assertFalse(windowOf(pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 11 / 10)).isConverged)
    }

    /** @requirement FR-011 */
    @Test
    fun givenAPairExactlyTheRadiusApart_thenConverged() {
        val a = readingOffset(0.0, 0.0)
        var b = readingOffset(r, 0.0)
        // Step back by the smallest representable amount until the computed distance is not above the radius.
        while (GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) > r) {
            b = b.copy(latitude = b.latitude.nextDown())
        }
        val distance = GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        assertTrue(r - distance < r / 1_000_000, "pair must be the radius apart to floating precision")
        val middle = readingOffset(r / 2, 0.0)
        assertTrue(windowOf(listOf(a, middle, b)).isConverged)
    }

    /** @requirement FR-011 */
    @Test
    fun givenFewerReadingsThanTheSampleCount_thenNeverConverged() {
        val readings = List(CONVERGENCE_SAMPLE_COUNT - 1) { readingOffset(0.0, 0.0) }
        assertFalse(windowOf(readings).isConverged)
    }

    /** @requirement FR-010 */
    @Test
    fun givenAReadingWithoutAccuracy_thenItIsNotAdded() {
        val window = windowOf(List(CONVERGENCE_SAMPLE_COUNT) { readingOffset(0.0, 0.0, accuracyMeters = null) })
        assertEquals(0, window.readings.size)
        assertFalse(window.isConverged)
    }

    /** @requirement FR-011 */
    @Test
    fun givenAFullWindow_whenAnotherReadingArrives_thenTheOldestLeaves() {
        val far = readingOffset(r * 5, 0.0)
        val near = List(CONVERGENCE_SAMPLE_COUNT) { readingOffset(0.0, 0.0, receivedElapsedMillis = it.toLong()) }
        val window = windowOf(listOf(far) + near)
        assertEquals(near, window.readings)
        assertTrue(window.isConverged)
    }

    /** @requirement FR-016 */
    @Test
    fun accuracyRadiusIsTheMaximumOfAccuracyPlusDistanceToCentroid() {
        val readings = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10)
            .mapIndexed { i, reading -> reading.copy(accuracyMeters = GOOD_ACCURACY_METERS * (i + 1)) }
        val window = windowOf(readings)
        val (lat, lon) = window.centroid
        val expected = readings.maxOf {
            it.accuracyMeters!! + GeoMath.distanceMeters(it.latitude, it.longitude, lat, lon)
        }
        assertEquals(expected, window.accuracyRadiusMeters, r / 1_000_000)
        assertTrue(window.accuracyRadiusMeters > readings.map { it.accuracyMeters!! }.average())
    }

    /** @requirement FR-016 */
    @Test
    fun toParkedLocationCarriesTheCentroidRadiusAndDeclarationTime() {
        val window = windowOf(pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10))
        val declaredAt = 1_234_567L
        val parked = window.toParkedLocation(declaredAt)
        assertEquals(declaredAt, parked.declaredAtEpochMillis)
        assertEquals(window.centroid, parked.latitude to parked.longitude)
        assertEquals(window.accuracyRadiusMeters, parked.accuracyMeters)
    }
}
