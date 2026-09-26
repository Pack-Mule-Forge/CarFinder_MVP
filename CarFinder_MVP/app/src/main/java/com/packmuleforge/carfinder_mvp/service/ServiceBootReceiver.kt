package com.packmuleforge.carfinder_mvp.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Broadcast receiver that restarts ParkingDetectionService after device reboot,
 * ensuring that parking state and location persist across device restart (FR-011).
 *
 * FR-046 (T111/CR-5 fix): the service must only be restarted if location permission is still
 * granted after the reboot — otherwise it would hit the same SecurityException as the original
 * unconditional start in CarFinderApplication.onCreate(). Checked directly via
 * ContextCompat.checkSelfPermission (a plain Context, not a suspend PermissionController call) to
 * match AndroidPermissionController's own status check, which uses the same API.
 */
@Requirement("FR-011", "FR-046", "SC-002")
class ServiceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED || context == null) return

        val locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (locationGranted) {
            val serviceIntent = Intent(context, ParkingDetectionService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
