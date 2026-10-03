package com.packmuleforge.carfindermvp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A guidance update changes the drawn cone and distance in place: the screen and the guidance nodes are updated,
 * not torn down and rebuilt.
 *
 * @requirement QR-013
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class GuidanceRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    private val parked = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, CONVERGENCE_RADIUS_METERS / 2, 1L)

    private fun guidanceAt(southMeters: Double, headingDegrees: Double): HomeScreenState.Guidance {
        val fix = Readings.readingOffset(-southMeters, 0.0)
        val g = GuidanceCalculator.compute(parked, fix, HeadingReading(headingDegrees, 0L))
        return HomeScreenState.Guidance(g.cone, g.distanceText)
    }

    /** @requirement FR-046 */
    @Test
    fun aGuidanceUpdateUpdatesTheGuidanceDisplayInPlace() {
        val first = guidanceAt(120.0, 20.0)
        val second = guidanceAt(80.0, 35.0)
        var state: HomeScreenState by mutableStateOf(first)
        compose.setContent { HomeScreen(state, onArrivalAnswered = {}, onDenialConfirmed = {}, onDenialDismissed = {}) }
        val coneId = compose.onNodeWithTag(UiTags.GUIDANCE_CONE).fetchSemanticsNode().id
        val textId = compose.onNodeWithTag(UiTags.DISTANCE_TEXT).fetchSemanticsNode().id
        val displayId = compose.onNodeWithTag(UiTags.GUIDANCE_DISPLAY).fetchSemanticsNode().id

        state = second
        compose.waitForIdle()

        compose.onNodeWithTag(UiTags.GUIDANCE_CONE)
            .assert(SemanticsMatcher.expectValue(GuidanceSemantics.DisplayBearingDegrees, second.cone.displayBearingDegrees))
        compose.onNodeWithTag(UiTags.DISTANCE_TEXT).assertTextEquals(second.distanceText)
        assertEquals(coneId, compose.onNodeWithTag(UiTags.GUIDANCE_CONE).fetchSemanticsNode().id)
        assertEquals(textId, compose.onNodeWithTag(UiTags.DISTANCE_TEXT).fetchSemanticsNode().id)
        assertEquals(displayId, compose.onNodeWithTag(UiTags.GUIDANCE_DISPLAY).fetchSemanticsNode().id)
    }
}
