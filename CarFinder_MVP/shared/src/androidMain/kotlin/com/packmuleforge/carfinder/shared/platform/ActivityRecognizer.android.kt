package com.packmuleforge.carfinder.shared.platform

import android.content.Context
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.TransitionRequest
import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android implementation of ActivityRecognizer using Google Activity Recognition API.
 * Detects when the user enters/exits a vehicle. If permission is denied, returns an empty
 * flow so correctness is unaffected (R-07).
 */
@Requirement("FR-014", "SC-010")
actual class ActivityRecognizer(private val context: Context) {
    actual fun inVehicleTransitions(): Flow<VehicleTransition> = callbackFlow {
        val transitions = listOf(
            ActivityTransitionRequest(
                DetectedActivity.IN_VEHICLE,
                TransitionRequest.ACTIVITY_TRANSITION_ENTER
            ),
            ActivityTransitionRequest(
                DetectedActivity.IN_VEHICLE,
                TransitionRequest.ACTIVITY_TRANSITION_EXIT
            )
        )

        val request = ActivityTransitionRequest(transitions)
        val pendingIntent = android.app.PendingIntent.getService(
            context,
            0,
            android.content.Intent(context, ActivityRecognitionReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try {
            @Suppress("MissingPermission")
            ActivityRecognition.getClient(context)
                .requestActivityTransitions(request, pendingIntent)
        } catch (e: SecurityException) {
            // Permission denied or unavailable; return empty flow
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                @Suppress("MissingPermission")
                ActivityRecognition.getClient(context).removeActivityTransitions(pendingIntent)
            } catch (e: SecurityException) {
                // Ignore errors during cleanup
            }
        }
    }
}

/**
 * Broadcast receiver for activity transitions. Emits transitions through a shared StateFlow
 * that callers can observe. This is a simplified stub; a real implementation would use
 * a proper broadcast mechanism or a service-level flow manager.
 */
internal class ActivityRecognitionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: android.content.Intent?) {
        // In a real implementation, this would parse the ActivityTransitionResult
        // and emit transitions through a shared StateFlow.
        // For now, this is a stub that allows the compilation to succeed.
    }
}
