package com.packmuleforge.carfindermvp.shared.domain

/**
 * Location request profile. The single platform subscription is re-issued whenever the profile changes; every
 * profile uses high accuracy so GNSS speed is available (research R2).
 *
 * @requirement FR-006
 */
enum class SamplingProfile(val intervalMillis: Long) {
    IDLE_WATCH(TuningConstants.IDLE_WATCH_SAMPLING_INTERVAL_MILLIS),
    DRIVING(TuningConstants.DRIVING_SAMPLING_INTERVAL_MILLIS),
    PARKING(CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS),
    GUIDANCE(TuningConstants.GUIDANCE_SAMPLING_INTERVAL_MILLIS),
}
