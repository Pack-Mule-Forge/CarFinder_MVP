package com.packmuleforge.carfindermvp

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.ui.UiTags
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-013 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class MainActivityObserverTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()
    private val location = ParkedLocation(
        Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS,
        FakeWallClock.DEFAULT_START_EPOCH_MILLIS - PARKED_RECOVERY_WINDOW_MILLIS * 10,
    )

    /** @requirement FR-043, FR-052 */
    @Test
    fun theActivityShowsTheRestoredStateAndStartsAndWritesNothing() {
        app.platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            assertEquals(LifecycleState.PARKED, app.engine.state.value.lifecycle)
            assertEquals(location, app.engine.state.value.parkedLocation)
            compose.onNodeWithTag(UiTags.STATUS_MESSAGE).assertTextEquals("Location unavailable")
            assertEquals(0, app.platform.location.startCount)
            assertFalse(app.platform.activity.isStarted)
            assertTrue(app.platform.store.writes.isEmpty())
            assertFalse(app.engine.isRunning)
        }
    }

    /** @requirement FR-027, FR-034 */
    @Test
    fun guidanceVisibilityFollowsTheActivitysStartedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            assertTrue(app.platform.heading.isStarted)
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(app.platform.heading.isStarted)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(app.platform.heading.isStarted)
        }
    }
}
