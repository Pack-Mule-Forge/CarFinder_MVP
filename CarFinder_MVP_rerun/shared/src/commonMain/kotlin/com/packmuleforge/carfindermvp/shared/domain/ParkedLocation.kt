package com.packmuleforge.carfindermvp.shared.domain

import kotlinx.serialization.Serializable

/**
 * The single current saved car position: the centroid of the converging readings and its accuracy radius.
 *
 * @requirement FR-012, FR-013
 */
@Serializable
data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAtEpochMillis: Long,
) {
    init {
        require(accuracyMeters > 0.0 && accuracyMeters.isFinite()) { "accuracyMeters must be > 0, was $accuracyMeters" }
    }
}
