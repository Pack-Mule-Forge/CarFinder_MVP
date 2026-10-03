package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS

/**
 * FR-027's sampling table and FR-028's in-vehicle hint, first matching row, and nothing else.
 *
 * @requirement FR-027, FR-028
 */
object SamplingPolicy {
    fun intervalFor(
        lifecycle: LifecycleState,
        isRecoveryOpen: Boolean,
        isGuidanceVisible: Boolean,
        isInVehicle: Boolean,
    ): Long = when {
        lifecycle == LifecycleState.DRIVING || lifecycle == LifecycleState.PARKING -> SAMPLING_INTERVAL_PARKING_MILLIS
        lifecycle == LifecycleState.PARKED && isRecoveryOpen -> SAMPLING_INTERVAL_PARKING_MILLIS
        lifecycle == LifecycleState.PARKED && isGuidanceVisible -> SAMPLING_INTERVAL_GUIDANCE_MILLIS
        isInVehicle -> SAMPLING_INTERVAL_PARKING_MILLIS
        else -> SAMPLING_INTERVAL_IDLE_MILLIS
    }
}
