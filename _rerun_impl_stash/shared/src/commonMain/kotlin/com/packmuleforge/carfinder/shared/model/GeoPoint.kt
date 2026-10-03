package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement

@Requirement("FR-001", "FR-007", "FR-015")
data class GeoPoint(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val accuracyRadiusMeters: Double
) {
    init {
        require(latitudeDegrees in -90.0..90.0) {
            "Latitude must be in range [-90, 90], got $latitudeDegrees"
        }
        require(longitudeDegrees in -180.0..180.0) {
            "Longitude must be in range [-180, 180], got $longitudeDegrees"
        }
        require(accuracyRadiusMeters >= 0.0) {
            "Accuracy radius must be non-negative, got $accuracyRadiusMeters"
        }
    }
}
