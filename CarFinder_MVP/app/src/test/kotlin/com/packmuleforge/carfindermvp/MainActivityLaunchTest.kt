package com.packmuleforge.carfindermvp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.ui.UiTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Test d of contracts/guidance-ui.md: the default view appears on launch with no interaction.
 *
 * @requirement QR-004
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class MainActivityLaunchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()

    /** @requirement FR-043 */
    @Test
    fun d_onAFreshStoreTheActivityShowsLocationUnavailableWithNoInteraction() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithTag(UiTags.STATUS_MESSAGE).assertTextEquals("Location unavailable")
        }
    }

    /** @requirement FR-043, FR-044 */
    @Test
    fun d_onAParkedStoreWithAFixAndHeadingTheActivityShowsTheConeWithNoInteraction() {
        val car = ParkedLocation(
            Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS,
            FakeWallClock.DEFAULT_START_EPOCH_MILLIS - PARKED_RECOVERY_WINDOW_MILLIS * 10,
        )
        app.platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = car))
        app.engine.start()
        runBlocking { app.platform.location.emit(Readings.readingOffset(-150.0, 0.0, receivedElapsedMillis = 0)) }
        app.platform.heading.emit(HeadingReading(0.0, 0L))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithTag(UiTags.GUIDANCE_CONE).assertIsDisplayed()
            compose.onNodeWithTag(UiTags.PERSON_ICON).assertIsDisplayed()
            compose.onNodeWithTag(UiTags.CAR_ICON).assertIsDisplayed()
        }
    }
}
