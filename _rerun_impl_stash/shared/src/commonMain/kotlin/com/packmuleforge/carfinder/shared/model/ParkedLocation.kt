package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement

@Requirement("FR-007", "FR-011", "FR-012", "FR-013")
data class ParkedLocation(
    val point: GeoPoint,
    val capturedAtEpochMillis: Long
)
