package com.packmuleforge.carfindermvp.shared.domain

import kotlinx.serialization.Serializable

/**
 * The single saved car position: centroid, accuracy radius and the wall-clock time of the PARKED declaration.
 *
 * @requirement FR-015
 */
@Serializable
data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val declaredAtEpochMillis: Long,
) {
    init {
        require(accuracyMeters.isFinite() && accuracyMeters > 0) { "accuracyMeters must be finite and > 0" }
    }
}
