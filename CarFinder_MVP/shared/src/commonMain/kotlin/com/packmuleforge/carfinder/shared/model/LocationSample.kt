package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement

@Requirement("FR-005", "FR-006")
data class LocationSample(
    val point: GeoPoint,
    val speedMetersPerSecond: Double,
    val timestampEpochMillis: Long
) {
    init {
        require(speedMetersPerSecond >= 0.0) {
            "Speed must be non-negative, got $speedMetersPerSecond m/s"
        }
    }
}
