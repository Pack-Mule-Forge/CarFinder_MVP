package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.test.Test
import kotlin.test.assertFailsWith

@Requirement("FR-001", "FR-007", "FR-015", "FR-037")
class GeoPointTest {
    @Test
    fun rejectsLatitudeAbove90() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(91.0, 0.0, 10.0)
        }
    }

    @Test
    fun rejectsLatitudeBelow90() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(-91.0, 0.0, 10.0)
        }
    }

    @Test
    fun acceptsLatitudeExactly90() {
        GeoPoint(90.0, 0.0, 10.0)  // Should not throw
    }

    @Test
    fun acceptsLatitudeExactlyNegative90() {
        GeoPoint(-90.0, 0.0, 10.0)  // Should not throw
    }

    @Test
    fun rejectsLongitudeAbove180() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(0.0, 181.0, 10.0)
        }
    }

    @Test
    fun rejectsLongitudeBelow180() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(0.0, -181.0, 10.0)
        }
    }

    @Test
    fun acceptsLongitudeExactly180() {
        GeoPoint(0.0, 180.0, 10.0)  // Should not throw
    }

    @Test
    fun acceptsLongitudeExactlyNegative180() {
        GeoPoint(0.0, -180.0, 10.0)  // Should not throw
    }

    @Test
    fun rejectsNegativeAccuracyRadius() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(0.0, 0.0, -1.0)
        }
    }

    @Test
    fun acceptsZeroAccuracyRadius() {
        GeoPoint(0.0, 0.0, 0.0)  // Should not throw
    }

    @Test
    fun createsValidPointWithinBounds() {
        val point = GeoPoint(37.7749, -122.4194, 15.5)
        assert(point.latitudeDegrees == 37.7749)
        assert(point.longitudeDegrees == -122.4194)
        assert(point.accuracyRadiusMeters == 15.5)
    }
}
