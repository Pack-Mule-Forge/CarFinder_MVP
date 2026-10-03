package com.packmuleforge.carfindermvp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Test g of contracts/guidance-ui.md.
 *
 * @requirement QR-004, QR-006
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class StatusMessagesTest {

    @get:Rule
    val compose = createComposeRule()

    /** @requirement FR-042 */
    @Test
    fun g_eachStatusStateShowsExactlyItsTextAndNoCone() {
        var state: HomeScreenState by mutableStateOf(HomeScreenState.Unavailable)
        compose.setContent { HomeScreen(state, onArrivalAnswered = {}, onDenialConfirmed = {}, onDenialDismissed = {}) }
        val expected = listOf(
            HomeScreenState.Unavailable to "Location unavailable",
            HomeScreenState.Driving to "Driving",
            HomeScreenState.Parking to "Sensing you will be parking soon",
        )
        for ((status, text) in expected) {
            state = status
            compose.waitForIdle()
            compose.onNodeWithTag(UiTags.STATUS_MESSAGE).assertTextEquals(text)
            compose.onNodeWithTag(UiTags.GUIDANCE_CONE).assertDoesNotExist()
            compose.onNodeWithTag(UiTags.DISTANCE_TEXT).assertDoesNotExist()
        }
    }
}
