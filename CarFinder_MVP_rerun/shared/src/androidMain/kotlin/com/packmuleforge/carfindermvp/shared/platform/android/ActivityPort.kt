package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** One activity transition, as the platform reports it. */
data class ActivityTransitionEvent(val activityType: Int, val transitionType: Int)

/** A thin seam over activity recognition so host tests drive [ActivityTransitionSource] with a test double. */
interface ActivityPort {
    fun hasActivityRecognitionPermission(): Boolean
    fun register(onTransition: (ActivityTransitionEvent) -> Unit)
    fun unregister()
}

internal class PlayServicesActivityPort(private val context: Context) : ActivityPort {
    private val client = ActivityRecognition.getClient(context)
    private val action = "${context.packageName}.ACTIVITY_TRANSITION"
    private var receiver: BroadcastReceiver? = null
    private var pendingIntent: PendingIntent? = null

    override fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    // The permission is checked by ActivityTransitionSource before registering.
    @SuppressLint("MissingPermission")
    override fun register(onTransition: (ActivityTransitionEvent) -> Unit) {
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!ActivityTransitionResult.hasResult(intent)) return
                ActivityTransitionResult.extractResult(intent)?.transitionEvents?.forEach {
                    onTransition(ActivityTransitionEvent(it.activityType, it.transitionType))
                }
            }
        }
        // The PendingIntent broadcast is sent with this app's identity, so the receiver need not be exported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(newReceiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(newReceiver, IntentFilter(action))
        }
        receiver = newReceiver
        // Mutable because the platform adds the transition result as extras.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        pendingIntent = intent
        val transitions = listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .map { ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(it).build() }
        try {
            client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), intent)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the request; the hint simply stays off.
        }
    }

    @SuppressLint("MissingPermission")
    override fun unregister() {
        pendingIntent?.let {
            try {
                client.removeActivityTransitionUpdates(it)
            } catch (_: SecurityException) {
                // Nothing to remove without permission.
            }
        }
        receiver?.let(context::unregisterReceiver)
        pendingIntent = null
        receiver = null
    }
}
