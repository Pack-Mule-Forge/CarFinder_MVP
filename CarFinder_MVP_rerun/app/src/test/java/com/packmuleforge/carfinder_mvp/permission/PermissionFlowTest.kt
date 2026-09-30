package com.packmuleforge.carfinder_mvp.permission

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.platform.Capability
import com.packmuleforge.carfinder.shared.platform.PermissionResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T108: PermissionFlowCoordinator asserted against FR-045/FR-046 using an :app-local
 * [FakePermissionController] (see that file for why the shared commonTest fake is not reused).
 *
 * Written first, against [PermissionFlowCoordinator] which does not exist yet at the start of
 * this task — expected to fail to compile/red until T110 lands.
 */
@Requirement("FR-045", "FR-046")
class PermissionFlowTest {

    @Test
    fun requestsInFR045OrderWhenEverythingIsGranted() = runTest {
        val fake = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.GRANTED)
            script(Capability.BACKGROUND_LOCATION, PermissionResult.GRANTED)
            script(Capability.ACTIVITY_RECOGNITION, PermissionResult.GRANTED)
            script(Capability.NOTIFICATIONS, PermissionResult.GRANTED)
        }
        var serviceStarts = 0
        val coordinator = PermissionFlowCoordinator(fake) { serviceStarts++ }

        coordinator.runFlow()

        assertEquals(
            listOf(
                Capability.LOCATION,
                Capability.BACKGROUND_LOCATION,
                Capability.ACTIVITY_RECOGNITION,
                Capability.NOTIFICATIONS
            ),
            fake.requestLog,
            "FR-045: precise location, then background location, then activity recognition, then notifications"
        )
        assertTrue(coordinator.locationGranted.value, "location granted must be observable")
        assertEquals(1, serviceStarts, "FR-046: service starts automatically, exactly once, on grant")
    }

    @Test
    fun deniedLocationStopsTheSequenceAndNeverStartsTheService() = runTest {
        val fake = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.DENIED)
        }
        var serviceStarts = 0
        val coordinator = PermissionFlowCoordinator(fake) { serviceStarts++ }

        coordinator.runFlow()

        assertEquals(
            listOf(Capability.LOCATION),
            fake.requestLog,
            "FR-046: background/activity/notifications must not be requested while location is denied"
        )
        assertFalse(coordinator.locationGranted.value)
        assertEquals(0, serviceStarts, "no service start while location is denied")
    }

    @Test
    fun aDeclinedCapabilityIsNeverRepeatedWithinTheSameSession() = runTest {
        val fake = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.GRANTED)
            script(Capability.BACKGROUND_LOCATION, PermissionResult.DENIED)
            script(Capability.ACTIVITY_RECOGNITION, PermissionResult.GRANTED)
            script(Capability.NOTIFICATIONS, PermissionResult.GRANTED)
        }
        val coordinator = PermissionFlowCoordinator(fake) {}

        coordinator.runFlow() // first "launch": background location declined
        coordinator.runFlow() // second "launch" in the same session: must not re-ask background

        val backgroundRequests = fake.requestLog.count { it == Capability.BACKGROUND_LOCATION }
        assertEquals(1, backgroundRequests, "FR-045: no repeat of a request already declined this session")
        // Location and the still-outstanding-but-granted capabilities may legitimately be
        // re-evaluated on each pass; only the declined one is asserted not to repeat.
    }

    @Test
    fun serviceStartIsRequestedOnlyOnceEvenIfLocationIsReRequestedGranted() = runTest {
        val fake = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.GRANTED)
            script(Capability.BACKGROUND_LOCATION, PermissionResult.GRANTED)
            script(Capability.ACTIVITY_RECOGNITION, PermissionResult.GRANTED)
            script(Capability.NOTIFICATIONS, PermissionResult.GRANTED)
        }
        var serviceStarts = 0
        val coordinator = PermissionFlowCoordinator(fake) { serviceStarts++ }

        coordinator.runFlow()
        coordinator.runFlow() // e.g. app re-opened later in the same process/session

        assertEquals(1, serviceStarts, "FR-046: automatic start on grant, not re-triggered every pass")
    }

    @Test
    fun backgroundLocationDivergesByApiLevelButCoordinatorSequencingIsUnaffected() = runTest {
        // API 24-28: AndroidPermissionController.request(BACKGROUND_LOCATION) returns GRANTED
        // implicitly (no dialog). API 30+: it is a genuine request. The divergence is entirely
        // inside PermissionController (contracts/platform-adapters.md); the coordinator above it
        // must behave identically either way - it always calls request() once, in order.
        val api24Style = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.GRANTED)
            script(Capability.BACKGROUND_LOCATION, PermissionResult.GRANTED) // implicit grant
            script(Capability.ACTIVITY_RECOGNITION, PermissionResult.GRANTED)
            script(Capability.NOTIFICATIONS, PermissionResult.GRANTED)
        }
        val api30Style = FakePermissionController().apply {
            script(Capability.LOCATION, PermissionResult.GRANTED)
            script(Capability.BACKGROUND_LOCATION, PermissionResult.DENIED) // explicit request, declined
            script(Capability.ACTIVITY_RECOGNITION, PermissionResult.GRANTED)
            script(Capability.NOTIFICATIONS, PermissionResult.GRANTED)
        }

        val coordinator24 = PermissionFlowCoordinator(api24Style) {}
        val coordinator30 = PermissionFlowCoordinator(api30Style) {}
        coordinator24.runFlow()
        coordinator30.runFlow()

        assertEquals(1, api24Style.requestLog.count { it == Capability.BACKGROUND_LOCATION })
        assertEquals(1, api30Style.requestLog.count { it == Capability.BACKGROUND_LOCATION })
        // Location itself was granted on both, so the sequence continued past background location
        // on both, regardless of whether that capability was granted or denied.
        assertTrue(Capability.ACTIVITY_RECOGNITION in api24Style.requestLog)
        assertTrue(Capability.ACTIVITY_RECOGNITION in api30Style.requestLog)
        assertTrue(coordinator24.locationGranted.value)
        assertTrue(coordinator30.locationGranted.value)
    }
}
