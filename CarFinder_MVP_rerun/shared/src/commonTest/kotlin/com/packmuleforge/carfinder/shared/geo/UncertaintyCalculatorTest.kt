package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Requirement("FR-015", "FR-016", "FR-035", "FR-037")
class UncertaintyCalculatorTest {
    @Test
    fun calculatesUncertaintyAsSum() {
        val parkedLocation = GeoPoint(37.7749, -122.4194, 20.0)  // Accuracy: 20m
        val currentFix = GeoPoint(37.7750, -122.4195, 15.0)      // Accuracy: 15m

        val uncertainty = UncertaintyCalculator.calculate(parkedLocation, currentFix)
        assertEquals(35.0, uncertainty, "Uncertainty should be sum of accuracy radii")
    }

    @Test
    fun handlesZeroAccuracy() {
        val parkedLocation = GeoPoint(37.7749, -122.4194, 0.0)
        val currentFix = GeoPoint(37.7750, -122.4195, 10.0)

        val uncertainty = UncertaintyCalculator.calculate(parkedLocation, currentFix)
        assertEquals(10.0, uncertainty, "Sum with zero accuracy should be correct")
    }

    @Test
    fun neverRoundsOrClamps() {
        val parkedLocation = GeoPoint(37.7749, -122.4194, 3.3)
        val currentFix = GeoPoint(37.7750, -122.4195, 5.5)

        val uncertainty = UncertaintyCalculator.calculate(parkedLocation, currentFix)
        val expected = 3.3 + 5.5  // 8.8
        assertTrue(abs(uncertainty - expected) < 0.0001, "Uncertainty should not be rounded")
    }

    @Test
    fun largeAccuracyValues() {
        val parkedLocation = GeoPoint(37.7749, -122.4194, 1000.0)
        val currentFix = GeoPoint(37.7750, -122.4195, 2000.0)

        val uncertainty = UncertaintyCalculator.calculate(parkedLocation, currentFix)
        assertEquals(3000.0, uncertainty)
    }
}
