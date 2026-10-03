package com.packmuleforge.carfindermvp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.packmuleforge.carfindermvp.R

/**
 * The persistent detection notification. Without background location it asks for "Allow all the time" and
 * tapping it opens the app's permission settings.
 *
 * @requirement FR-051, FR-054
 */
object DetectionNotification {
    const val ID = 1
    private const val CHANNEL_ID = "parking_detection"

    fun build(context: Context, hasBackgroundLocation: Boolean): Notification {
        ensureChannel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setOngoing(true)
        if (hasBackgroundLocation) {
            builder.setContentText(context.getString(R.string.notification_monitoring))
        } else {
            val text = context.getString(R.string.notification_allow_all_the_time)
            val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(PendingIntent.getActivity(context, 0, settings, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
