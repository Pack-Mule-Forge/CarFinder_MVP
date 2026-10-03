package com.packmuleforge.carfindermvp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.MainActivity
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests c and f of contracts/guidance-ui.md.
 *
 * @requirement QR-004, QR-006
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class ArrivalDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    private var answers = 0

    private fun show(state: HomeScreenState) {
        var current by mutableStateOf(state)
        compose.setContent {
            HomeScreen(current, onArrivalAnswered = { answers++; current = HomeScreenState.Arrived(false) }, onDenialConfirmed = {}, onDenialDismissed = {})
        }
    }

    /** @requirement FR-038, FR-039 */
    @Test
    fun c_atArrivalTheMessageAndPromptAreShownAndTheConeIsNot() {
        show(HomeScreenState.Arrived(isPromptVisible = true))
        compose.onNodeWithTag(UiTags.ARRIVAL_MESSAGE).assertTextEquals("You have arrived")
        compose.onNodeWithTag(UiTags.ARRIVAL_PROMPT).assertIsDisplayed()
        for (tag in listOf(UiTags.GUIDANCE_CONE, UiTags.PERSON_ICON, UiTags.CAR_ICON, UiTags.DISTANCE_TEXT)) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    /** @requirement FR-039 */
    @Test
    fun f_tappingYesRemovesThePromptAndKeepsTheMessage() {
        show(HomeScreenState.Arrived(isPromptVisible = true))
        compose.onNodeWithTag(UiTags.ARRIVAL_YES).performClick()
        compose.onNodeWithTag(UiTags.ARRIVAL_PROMPT).assertDoesNotExist()
        compose.onNodeWithTag(UiTags.ARRIVAL_MESSAGE).assertTextEquals("You have arrived")
        assertEquals(1, answers)
    }

    /** @requirement FR-039 */
    @Test
    fun f_tappingNoRemovesThePromptAndKeepsTheMessage() {
        show(HomeScreenState.Arrived(isPromptVisible = true))
        compose.onNodeWithTag(UiTags.ARRIVAL_NO).performClick()
        compose.onNodeWithTag(UiTags.ARRIVAL_PROMPT).assertDoesNotExist()
        compose.onNodeWithTag(UiTags.ARRIVAL_MESSAGE).assertTextEquals("You have arrived")
        assertEquals(1, answers)
    }
}

/** The arrival prompt stays dismissed when the Activity is re-created for rotation (FR-039). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class ArrivalRotationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** @requirement FR-039, QR-006 */
    @Test
    fun afterDismissalRecreatingTheActivityDoesNotShowThePrompt() {
        val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()
        val platform = app.platform
        val car = ParkedLocation(
            Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS,
            FakeWallClock.DEFAULT_START_EPOCH_MILLIS - PARKED_RECOVERY_WINDOW_MILLIS * 10,
        )
        platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = car))
        app.engine.start()
        runBlocking { platform.location.emit(Readings.readingOffset(-1.0, 0.0, receivedElapsedMillis = 0)) }
        platform.heading.emit(HeadingReading(0.0, 0L))
        compose.waitForIdle()
        compose.onNodeWithTag(UiTags.ARRIVAL_PROMPT).assertIsDisplayed()
        compose.onNodeWithTag(UiTags.ARRIVAL_YES).performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(UiTags.ARRIVAL_MESSAGE).assertTextEquals("You have arrived")
        compose.onNodeWithTag(UiTags.ARRIVAL_PROMPT).assertDoesNotExist()
    }
}
