package com.packmuleforge.carfindermvp

import android.os.Looper
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SHUTDOWN_NOTICE_DURATION_MILLIS
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.Capability.FINE_LOCATION
import com.packmuleforge.carfindermvp.shared.platform.Capability.NOTIFICATIONS
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import com.packmuleforge.carfindermvp.ui.UiTags
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class MainActivityPermissionFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()
    private val permissions get() = app.platform.permissions
    private val locationText = "Car Finder needs location permission to run. Close Car Finder?"
    private val notificationText = "Car Finder needs notification permission to run. Close Car Finder?"

    private fun nothingRequested() = Capability.entries.forEach { permissions.setStatus(it, PermissionStatus.NOT_REQUESTED) }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { idle() }

    private fun startedServices() = generateSequence { shadowOf(app).nextStartedService }
        .map { it.component?.className }.toList()

    private fun stoppedServices() = generateSequence { shadowOf(app).nextStoppedService }
        .map { it.component?.className }.toList()

    private fun activityOf(scenario: ActivityScenario<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        return checkNotNull(activity)
    }

    private fun confirmClose() {
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED_CLOSE).performClick()
        idle()
    }

    /** @requirement FR-048 */
    @Test
    fun atLaunchEachCapabilityIsRequestedOnceInOrderAndARelaunchRequestsNothing() {
        nothingRequested()
        launch().close()
        assertEquals(listOf(FINE_LOCATION, Capability.BACKGROUND_LOCATION, Capability.ACTIVITY_RECOGNITION, NOTIFICATIONS), permissions.requests)
        launch().close()
        assertEquals(4, permissions.requests.size)
    }

    /** @requirement FR-051, FR-052 */
    @Test
    fun withBothRequiredPermissionsTheServiceIsStartedAndTheActivityNeverStartsTheEngine() {
        launch().use {
            assertTrue(ParkingDetectionService::class.java.name in startedServices())
            assertFalse(app.engine.isRunning)
        }
    }

    /** @requirement FR-049, FR-056 */
    @Test
    fun aLocationDenialShowsTheConfirmationAndBackAsksForLocationAgain() {
        nothingRequested()
        permissions.enqueueAnswers(FINE_LOCATION, false, false)
        launch().use {
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(locationText)
            assertTrue(startedServices().isEmpty())
            Espresso.pressBack()
            idle()
            assertEquals(listOf(FINE_LOCATION, FINE_LOCATION), permissions.requests)
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(locationText)
        }
    }

    /** @requirement FR-056 */
    @Test
    fun confirmingShowsTheClosingMessageThenClosesTheAppAndStopsTheService() {
        nothingRequested()
        permissions.enqueueAnswers(FINE_LOCATION, false)
        val activity = activityOf(launch())
        confirmClose()
        compose.onNodeWithTag(UiTags.CLOSING_MESSAGE).assertTextEquals("Car Finder is closing.")
        assertEquals(listOf(FINE_LOCATION), permissions.requests)
        app.advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS - 1)
        assertFalse(activity.isFinishing)
        app.advanceTimeBy(1)
        assertTrue(activity.isFinishing)
        assertTrue(ParkingDetectionService::class.java.name in stoppedServices())
        assertFalse(app.presenter.isClosePending.value)
        assertTrue(app.presenter.state.value !is HomeScreenState.Closing)
    }

    /** @requirement FR-056 */
    @Test
    fun aNotificationDenialAfterTheOtherThreeClosesTheSameWay() {
        nothingRequested()
        permissions.enqueueAnswers(NOTIFICATIONS, false)
        val activity = activityOf(launch())
        compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(notificationText)
        assertTrue(startedServices().isEmpty())
        confirmClose()
        app.advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS)
        assertTrue(activity.isFinishing)
        assertEquals(Capability.entries.toList(), permissions.requests)
    }

    /** @requirement FR-048, FR-056 */
    @Test
    fun afterAConfirmedDenialANewActivityShowsNoStaleStateAndAsksAgain() {
        nothingRequested()
        permissions.enqueueAnswers(FINE_LOCATION, false, false)
        val closed = launch()
        confirmClose()
        app.advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS)
        // The system destroys the finished Activity before the user launches the app again.
        closed.close()
        launch().use {
            compose.onNodeWithTag(UiTags.CLOSING_MESSAGE).assertDoesNotExist()
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(locationText)
            assertEquals(listOf(FINE_LOCATION, FINE_LOCATION), permissions.requests)
        }
    }

    /** @requirement FR-056 */
    @Test
    fun anActivityStoppedDuringTheClosingMessageClosesOnItsNextStart() {
        nothingRequested()
        permissions.enqueueAnswers(FINE_LOCATION, false)
        val scenario = launch()
        val activity = activityOf(scenario)
        confirmClose()
        scenario.moveToState(Lifecycle.State.CREATED)
        app.advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS * 2)
        assertFalse(activity.isFinishing, "a stopped Activity is not closed")
        assertTrue(app.presenter.isClosePending.value)
        scenario.moveToState(Lifecycle.State.STARTED)
        idle()
        assertTrue(activity.isFinishing)
        assertFalse(app.presenter.isClosePending.value)
    }

    /** @requirement FR-048 */
    @Test
    fun recreatingTheActivityWhileAPromptIsOpenKeepsTheRequestAndTheSequenceContinues() {
        nothingRequested()
        permissions.holdRequests = true
        val scenario = launch()
        assertTrue(permissions.isRequestPending)
        scenario.recreate()
        idle()
        assertTrue(permissions.isRequestPending)
        permissions.holdRequests = false
        permissions.releaseRequest(true)
        idle()
        assertEquals(Capability.entries.toList(), permissions.requests)
        scenario.close()
    }

    /** @requirement FR-056 */
    @Test
    fun finishingWhileAPromptIsOpenLetsTheNextActivityAskAgain() {
        nothingRequested()
        permissions.holdRequests = true
        launch().close()
        assertFalse(permissions.isRequestPending)
        permissions.holdRequests = false
        launch().close()
        assertEquals(FINE_LOCATION, permissions.requests[1])
    }

    /** @requirement FR-056 */
    @Test
    fun finishingWhileTheConfirmationIsShownLetsTheNextActivityAskAgain() {
        nothingRequested()
        permissions.enqueueAnswers(FINE_LOCATION, false, false)
        launch().close()
        launch().use {
            assertEquals(listOf(FINE_LOCATION, FINE_LOCATION), permissions.requests)
            compose.onNodeWithTag(UiTags.PERMISSION_REQUIRED).assertTextEquals(locationText)
        }
    }

    /** @requirement FR-048, FR-051 */
    @Test
    fun returningToTheForegroundRefreshesAsksAgainForARevokedPermissionAndStartsTheService() {
        launch().use { scenario ->
            startedServices()
            scenario.moveToState(Lifecycle.State.CREATED)
            val refreshes = permissions.refreshCount
            permissions.setStatus(FINE_LOCATION, PermissionStatus.DENIED)
            permissions.enqueueAnswers(FINE_LOCATION, true)
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()
            assertTrue(permissions.refreshCount > refreshes)
            assertEquals(listOf(FINE_LOCATION), permissions.requests)
            assertTrue(ParkingDetectionService::class.java.name in startedServices())
        }
    }
}
