package com.packmuleforge.carfindermvp.shared.platform.android

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import com.packmuleforge.carfindermvp.shared.platform.ActivitySignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-vehicle hint: true from the platform's "entered vehicle" transition until its "exited vehicle"
 * transition. Without activity-recognition permission it registers nothing and stays false.
 *
 * @requirement FR-028, FR-049
 */
class ActivityTransitionSource(private val port: ActivityPort) : ActivitySignalSource {
    private val state = MutableStateFlow(false)
    override val isInVehicle: StateFlow<Boolean> = state.asStateFlow()
    private var isRegistered = false

    override fun start() {
        if (isRegistered || !port.hasActivityRecognitionPermission()) return
        port.register(::onTransition)
        isRegistered = true
    }

    override fun stop() {
        if (isRegistered) port.unregister()
        isRegistered = false
        state.value = false
    }

    private fun onTransition(event: ActivityTransitionEvent) {
        if (event.activityType != DetectedActivity.IN_VEHICLE) return
        when (event.transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> state.value = true
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> state.value = false
        }
    }
}
