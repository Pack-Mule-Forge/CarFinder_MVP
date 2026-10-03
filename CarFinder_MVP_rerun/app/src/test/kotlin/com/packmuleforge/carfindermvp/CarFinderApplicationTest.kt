package com.packmuleforge.carfindermvp

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class CarFinderApplicationTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()

    /** @requirement FR-029 */
    @Test
    fun theApplicationHoldsOneEngineBuiltFromItsAdapters() {
        assertSame(app.engine, app.engine)
        assertSame(app.platform.adapters, app.adapters)
    }

    /** @requirement FR-052 */
    @Test
    fun creatingTheApplicationStartsNoSensing() {
        app.engine
        assertFalse(app.engine.isRunning)
        assertFalse(app.platform.location.isStarted)
    }
}
