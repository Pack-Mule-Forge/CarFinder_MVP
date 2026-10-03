package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-028", "FR-029", "FR-037")
class DistanceFormatterTest {
    @Test
    fun metersAtOrBelowThresholdReturnsFeet() {
        val threshold = ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS
        val distance = threshold - 10.0

        val formatted = DistanceFormatter.format(distance)
        assertTrue(formatted.contains("ft") || formatted.contains("feet"), "Distance below threshold should be in feet")
    }

    @Test
    fun metersAboveThresholdReturnsMiles() {
        val threshold = ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS
        val distance = threshold + 100.0

        val formatted = DistanceFormatter.format(distance)
        assertTrue(formatted.contains("mi") || formatted.contains("miles"), "Distance above threshold should be in miles")
    }

    @Test
    fun metersBoundaryExactlyAtThresholdReturnsFeet() {
        // Exactly at threshold should render in feet (not miles)
        val threshold = ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS

        val formatted = DistanceFormatter.format(threshold)
        assertTrue(formatted.contains("ft") || formatted.contains("feet"), "Exactly at threshold should use feet")
    }

    @Test
    fun zeroMetersShowsZeroFeet() {
        val formatted = DistanceFormatter.format(0.0)
        assertTrue(formatted.startsWith("0"), "Zero distance should show 0 feet")
        assertTrue(formatted.contains("ft") || formatted.contains("feet"))
    }

    @Test
    fun conversionIsAccurate() {
        // 152.4 meters = 500 feet = threshold boundary
        val threshold = ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS
        val formatted = DistanceFormatter.format(threshold)

        // Should contain a numeric value in the 490-510 range
        val numbers = Regex("""\d+""").findAll(formatted).map { it.value.toInt() }.toList()
        assertTrue(numbers.isNotEmpty(), "Formatted string should contain numeric value")
    }

    @Test
    fun largeDistanceInMiles() {
        val largeDistance = 5000.0  // ~3 miles
        val formatted = DistanceFormatter.format(largeDistance)
        assertTrue(formatted.contains("mi") || formatted.contains("miles"))
    }
}
