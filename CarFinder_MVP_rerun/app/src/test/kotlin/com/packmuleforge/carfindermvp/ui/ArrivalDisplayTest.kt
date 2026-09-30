package com.packmuleforge.carfindermvp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometryCalculator
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The arrival view replaces the cone, icons and distance text (FR-028) and asks "Do you see your car?".
 * @requirement QR-003
 */
@RunWith(RobolectricTestRunner::class)
class ArrivalDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(halfAngle: Double, arrived: Boolean, prompt: Boolean) = HomeScreenState.Guidance(
        cone = ConeGeometryCalculator.compute(displayBearingDegrees = 0.0, halfAngleDegrees = halfAngle),
        distance = GuidanceCalculator.distanceDisplay(3.0),
        isArrived = arrived,
        isArrivalPromptVisible = prompt,
    )

    private val arrival = CarFinderConstants.ARRIVAL_HALF_ANGLE_DEGREES

    /** @requirement FR-028 */
    @Test
    fun atArrival_coneIconsAndDistanceAreReplacedByMessageAndPrompt() {
        compose.setContent { GuidanceDisplay(state(arrival, arrived = true, prompt = true), onArrivalAnswered = {}) }
        listOf(TestTags.GUIDANCE_CONE, TestTags.PERSON_ICON, TestTags.CAR_ICON, TestTags.DISTANCE_TEXT)
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onNodeWithTag(TestTags.ARRIVAL_MESSAGE).assertTextEquals("You have arrived")
        compose.onNodeWithTag(TestTags.ARRIVAL_PROMPT).assertTextEquals("Do you see your car?")
    }

    /** @requirement FR-029 */
    @Test
    fun yesAndNo_invokeCallbackWithTheAnswer() {
        val answers = mutableListOf<Boolean>()
        compose.setContent { GuidanceDisplay(state(arrival, arrived = true, prompt = true), onArrivalAnswered = { answers += it }) }
        compose.onNodeWithTag(TestTags.ARRIVAL_YES).performClick()
        compose.onNodeWithTag(TestTags.ARRIVAL_NO).performClick()
        assertEquals(listOf(true, false), answers)
    }

    /** @requirement FR-028, FR-029 */
    @Test
    fun arrivedWithPromptDismissed_showsMessageOnly() {
        compose.setContent { GuidanceDisplay(state(arrival, arrived = true, prompt = false), onArrivalAnswered = {}) }
        compose.onNodeWithTag(TestTags.ARRIVAL_MESSAGE).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.ARRIVAL_PROMPT).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.ARRIVAL_YES).assertDoesNotExist()
    }

    /** @requirement FR-028 */
    @Test
    fun justBelowArrival_coneShows_andArrivalDoesNot() {
        compose.setContent { GuidanceDisplay(state(arrival - 1, arrived = false, prompt = false), onArrivalAnswered = {}) }
        compose.onNodeWithTag(TestTags.GUIDANCE_CONE).assertExists()
        compose.onNodeWithTag(TestTags.ARRIVAL_MESSAGE).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.ARRIVAL_PROMPT).assertDoesNotExist()
    }
}
