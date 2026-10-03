package com.packmuleforge.carfindermvp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.platform.Capability
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test k of contracts/guidance-ui.md.
 *
 * @requirement QR-004, QR-006
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class PermissionConfirmationTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirmed = 0
    private var dismissed = 0

    private fun show(state: HomeScreenState) {
        compose.setContent {
            HomeScreen(state, onArrivalAnswered = {}, onDenialConfirmed = { confirmed++ }, onDenialDismissed = { dismissed++ })
        }
    }

    /** @requirement FR-056 */
    @Test
    fun k_theConfirmationShowsExactlyItsTextForEachRequiredCapability() {
        val texts = mapOf(
            Capability.FINE_LOCATION to "Car Finder needs location permission to run. Close Car Finder?",
            Capability.NOTIFICATIONS to "Car Finder needs notification permission to run. Close Car Finder?",
        )
        var capability by mutableStateOf(Capability.FINE_LOCATION)
        compose.setContent {
            HomeScreen(HomeScreenState.PermissionRequired(capability), onArrivalAnswered = {}, onDenialConfirmed = {}, onDenialDismissed = {})
        }
        for ((required, text) in texts) {
            capability = required
            compose.waitForIdle()
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(text)
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_CLOSE).assertTextEquals("Close")
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_ALLOW).assertTextEquals("Allow")
        }
    }

    /** @requirement FR-056 */
    @Test
    fun k_closeConfirmsOnce() {
        show(HomeScreenState.PermissionRequired(Capability.FINE_LOCATION))
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_CLOSE).performClick()
        assertEquals(1 to 0, confirmed to dismissed)
    }

    /** @requirement FR-056 */
    @Test
    fun k_allowDismissesOnce() {
        show(HomeScreenState.PermissionRequired(Capability.FINE_LOCATION))
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_ALLOW).performClick()
        assertEquals(0 to 1, confirmed to dismissed)
    }

    /** @requirement FR-056 */
    @Test
    fun k_backDismissesOnce() {
        show(HomeScreenState.PermissionRequired(Capability.NOTIFICATIONS))
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(0 to 1, confirmed to dismissed)
    }

    /** @requirement FR-056 */
    @Test
    fun k_closingShowsExactlyItsTextAndNoButton() {
        show(HomeScreenState.Closing)
        compose.onNodeWithTag(UiTags.CLOSING_MESSAGE).assertTextEquals("Car Finder is closing.")
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_CLOSE).assertDoesNotExist()
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_ALLOW).assertDoesNotExist()
    }
}
