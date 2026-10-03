package com.packmuleforge.carfindermvp.shared.domain

/**
 * One position sample.
 *
 * @property accuracyMeters `null` when the platform reported none; never `0.0` for absent.
 * @property speedMetersPerSecond `null` when the platform reported none; never `0.0` for absent.
 * @property receivedElapsedMillis monotonic receipt time, used for fix age and recovery thinning.
 * @requirement FR-009, FR-010
 */
data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val receivedElapsedMillis: Long,
) {
    val speedMph: Double? get() = speedMetersPerSecond?.let { it * CarFinderConstants.MPH_PER_METER_PER_SECOND }
}
