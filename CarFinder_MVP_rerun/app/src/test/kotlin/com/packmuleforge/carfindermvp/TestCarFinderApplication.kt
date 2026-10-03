package com.packmuleforge.carfindermvp

import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The application under Robolectric: every adapter is a fake and the engine runs unconfined. */
open class TestCarFinderApplication : CarFinderApplication() {
    val platform = FakePlatform()

    override val engineDispatcher: CoroutineDispatcher get() = Dispatchers.Unconfined

    override fun createAdapters(): PlatformAdapters = platform.adapters
}
