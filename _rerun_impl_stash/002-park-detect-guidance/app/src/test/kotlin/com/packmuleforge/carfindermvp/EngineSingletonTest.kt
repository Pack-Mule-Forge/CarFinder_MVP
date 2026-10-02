package com.packmuleforge.carfindermvp

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * MainActivity and ParkingDetectionService resolve to the identical ParkingEngine instance, so the engine the
 * service runs is the one the screen shows, rather than relying on `by lazy` and the single-process convention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class EngineSingletonTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    init {
        app.fakes.monotonicClock.now = 1_000_000L
        app.fakes.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
            ),
        )
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** @requirement FR-014, FR-033 */
    @Test
    fun activityAndService_shareOneEngine_andTheServiceStartedEngineFeedsTheScreen() {
        compose.waitForIdle()
        val activityEngine = (compose.activity.application as CarFinderApplication).engine
        assertFalse(activityEngine.isRunning)

        val service = Robolectric.buildService(ParkingDetectionService::class.java).create().get()
        service.onStartCommand(Intent(app, ParkingDetectionService::class.java), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        val serviceEngine = (service.application as CarFinderApplication).engine

        assertSame(activityEngine, serviceEngine, "Activity and service must share one ParkingEngine")
        assertTrue(activityEngine.isRunning, "starting the service started the engine the Activity observes")
        assertTrue(app.fakes.location.isStarted)

        // A fix delivered to the service-started engine reaches the Activity's screen.
        val now = app.fakes.monotonicClock.now
        runBlocking { app.fakes.location.emit(Readings.readingOffset(-60.0, 0.0, speedMph = null, elapsedMillis = now)) }
        app.fakes.heading.emit(HeadingReading(0.0, now))
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(TestTags.GUIDANCE_CONE)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(TestTags.GUIDANCE_CONE).assertExists()
    }
}
