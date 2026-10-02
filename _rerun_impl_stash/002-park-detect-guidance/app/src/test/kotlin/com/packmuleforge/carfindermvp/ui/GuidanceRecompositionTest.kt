package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometryCalculator
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A guidance update redraws only the guidance display (FR-027). Frame timing itself is measured by the
 * :benchmark module on a device.
 */
@RunWith(RobolectricTestRunner::class)
class GuidanceRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    private var outsideCompositions = 0
    private var guidanceCompositions = 0

    @Composable
    private fun OutsideGuidance() {
        SideEffect { outsideCompositions++ }
        Text("static sibling")
    }

    @Composable
    private fun CountingHomeScreen(state: HomeScreenState) {
        SideEffect { guidanceCompositions++ }
        HomeScreen(state, onArrivalAnswered = {})
    }

    private fun guidance(bearing: Double) = HomeScreenState.Guidance(
        cone = ConeGeometryCalculator.compute(bearing, halfAngleDegrees = 12.0),
        distance = GuidanceCalculator.distanceDisplay(100.0),
        isArrived = false,
        isArrivalPromptVisible = false,
    )

    /** @requirement FR-027 */
    @Test
    fun bearingUpdates_recomposeGuidanceOncePerState_andNothingOutsideIt() {
        val updates = 100
        var state by mutableStateOf<HomeScreenState>(guidance(0.0))
        compose.setContent {
            Column {
                OutsideGuidance()
                CountingHomeScreen(state)
            }
        }
        compose.waitForIdle()
        val baseline = guidanceCompositions

        repeat(updates) { i ->
            state = guidance(bearing = (i + 1) * 3.0)
            compose.waitForIdle()
        }

        assertEquals(updates, guidanceCompositions - baseline)
        assertEquals(1, outsideCompositions)
        assertEquals(
            (updates * 3.0) % 360.0,
            compose.onNodeWithTag(TestTags.GUIDANCE_CONE).fetchSemanticsNode().config[ConeDisplayBearingDegrees],
            1e-9,
        )
    }
}
