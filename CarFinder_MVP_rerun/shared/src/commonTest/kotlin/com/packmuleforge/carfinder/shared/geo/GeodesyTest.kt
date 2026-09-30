package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-023", "FR-024", "FR-028", "FR-035", "FR-037")
class GeodesyTest {
    @Test
    fun distanceMetersReturnZeroBetweenIdenticalPoints() {
        val point = GeoPoint(37.7749, -122.4194, 10.0)
        val distance = Geodesy.distanceMeters(point, point)
        assertTrue(abs(distance) < 0.01, "Distance between identical points should be ~0")
    }

    @Test
    fun distanceMetersAgainstKnownReference() {
        // San Francisco to Los Angeles (approx 559 km)
        val sf = GeoPoint(37.7749, -122.4194, 10.0)
        val la = GeoPoint(34.0522, -118.2437, 10.0)
        val distance = Geodesy.distanceMeters(sf, la)

        // Expected distance is approximately 559 km = 559,000 meters
        // Allow 10% tolerance for spherical approximation
        val expectedMeters = 559_000.0
        assertTrue(
            abs(distance - expectedMeters) < expectedMeters * 0.1,
            "SF to LA distance should be ~559 km, got ${distance / 1000.0} km"
        )
    }

    @Test
    fun trueBearingDegreesReturnsNorthToNorthPoint() {
        val base = GeoPoint(0.0, 0.0, 10.0)
        val north = GeoPoint(1.0, 0.0, 10.0)  // 1° north
        val bearing = Geodesy.trueBearingDegrees(base, north)
        assertTrue(
            abs(bearing - 0.0) < 1.0,
            "Bearing to north should be ~0°, got $bearing°"
        )
    }

    @Test
    fun trueBearingDegreesReturnsEastToEastPoint() {
        val base = GeoPoint(0.0, 0.0, 10.0)
        val east = GeoPoint(0.0, 1.0, 10.0)  // 1° east
        val bearing = Geodesy.trueBearingDegrees(base, east)
        assertTrue(
            abs(bearing - 90.0) < 1.0,
            "Bearing to east should be ~90°, got $bearing°"
        )
    }

    @Test
    fun trueBearingDegreesReturnsSouthToSouthPoint() {
        val base = GeoPoint(0.0, 0.0, 10.0)
        val south = GeoPoint(-1.0, 0.0, 10.0)  // 1° south
        val bearing = Geodesy.trueBearingDegrees(base, south)
        assertTrue(
            abs(bearing - 180.0) < 1.0,
            "Bearing to south should be ~180°, got $bearing°"
        )
    }

    @Test
    fun trueBearingDegreesReturnsWestToWestPoint() {
        val base = GeoPoint(0.0, 0.0, 10.0)
        val west = GeoPoint(0.0, -1.0, 10.0)  // 1° west
        val bearing = Geodesy.trueBearingDegrees(base, west)
        // Bearing to west should be 270° or -90°; we normalize to [0, 360)
        assertTrue(
            abs(bearing - 270.0) < 1.0,
            "Bearing to west should be ~270°, got $bearing°"
        )
    }

    @Test
    fun trueBearingDegreesReturnsValueInRange() {
        val base = GeoPoint(37.7749, -122.4194, 10.0)
        val other = GeoPoint(34.0522, -118.2437, 10.0)
        val bearing = Geodesy.trueBearingDegrees(base, other)
        assertTrue(
            bearing >= 0.0 && bearing < 360.0,
            "Bearing should be in [0, 360), got $bearing"
        )
    }

    @Test
    fun trueBearingDegreesAntimeridianCrossing() {
        // Point on west side of antimeridian and point on east side
        val west = GeoPoint(0.0, 179.0, 10.0)
        val east = GeoPoint(0.0, -179.0, 10.0)
        val bearing = Geodesy.trueBearingDegrees(west, east)
        assertTrue(
            bearing >= 0.0 && bearing < 360.0,
            "Bearing across antimeridian should be in [0, 360), got $bearing"
        )
    }
}
