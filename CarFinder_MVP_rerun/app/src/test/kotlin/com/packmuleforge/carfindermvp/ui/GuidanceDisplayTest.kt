package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FEET_PER_METER
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceState
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests a, b, e and h of contracts/guidance-ui.md.
 *
 * @requirement QR-004, QR-006
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class GuidanceDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    private val parked = ParkedLocation(
        Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, CONVERGENCE_RADIUS_METERS / 2, 1L,
    )

    private fun guidance(southMeters: Double, headingDegrees: Double = 20.0): GuidanceState {
        val fix = Readings.readingOffset(-southMeters, southMeters / 3, accuracyMeters = CONVERGENCE_RADIUS_METERS / 4)
        return GuidanceCalculator.compute(parked, fix, HeadingReading(headingDegrees, 0L))
    }

    private fun state(g: GuidanceState) = HomeScreenState.Guidance(g.cone, g.distanceText)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun center(node: SemanticsNode) = node.boundsInRoot.center

    private fun assertNear(expected: Offset, actual: Offset, what: String) {
        assertTrue(abs(expected.x - actual.x) <= 1f && abs(expected.y - actual.y) <= 1f, "$what: $expected vs $actual")
    }

    /** @requirement FR-031 */
    @Test
    fun a_theDrawnHalfAngleMatchesTheFormula() {
        val g = guidance(CONVERGENCE_RADIUS_METERS * 8)
        compose.setContent { GuidanceDisplay(state(g)) }
        val expected = GuidanceCalculator.coneHalfAngleDegrees(g.uncertaintyMeters, g.distanceMeters)
        compose.onNodeWithTag(UiTags.GUIDANCE_CONE)
            .assert(SemanticsMatcher.expectValue(GuidanceSemantics.HalfAngleDegrees, expected))
            .assert(SemanticsMatcher.expectValue(GuidanceSemantics.DisplayBearingDegrees, g.displayBearingDegrees))
    }

    /** @requirement FR-037 */
    @Test
    fun b_theDistanceIsInFeetAtTheThresholdAndInMilesJustAboveIt() {
        var shown by mutableStateOf(GuidanceCalculator.distanceText(DISTANCE_UNIT_THRESHOLD_FEET / FEET_PER_METER))
        val g = guidance(CONVERGENCE_RADIUS_METERS * 8)
        compose.setContent { GuidanceDisplay(HomeScreenState.Guidance(g.cone, shown)) }
        compose.onNodeWithTag(UiTags.DISTANCE_TEXT).assertTextEquals("${DISTANCE_UNIT_THRESHOLD_FEET.toLong()} ft")
        shown = GuidanceCalculator.distanceText((DISTANCE_UNIT_THRESHOLD_FEET + 1) / FEET_PER_METER)
        compose.waitForIdle()
        val text = node(UiTags.DISTANCE_TEXT).config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertTrue(Regex("""^\d+\.\d{2} mi$""").matches(text), text)
    }

    /** @requirement FR-035 */
    @Test
    fun e_theIconsSitAtTheTwoAnchorsAndNoCenterlineIsDrawn() {
        val g = guidance(CONVERGENCE_RADIUS_METERS * 8, headingDegrees = 77.0)
        compose.setContent { Box(Modifier.size(300.dp, 600.dp)) { GuidanceDisplay(state(g)) } }
        val display = node(UiTags.GUIDANCE_DISPLAY).boundsInRoot
        val scale = minOf(display.width, display.height)
        val origin = display.center
        val apex = Offset(origin.x + (g.cone.apex.x * scale).toFloat(), origin.y - (g.cone.apex.y * scale).toFloat())
        val car = Offset(origin.x + (g.cone.carAnchor.x * scale).toFloat(), origin.y - (g.cone.carAnchor.y * scale).toFloat())
        assertNear(apex, center(node(UiTags.PERSON_ICON)), "person icon")
        assertNear(car, center(node(UiTags.CAR_ICON)), "car icon")
        compose.onNodeWithTag(UiTags.GUIDANCE_CONE)
            .assert(SemanticsMatcher.expectValue(GuidanceSemantics.IsCenterlineDrawn, false))
    }

    /** @requirement FR-036, FR-046 */
    @Test
    fun h_theConeKeepsItsSizeAndCenterInPortraitAndLandscape() {
        val g = guidance(CONVERGENCE_RADIUS_METERS * 8, headingDegrees = 140.0)
        var landscape by mutableStateOf(false)
        compose.setContent {
            val size = if (landscape) Modifier.size(600.dp, 300.dp) else Modifier.size(300.dp, 600.dp)
            Box(size) { GuidanceDisplay(state(g)) }
        }
        fun measure(): Pair<Offset, Offset> {
            val origin = node(UiTags.GUIDANCE_DISPLAY).boundsInRoot.center
            return (center(node(UiTags.PERSON_ICON)) - origin) to (center(node(UiTags.CAR_ICON)) - origin)
        }
        val portrait = measure()
        landscape = true
        compose.waitForIdle()
        val wide = measure()
        assertNear(portrait.first, wide.first, "person offset from center")
        assertNear(portrait.second, wide.second, "car offset from center")
    }
}
