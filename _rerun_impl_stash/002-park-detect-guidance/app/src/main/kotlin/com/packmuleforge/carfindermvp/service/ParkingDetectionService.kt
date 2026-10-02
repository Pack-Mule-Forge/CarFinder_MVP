package com.packmuleforge.carfindermvp.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.packmuleforge.carfindermvp.CarFinderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Location-type foreground service that keeps the parking engine running in every state, whether or not the app
 * is open, with a persistent notification (FR-033, research R5).
 *
 * @requirement FR-033
 */
class ParkingDetectionService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as CarFinderApplication
        DetectionNotification.ensureChannel(this)
        val notification = DetectionNotification.build(
            this,
            app.engine.state.value.lifecycle,
            app.adapters.permissions.status.value,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, DetectionNotification.NOTIFICATION_ID, notification, type)

        // The only production call site of engine.start() (enforced by EngineStartOwnershipScanTest).
        app.engine.start()
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { observe(it, app) }
        }
        return START_STICKY
    }

    private fun observe(scope: CoroutineScope, app: CarFinderApplication) {
        val manager = getSystemService(NotificationManager::class.java)
        scope.launch {
            combine(
                app.engine.state.map { it.lifecycle }.distinctUntilChanged(),
                app.adapters.permissions.status,
            ) { lifecycle, permissions -> DetectionNotification.build(this@ParkingDetectionService, lifecycle, permissions) }
                .collect { manager.notify(DetectionNotification.NOTIFICATION_ID, it) }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
