package com.packmuleforge.carfindermvp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.packmuleforge.carfindermvp.CarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability

/**
 * Restarts detection after a reboot only when background location and both required permissions are granted;
 * with foreground-only location detection resumes the next time the app is opened.
 *
 * @requirement FR-052, FR-053, FR-054
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val permissions = (context.applicationContext as CarFinderApplication).adapters.permissions
        permissions.refresh()
        val state = permissions.status.value
        if (state.areRequiredGranted && state.isGranted(Capability.BACKGROUND_LOCATION)) {
            context.startForegroundService(Intent(context, ParkingDetectionService::class.java))
        }
    }
}
