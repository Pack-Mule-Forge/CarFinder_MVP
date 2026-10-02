package com.packmuleforge.carfindermvp.shared.domain

/** Plan-level tuning knobs. Unlike [CarFinderConstants] these are not spec values; see research.md. */
object TuningConstants {
    /** Location interval in FINDING, and in PARKED while guidance is not visible (research R2). */
    const val IDLE_WATCH_SAMPLING_INTERVAL_MILLIS = 20_000L

    /** Location interval in DRIVING (research R2). */
    const val DRIVING_SAMPLING_INTERVAL_MILLIS = 5_000L

    /** Location interval while the guidance display is visible (research R2, SC-008). */
    const val GUIDANCE_SAMPLING_INTERVAL_MILLIS = 1_000L

    /**
     * Minimum time between readings used for PARKED recovery (FR-035). Below the parking-sampling interval so a
     * reading delivered slightly early is still used, and far above the guidance interval so guidance-rate
     * readings are thinned: three readings a second apart converge at any walking speed.
     */
    const val RECOVERY_MIN_SAMPLE_SPACING_MILLIS = CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS * 4 / 5

    /** Presenter tick that re-checks fix and heading currency without a new event (research R10, SC-010). */
    const val AVAILABILITY_RECHECK_INTERVAL_MILLIS = 500L

    /**
     * Cone length as a fraction of the minimum display dimension (research R10). The sector's farthest point from
     * the screen center is sqrt(1.25 - cos(half-angle)) x this length, about 0.737x at the 45-degree arrival angle,
     * so the value must stay at or below 0.678 for the whole cone to fit inside the minimum-dimension square.
     */
    const val CONE_LENGTH_FRACTION = 0.65
}
