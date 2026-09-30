package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters

/** All fakes wired together, with typed access for tests. */
class FakePlatform(
    val location: FakeLocationSource = FakeLocationSource(),
    val heading: FakeHeadingSource = FakeHeadingSource(),
    val activity: FakeActivitySignalSource = FakeActivitySignalSource(),
    val store: InMemoryParkingStore = InMemoryParkingStore(),
    val monotonicClock: FakeMonotonicClock = FakeMonotonicClock(),
    val wallClock: FakeWallClock = FakeWallClock(),
    val permissions: FakePermissionController = FakePermissionController(),
) {
    val adapters = PlatformAdapters(location, heading, activity, store, monotonicClock, wallClock, permissions)
}
