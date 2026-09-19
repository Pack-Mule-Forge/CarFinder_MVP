package com.packmuleforge.carfinder_mvp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.platform.LocationRequestTier
import com.packmuleforge.carfinder.shared.state.ParkingStateMachine
import com.packmuleforge.carfinder_mvp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the parking state machine and runs parking detection continuously
 * in the background, independent of app visibility (FR-014, FR-010a).
 *
 * The service persists every state change and starts on device boot (via ServiceBootReceiver).
 * It feeds location samples from the LocationProvider to the state machine and adjusts location
 * tiers on state changes.
 */
@Requirement("FR-014", "FR-010a", "SC-011", "SC-012")
class ParkingDetectionService : Service() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var stateMachine: ParkingStateMachine
    private lateinit var locationProvider: com.packmuleforge.carfinder.shared.platform.LocationProvider

    override fun onCreate() {
        super.onCreate()

        // Initialize dependencies from the application's DI container
        val app = application as? com.packmuleforge.carfinder_mvp.CarFinderApplication
        stateMachine = app?.stateMachine ?: return
        locationProvider = app?.locationProvider ?: return

        // Create notification channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parking Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location monitoring for vehicle parking detection"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Start as foreground service with notification (FR-014 requires this on Android 8+)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Car Finder")
            .setContentText("Monitoring for parking")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Start location monitoring
        scope.launch {
            stateMachine.restore()
            startLocationMonitoring()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service should continue running even if app is killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun startLocationMonitoring() {
        scope.launch {
            // Start with DRIVING tier (high frequency, balanced accuracy)
            locationProvider.samples(LocationRequestTier.DRIVING).collect { sample ->
                stateMachine.onLocationSample(sample)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "parking_detection"
        private const val NOTIFICATION_ID = 1001
    }
}
