package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.math.roundToInt

/**
 * Format distance in meters as feet or miles depending on a threshold (FR-028, FR-029).
 */
@Requirement("FR-028", "FR-029")
object DistanceFormatter {
    /**
     * Format distance as a human-readable string.
     * At or below [ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS], uses feet.
     * Above that, uses miles.
     *
     * @param distanceMeters Distance in meters
     * @return Formatted string like "123 ft" or "2.5 mi"
     */
    fun format(distanceMeters: Double): String {
        return if (distanceMeters <= ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS) {
            // Convert meters to feet (1 meter = 3.28084 feet)
            val feet = (distanceMeters * 3.28084).roundToInt()
            "$feet ft"
        } else {
            // Convert meters to miles (1 meter = 0.000621371 miles)
            val miles = (distanceMeters * 0.000621371 * 10).roundToInt() / 10.0
            "$miles mi"
        }
    }
}
