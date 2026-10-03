package com.packmuleforge.carfindermvp.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import com.packmuleforge.carfindermvp.CarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability

/**
 * The location-type foreground service that owns detection; the only caller of the engine's `start()`. It checks
 * the required permissions itself on every start command, including the sticky restart with no intent.
 *
 * @requirement FR-049, FR-050, FR-051, FR-052
 */
class ParkingDetectionService : LifecycleService() {

    private val app: CarFinderApplication get() = application as CarFinderApplication

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val permissions = app.adapters.permissions
        permissions.refresh()
        val state = permissions.status.value
        if (!state.areRequiredGranted) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startInForeground(DetectionNotification.build(this, state.isGranted(Capability.BACKGROUND_LOCATION)))
        } catch (_: RuntimeException) {
            // The platform refused a foreground start (permission withdrawn, or a background start not allowed).
            stopSelf()
            return START_NOT_STICKY
        }
        app.engine.start()
        return START_STICKY
    }

    override fun onDestroy() {
        app.engine.stop()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DetectionNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(DetectionNotification.ID, notification)
        }
    }
}
