package com.packmuleforge.carfindermvp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * The three status views use exactly the spec's wording, capitalization and punctuation (FR-016).
 * @requirement QR-003
 */
@RunWith(RobolectricTestRunner::class)
class StatusMessagesTest {

    @get:Rule
    val compose = createComposeRule()

    /** @requirement FR-016, FR-017 */
    @Test
    fun eachStatusState_showsItsExactMessage_andNoCone() {
        val expected = listOf(
            HomeScreenState.Driving to "Driving - Waiting to Park.",
            HomeScreenState.Unavailable to "Parked location unavailable.",
            HomeScreenState.Parking to "Sensing you will be Parking Soon.",
        )
        var state by mutableStateOf<HomeScreenState>(expected.first().first)
        compose.setContent { HomeScreen(state, onArrivalAnswered = {}) }

        for ((screenState, text) in expected) {
            state = screenState
            compose.waitForIdle()
            compose.onNodeWithTag(TestTags.STATUS_MESSAGE).assertTextEquals(text)
            compose.onNodeWithTag(TestTags.GUIDANCE_CONE).assertDoesNotExist()
        }
    }
}
