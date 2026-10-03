package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.platform.android.AndroidDiagnosticLog
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidMonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidSensorPort
import com.packmuleforge.carfindermvp.shared.platform.android.RotationVectorHeadingSource
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidWallClock
import com.packmuleforge.carfindermvp.shared.platform.android.DataStoreParkingStore
import com.packmuleforge.carfindermvp.shared.platform.android.FusedLocationSource
import com.packmuleforge.carfindermvp.shared.platform.android.PlayServicesFusedClientPort
import com.packmuleforge.carfindermvp.shared.platform.android.StubPermissionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

actual typealias PlatformContext = android.content.Context

/** @requirement QR-014 */
actual fun createPlatformAdapters(context: PlatformContext): PlatformAdapters {
    val appContext = context.applicationContext
    val log = AndroidDiagnosticLog()
    val monotonicClock = AndroidMonotonicClock()
    val wallClock = AndroidWallClock()
    val location = FusedLocationSource(PlayServicesFusedClientPort(appContext), monotonicClock, log)
    return PlatformAdapters(
        location = location,
        heading = RotationVectorHeadingSource(AndroidSensorPort(appContext), monotonicClock, location::latestReading, wallClock),
        activity = InertActivitySignalSource(),
        store = DataStoreParkingStore(
            produceFile = { File(appContext.filesDir, "datastore/parking_record.json") },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            log = log,
        ),
        permissions = StubPermissionController(appContext),
        monotonicClock = monotonicClock,
        wallClock = wallClock,
        log = log,
    )
}
