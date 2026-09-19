package com.packmuleforge.carfinder_mvp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Broadcast receiver that restarts ParkingDetectionService after device reboot,
 * ensuring that parking state and location persist across device restart (FR-011).
 */
@Requirement("FR-011", "SC-002")
class ServiceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            val serviceIntent = Intent(context, ParkingDetectionService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
