package com.packmuleforge.carfindermvp.shared.domain

/**
 * The FR-055 named constants. Each value is defined only here; code and tests refer to it by name (QR-005).
 *
 * @requirement FR-055
 */
object CarFinderConstants {
    const val PARKING_SPEED_THRESHOLD_MPH = 5.0
    const val DRIVING_SPEED_THRESHOLD_MPH = 25.0
    const val CONVERGENCE_RADIUS_METERS = 10.0
    const val CONVERGENCE_SAMPLE_COUNT = 3
    const val SAMPLING_INTERVAL_PARKING_MILLIS = 5_000L
    const val SAMPLING_INTERVAL_GUIDANCE_MILLIS = 1_000L
    const val SAMPLING_INTERVAL_IDLE_MILLIS = 20_000L
    const val ARRIVAL_CONE_HALF_ANGLE_DEGREES = 45.0
    const val DISTANCE_UNIT_THRESHOLD_FEET = 500.0
    const val FIX_STALENESS_TIMEOUT_MILLIS = 30_000L
    const val HEADING_STALENESS_TIMEOUT_MILLIS = 2_000L
    const val PARKED_RECOVERY_WINDOW_MILLIS = 180_000L
    const val TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS = 2_000L
    const val CONE_CONTAINMENT_TARGET = 0.90
    const val SPEED_FILTER_WINDOW_SIZE = 3
    const val AVAILABILITY_RECHECK_INTERVAL_MILLIS = 500L
    const val CONE_LENGTH_FRACTION = 0.65
    const val SHUTDOWN_NOTICE_DURATION_MILLIS = 2_000L

    // Unit conversions; not spec constants.
    const val MPH_PER_METER_PER_SECOND = 2.236936292054402
    const val FEET_PER_METER = 3.280839895013123
    const val FEET_PER_MILE = 5_280.0
}
