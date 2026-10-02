package com.packmuleforge.carfindermvp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lifecycle side effects live outside composition (Constitution IV): the observer toggles guidance sampling and
 * the compass, and the Activity copies presenter state into the pure HomeScreen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class GuidanceSessionObserverTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    init {
        // Seed before the rule launches MainActivity.
        app.fakes.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
            ),
        )
        app.fakes.monotonicClock.now = 1_000_000L
        // Long parked: the recovery window (FR-035), which holds the PARKING rate, has lapsed.
        app.fakes.wallClock.now = CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS + 1
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** The Activity only restores; detection is started by ParkingDetectionService, simulated here. */
    private fun startDetectionAsTheServiceWould() {
        compose.waitForIdle()
        app.engine.start()
        compose.waitForIdle()
    }

    /** @requirement FR-017 */
    @Test
    fun started_turnsOnGuidanceProfileAndCompass_stopped_turnsThemOff() {
        startDetectionAsTheServiceWould()
        assertTrue(app.fakes.heading.started)
        assertEquals(SamplingProfile.GUIDANCE, app.fakes.location.profileHistory.last())

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        assertTrue(!app.fakes.heading.started)
        assertTrue(app.fakes.heading.stopCount >= 1)
        assertEquals(SamplingProfile.IDLE_WATCH, app.fakes.location.profileHistory.last())
    }

    /** @requirement FR-017 */
    @Test
    fun presenterState_reachesHomeScreen_whileStarted() {
        startDetectionAsTheServiceWould()
        val now = app.fakes.monotonicClock.now
        runBlocking {
            app.fakes.location.emit(Readings.readingOffset(-60.0, 0.0, speedMph = null, elapsedMillis = now))
        }
        app.fakes.heading.emit(HeadingReading(0.0, now))
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTagCount(TestTags.DISTANCE_TEXT) > 0
        }
        compose.onNodeWithTag(TestTags.GUIDANCE_CONE).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
