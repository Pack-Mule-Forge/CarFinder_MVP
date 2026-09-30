package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Supporting math for the guidance display (distance, bearing, centroid).
 * @requirement FR-020, FR-021, FR-025
 */
class GeoMathTest {

    // Reference pair: Nashville BNA -> Los Angeles LAX, the Rosetta Code haversine example
    // (2887.26 km with R = 6372.8 km). The tolerance absorbs the different Earth radius used here.
    private val bna = LatLon(36.12, -86.67)
    private val lax = LatLon(33.94, -118.40)
    private val referenceKm = 2887.26
    private val relativeTolerance = 0.005

    /** @requirement FR-025 */
    @Test
    fun distanceForPublishedReferencePair_isWithinHalfPercent() {
        val km = GeoMath.distanceMeters(bna.latitude, bna.longitude, lax.latitude, lax.longitude) / 1_000
        assertTrue(abs(km - referenceKm) / referenceKm < relativeTolerance, "was $km km")
    }

    /** @requirement FR-025 */
    @Test
    fun identicalPoints_areZeroMetersApart() {
        assertEquals(0.0, GeoMath.distanceMeters(bna.latitude, bna.longitude, bna.latitude, bna.longitude))
    }

    /** @requirement FR-021 */
    @Test
    fun initialBearing_forCardinalDirections() {
        val origin = LatLon(0.0, 0.0)
        val step = 0.01
        val tolerance = 1e-6
        assertEquals(0.0, bearing(origin, LatLon(step, 0.0)), tolerance)
        assertEquals(90.0, bearing(origin, LatLon(0.0, step)), tolerance)
        assertEquals(180.0, bearing(origin, LatLon(-step, 0.0)), tolerance)
        assertEquals(270.0, bearing(origin, LatLon(0.0, -step)), tolerance)
    }

    /** @requirement FR-021 */
    @Test
    fun initialBearing_isAlwaysInZeroTo360() {
        val targets = listOf(LatLon(1.0, 1.0), LatLon(-1.0, -1.0), LatLon(1.0, -1.0), LatLon(-1.0, 1.0), lax)
        for (target in targets) {
            val result = bearing(bna, target)
            assertTrue(result >= 0.0 && result < 360.0, "bearing $result out of range")
        }
    }

    /** @requirement FR-020 */
    @Test
    fun centroid_ofPointsStraddlingAntimeridian_liesOnAntimeridian() {
        val points = listOf(LatLon(0.0, 179.9999), LatLon(0.0, -179.9999))
        val c = GeoMath.centroid(points)
        val metersFromAntimeridian = GeoMath.distanceMeters(c.latitude, c.longitude, 0.0, 180.0)
        assertTrue(metersFromAntimeridian < 1.0, "centroid was $c")
    }

    /** @requirement FR-020 */
    @Test
    fun centroid_ofSimplePoints_isTheirMean() {
        val c = GeoMath.centroid(listOf(LatLon(1.0, 2.0), LatLon(3.0, 4.0)))
        assertEquals(2.0, c.latitude, 1e-9)
        assertEquals(3.0, c.longitude, 1e-9)
    }

    private fun bearing(from: LatLon, to: LatLon) =
        GeoMath.initialBearingDegrees(from.latitude, from.longitude, to.latitude, to.longitude)
}
