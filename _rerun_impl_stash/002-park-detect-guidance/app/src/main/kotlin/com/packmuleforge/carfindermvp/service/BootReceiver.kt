package com.packmuleforge.carfindermvp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.packmuleforge.carfindermvp.CarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus

/**
 * Restarts detection after a reboot or app update. A location foreground service started from the background
 * only gets location access with background location granted, so both permissions are required (research R5).
 *
 * @requirement FR-033
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTART_ACTIONS) return
        val app = context.applicationContext as CarFinderApplication
        val status = app.adapters.permissions.status.value
        val canRestart = status[Capability.LOCATION_FOREGROUND] == CapabilityStatus.GRANTED &&
            status[Capability.LOCATION_BACKGROUND] == CapabilityStatus.GRANTED
        if (canRestart) {
            ContextCompat.startForegroundService(context, Intent(context, ParkingDetectionService::class.java))
        }
    }

    private companion object {
        val RESTART_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
