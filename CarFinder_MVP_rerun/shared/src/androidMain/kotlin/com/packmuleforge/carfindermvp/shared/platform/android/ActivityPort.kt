package com.packmuleforge.carfindermvp.shared.platform.android

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/** Thin seam over the Activity Recognition Transition API so the source can be tested with a fake. */
interface ActivityPort {
    /** @throws SecurityException if ACTIVITY_RECOGNITION is not granted. */
    fun requestInVehicleTransitions(onIntent: (Intent) -> Unit)

    fun removeUpdates()
}

/**
 * Delivers transition results through a context-registered, non-exported receiver. The foreground service keeps
 * the process alive, so a manifest receiver is not needed, and results stay private to this app.
 */
internal class PlayServicesActivityPort(private val context: Context) : ActivityPort {
    private val client = ActivityRecognition.getClient(context)
    private var receiver: BroadcastReceiver? = null
    private var pendingIntent: PendingIntent? = null

    // ACTIVITY_RECOGNITION absence surfaces as a SecurityException, which the caller handles.
    @SuppressLint("MissingPermission")
    override fun requestInVehicleTransitions(onIntent: (Intent) -> Unit) {
        removeUpdates()
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onIntent(intent)
        }
        ContextCompat.registerReceiver(context, newReceiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = newReceiver
        // Mutable: Play Services fills in the transition result extras.
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        pendingIntent = intent
        val request = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
            ),
        )
        client.requestActivityTransitionUpdates(request, intent)
    }

    @SuppressLint("MissingPermission")
    override fun removeUpdates() {
        pendingIntent?.let { client.removeActivityTransitionUpdates(it) }
        pendingIntent = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private companion object {
        const val ACTION = "com.packmuleforge.carfindermvp.ACTIVITY_TRANSITION"
    }
}
