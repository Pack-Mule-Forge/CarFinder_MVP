package com.packmuleforge.carfindermvp

import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Robolectric application that swaps every platform adapter for a fake from :shared-testing.
 * Use with `@Config(application = TestCarFinderApplication::class)`.
 */
class TestCarFinderApplication : CarFinderApplication() {
    val fakes = FakePlatform()

    override fun createAdapters(): PlatformAdapters = fakes.adapters

    override val engineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
}
