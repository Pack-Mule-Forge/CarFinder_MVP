package com.packmuleforge.carfindermvp.shared.domain

import kotlinx.serialization.Serializable

/**
 * The single current saved car position: the centroid of the converging readings and its accuracy radius.
 *
 * [capturedAtEpochMillis] is the wall-clock time of the PARKED declaration. A recovery correction replaces the
 * position and accuracy but keeps this time, so the recovery window is never extended.
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

    /**
     * True while a new convergence may still correct this location. A wall clock that reads earlier than the
     * declaration counts as outside the window: keeping a premature location is safer than overwriting a good one.
     *
     * @requirement FR-035
     */
    fun isWithinRecoveryWindow(nowEpochMillis: Long): Boolean =
        nowEpochMillis - capturedAtEpochMillis in 0..CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
}
