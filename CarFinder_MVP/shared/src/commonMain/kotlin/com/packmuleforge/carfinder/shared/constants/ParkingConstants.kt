package com.packmuleforge.carfinder.shared.constants

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.math.PI

/**
 * FR-034: all tunable thresholds named here, single-sourced, rather than inlined as literals.
 * FR-037: automated tests reference these constants rather than their literal values.
 */
@Requirement("FR-034", "FR-037")
object ParkingConstants {

    // ========== Parking State Thresholds (FR-034) ==========

    const val PARKING_SPEED_THRESHOLD_MPH = 5.0
    val PARKING_SPEED_THRESHOLD_MPS = 5.0 * 0.44704  // 2.2352 m/s

    const val DRIVING_SPEED_THRESHOLD_MPH = 25.0
    val DRIVING_SPEED_THRESHOLD_MPS = 25.0 * 0.44704  // 11.176 m/s

    // ========== Convergence Tuning (FR-034) ==========

    const val CONVERGENCE_RADIUS_METERS = 10.0
    const val CONVERGENCE_SAMPLE_COUNT = 3

    // ========== Sampling Intervals (FR-034) ==========

    const val PARKING_SAMPLE_INTERVAL_MILLIS = 5_000L

    // ========== Arrival Detection (FR-034) ==========

    const val ARRIVAL_CONE_HALF_ANGLE_DEGREES = 45.0
    val ARRIVAL_CONE_HALF_ANGLE_RADIANS = 45.0 * (PI / 180.0)  // π/4 rad

    // ========== Distance Units (FR-034) ==========

    const val DISTANCE_UNIT_THRESHOLD_FEET = 500.0
    val DISTANCE_UNIT_THRESHOLD_METERS = 500.0 * 0.3048  // 152.4 m

    // ========== Fix Staleness (FR-034, FR-043) ==========

    const val FIX_STALENESS_TIMEOUT_MILLIS = 30_000L

    init {
        // FR-004: Dead zone is only well-defined if thresholds are ordered
        require(PARKING_SPEED_THRESHOLD_MPS < DRIVING_SPEED_THRESHOLD_MPS) {
            "PARKING_SPEED_THRESHOLD must be less than DRIVING_SPEED_THRESHOLD"
        }
    }
}
