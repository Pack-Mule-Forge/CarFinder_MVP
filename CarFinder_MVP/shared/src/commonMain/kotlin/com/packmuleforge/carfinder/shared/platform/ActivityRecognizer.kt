package com.packmuleforge.carfinder.shared.platform

import kotlinx.coroutines.flow.Flow

/**
 * Vehicle state transitions detected by the platform's activity recognition API.
 * Used to start/stop the location provider efficiently (FR-010a).
 */
enum class VehicleTransition {
    ENTERED_VEHICLE, EXITED_VEHICLE
}

/**
 * Platform abstraction for activity recognition (vehicle entry/exit detection). Yields a
 * continuous Flow of VehicleTransition events. Updates cease when the consumer unsubscribes.
 *
 * On platforms that do not support activity recognition or where permission is denied,
 * implementations should return an empty flow (no transitions) so correctness is unaffected.
 */
expect class ActivityRecognizer {
    fun inVehicleTransitions(): Flow<VehicleTransition>
}
