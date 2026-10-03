package com.packmuleforge.carfindermvp

import android.os.Looper
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.robolectric.Shadows.shadowOf

/**
 * The application under Robolectric: every adapter is a fake, and the engine and presenter run unconfined on a
 * virtual clock that tests advance with [advanceTimeBy].
 */
open class TestCarFinderApplication : CarFinderApplication() {
    val scheduler = TestCoroutineScheduler()
    val platform = FakePlatform(scheduler)

    override val engineDispatcher: CoroutineDispatcher by lazy { UnconfinedTestDispatcher(scheduler) }

    override fun createAdapters(): PlatformAdapters = platform.adapters

    /** Advances virtual time, then lets the main looper deliver anything that resulted. */
    fun advanceTimeBy(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
