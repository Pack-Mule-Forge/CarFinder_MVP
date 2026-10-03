package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Every fake wired into one [PlatformAdapters]. All four capabilities start `GRANTED`. */
class FakePlatform(scheduler: TestCoroutineScheduler? = null) {
    val log = RecordingDiagnosticLog()
    val location = FakeLocationSource()
    val heading = FakeHeadingSource()
    val activity = FakeActivitySignalSource()
    val store = InMemoryParkingStore(log)
    val permissions = FakePermissionController(PermissionState.ALL_GRANTED)
    val monotonicClock = FakeMonotonicClock(scheduler)
    val wallClock = FakeWallClock(scheduler)

    val adapters = PlatformAdapters(
        location = location,
        heading = heading,
        activity = activity,
        store = store,
        permissions = permissions,
        monotonicClock = monotonicClock,
        wallClock = wallClock,
        log = log,
    )
}
