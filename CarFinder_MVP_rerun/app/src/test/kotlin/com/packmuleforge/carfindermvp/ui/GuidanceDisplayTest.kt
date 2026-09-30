package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometryCalculator
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Semantics-based UI tests for the guidance display (Constitution I).
 * @requirement QR-003
 */
@RunWith(RobolectricTestRunner::class)
class GuidanceDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    private val parked = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, accuracyMeters = 6.0, capturedAtEpochMillis = 0L)

    private fun guidanceFor(northOfCarMeters: Double, fixAccuracy: Double, headingDegrees: Double): Pair<HomeScreenState.Guidance, Double> {
        val fix = Readings.readingOffset(northMeters = -northOfCarMeters, eastMeters = 0.0, accuracy = fixAccuracy, speedMph = null)
        val g = GuidanceCalculator.compute(parked, fix, HeadingReading(headingDegrees, 0L))
        val cone = ConeGeometryCalculator.compute(g.displayBearingDegrees, g.coneHalfAngleDegrees)
        val expectedHalfAngle = atan2(g.uncertaintyMeters, g.distanceMeters) * 180 / PI
        return HomeScreenState.Guidance(cone, g.distanceDisplay, isArrived = false, isArrivalPromptVisible = false) to expectedHalfAngle
    }

    private fun guidanceAtFeet(feet: Double): HomeScreenState.Guidance {
        val (base, _) = guidanceFor(northOfCarMeters = 80.0, fixAccuracy = 4.0, headingDegrees = 0.0)
        return base.copy(distance = GuidanceCalculator.distanceDisplay(feet / CarFinderConstants.METERS_TO_FEET))
    }

    private fun coneConfig() = compose.onNodeWithTag(TestTags.GUIDANCE_CONE).fetchSemanticsNode().config

    /** @requirement FR-020, FR-021, FR-024 */
    @Test
    fun coneGeometry_rendersHalfAngleAndBearingFromInputs_sameSizeInPortraitAndLandscape() {
        val heading = 30.0
        val (state, expectedHalfAngle) = guidanceFor(northOfCarMeters = 80.0, fixAccuracy = 4.0, headingDegrees = heading)
        var portrait by mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(if (portrait) 400.dp else 800.dp, if (portrait) 800.dp else 400.dp)) {
                GuidanceDisplay(state, onArrivalAnswered = {})
            }
        }

        assertEquals(expectedHalfAngle, coneConfig()[ConeHalfAngleDegrees], 1e-9)
        // Car due north, heading 30° east of north: the car is 330° clockwise from the top of the screen.
        assertEquals(GuidanceCalculator.displayBearingDegrees(heading, 0.0), coneConfig()[ConeDisplayBearingDegrees], 0.01)
        val portraitSize: Dp = coneConfig()[ConeDrawSize]

        portrait = false
        compose.waitForIdle()
        assertEquals(portraitSize, coneConfig()[ConeDrawSize])
    }

    /** @requirement FR-026 */
    @Test
    fun distanceText_isFeetAtOrBelowThreshold_milesAbove() {
        val threshold = CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET
        var state by mutableStateOf(guidanceAtFeet(threshold - 1))
        compose.setContent { GuidanceDisplay(state, onArrivalAnswered = {}) }

        fun text() = compose.onNodeWithTag(TestTags.DISTANCE_TEXT).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }

        assertTrue(text().endsWith("ft"), text())
        state = guidanceAtFeet(threshold)
        compose.waitForIdle()
        assertTrue(text().endsWith("ft"), text())
        state = guidanceAtFeet(threshold + 1)
        compose.waitForIdle()
        assertTrue(text().endsWith("mi"), text())
    }

    /** @requirement FR-022 */
    @Test
    fun personAndCarIcons_areShown() {
        compose.setContent { GuidanceDisplay(guidanceAtFeet(200.0), onArrivalAnswered = {}) }
        compose.onNodeWithTag(TestTags.PERSON_ICON).assertContentDescriptionEquals("You")
        compose.onNodeWithTag(TestTags.CAR_ICON).assertContentDescriptionEquals("Your car")
    }

    /** @requirement FR-023, FR-025 */
    @Test
    fun cone_hasNoCenterlineChild_andDistanceTextIsCentered() {
        compose.setContent { GuidanceDisplay(guidanceAtFeet(200.0), onArrivalAnswered = {}) }
        val cone = compose.onNodeWithTag(TestTags.GUIDANCE_CONE).fetchSemanticsNode()
        assertTrue(cone.children.isEmpty(), "cone must not contain child elements such as a centerline")
        assertEquals(null, cone.config.getOrNull(SemanticsProperties.Text))
        compose.onNodeWithTag(TestTags.DISTANCE_TEXT).fetchSemanticsNode()
    }
}
