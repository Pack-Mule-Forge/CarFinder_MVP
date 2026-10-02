package com.packmuleforge.carfindermvp.shared.domain

/**
 * One position sample from the platform location provider.
 *
 * @property accuracyMeters horizontal accuracy radius, or null if the provider gave none. Such readings are not
 *   usable for convergence or guidance, because uncertainty is never approximated.
 * @property speedMetersPerSecond ground speed, or null if the provider gave none. Such readings do not enter the
 *   speed filter.
 * @property elapsedRealtimeMillis monotonic time the fix was produced; used for currency checks (FR-034).
 * @property epochMillis wall-clock time of the fix.
 */
data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val elapsedRealtimeMillis: Long,
    val epochMillis: Long,
) {
    val hasAccuracy: Boolean get() = accuracyMeters != null

    val speedMph: Double? get() = speedMetersPerSecond?.let { it * CarFinderConstants.METERS_PER_SECOND_TO_MPH }
}
