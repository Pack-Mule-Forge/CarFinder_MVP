package com.packmuleforge.carfindermvp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.packmuleforge.carfindermvp.MainActivity
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus

/**
 * The persistent notification that accompanies always-on detection. Without background location it explains
 * that "Allow all the time" is needed and opens the app's settings, because detection cannot restart after a
 * reboot without it (spec Edge Cases).
 *
 * @requirement FR-016, FR-033
 */
object DetectionNotification {
    const val CHANNEL_ID = "parking_detection"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        lifecycle: LifecycleState,
        permissions: Map<Capability, CapabilityStatus>,
    ): Notification {
        val needsBackground = permissions[Capability.LOCATION_BACKGROUND] != CapabilityStatus.GRANTED
        val text = context.getString(if (needsBackground) R.string.notification_needs_background else textFor(lifecycle))
        val tapIntent = if (needsBackground) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Reuses the home screen's status wording (FR-016) so the notification and the app always agree. */
    private fun textFor(lifecycle: LifecycleState): Int = when (lifecycle) {
        LifecycleState.FINDING -> R.string.status_unavailable
        LifecycleState.DRIVING -> R.string.status_driving
        LifecycleState.PARKING -> R.string.status_parking
        LifecycleState.PARKED -> R.string.notification_parked
    }
}
