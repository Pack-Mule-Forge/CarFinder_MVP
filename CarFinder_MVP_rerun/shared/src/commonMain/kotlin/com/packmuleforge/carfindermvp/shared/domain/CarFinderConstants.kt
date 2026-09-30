package com.packmuleforge.carfindermvp.shared.domain

/**
 * The spec's named, configurable constants. Each value is defined only here, and code and tests reference the
 * name, never the literal (Constitution I).
 *
 * @requirement FR-030
 */
object CarFinderConstants {
    /** Filtered speed at or below which DRIVING becomes PARKING (FR-004). @requirement FR-030 */
    const val PARKING_SPEED_THRESHOLD_MPH = 5.0

    /** Filtered speed above which any state becomes DRIVING (FR-003). @requirement FR-030 */
    const val DRIVING_SPEED_THRESHOLD_MPH = 25.0

    /** Maximum pairwise distance between readings for convergence (FR-007). @requirement FR-030 */
    const val CONVERGENCE_RADIUS_METERS = 10.0

    /** Number of consecutive readings that must converge (FR-007). @requirement FR-030 */
    const val CONVERGENCE_SAMPLE_COUNT = 3

    /** Location sampling interval while PARKING (FR-006). @requirement FR-030 */
    const val PARKING_SAMPLING_INTERVAL_MILLIS = 5_000L

    /** Cone half-angle at or above which arrival is shown (FR-028). @requirement FR-030 */
    const val ARRIVAL_HALF_ANGLE_DEGREES = 45.0

    /** Distances at or below this are shown in feet, above it in miles (FR-026). @requirement FR-030 */
    const val DISTANCE_UNIT_THRESHOLD_FEET = 500.0

    /** Number of speed samples in the rolling median filter (FR-032). @requirement FR-030 */
    const val SPEED_FILTER_WINDOW_SIZE = 3

    /** A live fix older than this is not current (FR-034). @requirement FR-030 */
    const val FIX_STALENESS_TIMEOUT_MILLIS = 30_000L

    /** A heading with no sensor event for this long is unavailable (FR-031). @requirement FR-030 */
    const val HEADING_STALENESS_TIMEOUT_MILLIS = 2_000L

    /** Unit conversion: 1 m/s = 2.2369362920544 mph (exact from 1609.344 m per mile). */
    const val METERS_PER_SECOND_TO_MPH = 3_600.0 / 1_609.344

    /** Unit conversion: 1 m = 3.280839895 ft (exact from 0.3048 m per foot). */
    const val METERS_TO_FEET = 1.0 / 0.3048

    /** Unit conversion: feet in one statute mile. */
    const val FEET_PER_MILE = 5_280.0
}
