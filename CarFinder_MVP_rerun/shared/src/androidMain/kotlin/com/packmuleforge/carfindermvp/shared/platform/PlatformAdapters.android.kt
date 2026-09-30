package com.packmuleforge.carfindermvp.shared.platform

import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidMonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidPermissionController
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidWallClock
import com.packmuleforge.carfindermvp.shared.platform.android.DataStoreParkingStore
import com.packmuleforge.carfindermvp.shared.platform.android.FusedLocationSource
import com.packmuleforge.carfindermvp.shared.platform.android.ActivityTransitionSource
import com.packmuleforge.carfindermvp.shared.platform.android.PlayServicesActivityPort
import com.packmuleforge.carfindermvp.shared.platform.android.PlayServicesFusedClientPort
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidSensorPort
import com.packmuleforge.carfindermvp.shared.platform.android.RotationVectorHeadingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

actual typealias PlatformContext = Context

/**
 * Wires the Android adapters behind the common interfaces.
 *
 * @requirement QR-010
 */
actual fun createPlatformAdapters(context: PlatformContext): PlatformAdapters {
    val app = context.applicationContext
    return PlatformAdapters(
        location = FusedLocationSource(PlayServicesFusedClientPort(app)),
        heading = RotationVectorHeadingSource(
            port = AndroidSensorPort(app.getSystemService(SensorManager::class.java)),
            displayRotation = {
                app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                    ?: Surface.ROTATION_0
            },
            clock = AndroidMonotonicClock,
            scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
        ),
        activity = ActivityTransitionSource(PlayServicesActivityPort(app)),
        store = DataStoreParkingStore.getInstance(app),
        monotonicClock = AndroidMonotonicClock,
        wallClock = AndroidWallClock,
        permissions = AndroidPermissionController(app),
    )
}
