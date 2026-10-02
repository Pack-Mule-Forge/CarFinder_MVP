package com.packmuleforge.carfindermvp.shared.platform.android

import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.packmuleforge.carfindermvp.shared.platform.ActivitySignalSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits when Activity Recognition reports entering a vehicle. This is only a sampling-rate hint; lifecycle
 * transitions are decided by filtered location speed alone (research R2). Any failure means no hints.
 *
 * @requirement FR-006
 */
class ActivityTransitionSource(private val port: ActivityPort) : ActivitySignalSource {

    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val inVehicleEntered: SharedFlow<Unit> = flow.asSharedFlow()

    override fun start() {
        try {
            port.requestInVehicleTransitions(::handle)
        } catch (e: SecurityException) {
            Log.w(TAG, "Activity recognition unavailable; continuing without rate hints", e)
        }
    }

    override fun stop() {
        port.removeUpdates()
    }

    private fun handle(intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val enteredVehicle = result.transitionEvents.any {
            it.activityType == DetectedActivity.IN_VEHICLE && it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        if (enteredVehicle) flow.tryEmit(Unit)
    }

    private companion object {
        const val TAG = "ActivityTransitionSource"
    }
}
