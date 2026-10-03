package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.platform.Capability.ACTIVITY_RECOGNITION
import com.packmuleforge.carfindermvp.shared.platform.Capability.BACKGROUND_LOCATION
import com.packmuleforge.carfindermvp.shared.platform.Capability.FINE_LOCATION
import com.packmuleforge.carfindermvp.shared.platform.Capability.NOTIFICATIONS
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus.DENIED
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus.GRANTED
import com.packmuleforge.carfindermvp.shared.testing.FakePermissionController
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** @requirement QR-001 */
class PermissionSequenceTest {

    private fun controller(vararg statuses: Pair<Capability, PermissionStatus>) =
        FakePermissionController(PermissionState(statuses.toMap()))

    /** Records every confirmation asked for and answers from [answers] (dismiss when exhausted). */
    private class Confirmations(vararg answers: Boolean) {
        val asked = mutableListOf<Capability>()
        private val queue = ArrayDeque(answers.toList())
        suspend fun answer(capability: Capability): Boolean {
            asked += capability
            return queue.removeFirstOrNull() ?: false
        }
    }

    /** @requirement FR-048 */
    @Test
    fun withNothingRequestedEachCapabilityIsRequestedOnceInOrder() = runTest {
        val controller = controller()
        val confirmations = Confirmations()
        val result = requestPermissionsInOrder(controller, confirmations::answer)
        assertEquals(listOf(FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, NOTIFICATIONS), controller.requests)
        assertTrue(confirmations.asked.isEmpty())
        assertIs<PermissionSequenceResult.Completed>(result)
        assertTrue(result.state.areRequiredGranted)
    }

    /** @requirement FR-048 */
    @Test
    fun aGrantedCapabilityIsNotRequested() = runTest {
        val controller = controller(FINE_LOCATION to GRANTED, NOTIFICATIONS to GRANTED)
        requestPermissionsInOrder(controller, Confirmations()::answer)
        assertEquals(listOf(BACKGROUND_LOCATION, ACTIVITY_RECOGNITION), controller.requests)
    }

    /** @requirement FR-048 */
    @Test
    fun aDeniedOptionalCapabilityIsNotRequestedAgainAndItsDenialAsksForNoConfirmation() = runTest {
        val again = controller(FINE_LOCATION to GRANTED, NOTIFICATIONS to GRANTED, BACKGROUND_LOCATION to DENIED, ACTIVITY_RECOGNITION to DENIED)
        requestPermissionsInOrder(again, Confirmations()::answer)
        assertTrue(again.requests.isEmpty())

        val fresh = controller()
        fresh.enqueueAnswers(BACKGROUND_LOCATION, false)
        fresh.enqueueAnswers(ACTIVITY_RECOGNITION, false)
        val confirmations = Confirmations()
        val result = requestPermissionsInOrder(fresh, confirmations::answer)
        assertEquals(listOf(FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, NOTIFICATIONS), fresh.requests)
        assertTrue(confirmations.asked.isEmpty())
        assertIs<PermissionSequenceResult.Completed>(result)
    }

    /** @requirement FR-048, FR-056 */
    @Test
    fun aDeniedRequiredCapabilityIsRequestedAgain() = runTest {
        val controller = controller(FINE_LOCATION to DENIED, NOTIFICATIONS to DENIED, BACKGROUND_LOCATION to DENIED, ACTIVITY_RECOGNITION to DENIED)
        requestPermissionsInOrder(controller, Confirmations()::answer)
        assertEquals(listOf(FINE_LOCATION, NOTIFICATIONS), controller.requests)
    }

    /** @requirement FR-056 */
    @Test
    fun aConfirmedLocationDenialClosesAndRequestsNothingElse() = runTest {
        val controller = controller()
        controller.enqueueAnswers(FINE_LOCATION, false)
        val confirmations = Confirmations(true)
        val result = requestPermissionsInOrder(controller, confirmations::answer)
        assertEquals(PermissionSequenceResult.CloseConfirmed(FINE_LOCATION), result)
        assertEquals(listOf(FINE_LOCATION), controller.requests)
        assertEquals(listOf(FINE_LOCATION), confirmations.asked)
    }

    /** @requirement FR-056 */
    @Test
    fun aDismissedLocationDenialRequestsLocationAgainAndAGrantContinues() = runTest {
        val controller = controller()
        controller.enqueueAnswers(FINE_LOCATION, false, true)
        val confirmations = Confirmations(false)
        val result = requestPermissionsInOrder(controller, confirmations::answer)
        assertEquals(listOf(FINE_LOCATION, FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, NOTIFICATIONS), controller.requests)
        assertEquals(listOf(FINE_LOCATION), confirmations.asked)
        assertIs<PermissionSequenceResult.Completed>(result)
    }

    /** @requirement FR-056 */
    @Test
    fun aConfirmedNotificationDenialCloses() = runTest {
        val controller = controller()
        controller.enqueueAnswers(NOTIFICATIONS, false)
        val result = requestPermissionsInOrder(controller, Confirmations(true)::answer)
        assertEquals(PermissionSequenceResult.CloseConfirmed(NOTIFICATIONS), result)
        assertEquals(listOf(FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, NOTIFICATIONS), controller.requests)
    }

    /** @requirement FR-056 */
    @Test
    fun aDismissedNotificationDenialRequestsNotificationsAgain() = runTest {
        val controller = controller()
        controller.enqueueAnswers(NOTIFICATIONS, false, true)
        val result = requestPermissionsInOrder(controller, Confirmations(false)::answer)
        assertEquals(listOf(NOTIFICATIONS, NOTIFICATIONS), controller.requests.takeLast(2))
        assertIs<PermissionSequenceResult.Completed>(result)
    }

    /** @requirement FR-048 */
    @Test
    fun backgroundLocationIsNeverRequestedWhileLocationIsNotGranted() = runTest {
        val controller = controller()
        controller.enqueueAnswers(FINE_LOCATION, false, false, false)
        requestPermissionsInOrder(controller, Confirmations(false, false, true)::answer)
        assertTrue(BACKGROUND_LOCATION !in controller.requests)
    }

    /** @requirement FR-048 */
    @Test
    fun aSecondCallAfterEverythingWasGrantedRequestsNothing() = runTest {
        val controller = controller()
        requestPermissionsInOrder(controller, Confirmations()::answer)
        controller.requests.clear()
        requestPermissionsInOrder(controller, Confirmations()::answer)
        assertTrue(controller.requests.isEmpty())
    }
}
