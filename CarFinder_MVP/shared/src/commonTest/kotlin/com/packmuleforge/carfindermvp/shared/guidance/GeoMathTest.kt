package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-001 */
class GeoMathTest {

    private val km = 1_000.0

    /** @requirement FR-032 */
    @Test
    fun landsEndToJohnOGroatsMatchesThePublishedHaversineExample() {
        // Movable Type Scripts, "Calculate distance and bearing between two Latitude/Longitude points":
        // 50°03'59"N 005°42'53"W to 58°38'38"N 003°04'12"W: 968.9 km and initial bearing 009°07'11".
        val from = 50 + 3 / 60.0 + 59 / 3600.0 to -(5 + 42 / 60.0 + 53 / 3600.0)
        val to = 58 + 38 / 60.0 + 38 / 3600.0 to -(3 + 4 / 60.0 + 12 / 3600.0)
        val distance = GeoMath.distanceMeters(from.first, from.second, to.first, to.second)
        val bearing = GeoMath.initialBearingDegrees(from.first, from.second, to.first, to.second)
        assertEquals(968.9 * km, distance, 0.1 * km)
        assertEquals(9 + 7 / 60.0 + 11 / 3600.0, bearing, 0.001)
    }

    /** @requirement FR-032 */
    @Test
    fun laxToJfkMatchesTheAviationFormularyExample() {
        // Ed Williams, Aviation Formulary: LAX (33°57'N 118°24'W) to JFK (40°38'N 73°47'W),
        // distance 0.623585 rad, initial true course 66 degrees (1.150035 rad).
        val lax = 33 + 57 / 60.0 to -(118 + 24 / 60.0)
        val jfk = 40 + 38 / 60.0 to -(73 + 47 / 60.0)
        val distance = GeoMath.distanceMeters(lax.first, lax.second, jfk.first, jfk.second)
        val bearing = GeoMath.initialBearingDegrees(lax.first, lax.second, jfk.first, jfk.second)
        assertEquals(0.623585 * GeoMath.EARTH_RADIUS_METERS, distance, 1 * km)
        assertEquals(1.150035 * 180 / PI, bearing, 0.01)
    }

    /** @requirement FR-032 */
    @Test
    fun oneDegreeOfLongitudeOnTheEquatorIsOneRadianOverFiftySevenPointThree() {
        val distance = GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(GeoMath.EARTH_RADIUS_METERS * PI / 180, distance, 0.01)
    }

    /** @requirement FR-032 */
    @Test
    fun dueNorthIsZeroAndDueEastIsNinety() {
        assertEquals(0.0, GeoMath.initialBearingDegrees(37.0, -122.0, 37.01, -122.0), 1e-9)
        assertEquals(90.0, GeoMath.initialBearingDegrees(0.0, 0.0, 0.0, 0.01), 1e-9)
        assertEquals(180.0, GeoMath.initialBearingDegrees(37.01, -122.0, 37.0, -122.0), 1e-9)
        assertEquals(270.0, GeoMath.initialBearingDegrees(0.0, 0.01, 0.0, 0.0), 1e-9)
    }

    /** @requirement FR-032 */
    @Test
    fun theBearingIsAlwaysInZeroToThreeSixty() {
        val points = listOf(-60.0, -1.0, 0.0, 1.0, 44.0, 89.0)
        for (aLat in points) for (bLat in points) for (dLon in listOf(-179.0, -0.001, 0.0, 0.001, 179.0)) {
            val bearing = GeoMath.initialBearingDegrees(aLat, 11.0, bLat, 11.0 + dLon)
            assertTrue(bearing >= 0.0 && bearing < 360.0, "bearing $bearing for $aLat,$bLat,$dLon")
        }
    }

    /** @requirement FR-031 */
    @Test
    fun zeroDistanceDoesNotFail() {
        assertEquals(0.0, GeoMath.distanceMeters(37.0, -122.0, 37.0, -122.0))
        val bearing = GeoMath.initialBearingDegrees(37.0, -122.0, 37.0, -122.0)
        assertTrue(bearing.isFinite() && bearing >= 0.0 && bearing < 360.0)
    }
}
