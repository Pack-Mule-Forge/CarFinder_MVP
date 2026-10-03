package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SHUTDOWN_NOTICE_DURATION_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.Capability.FINE_LOCATION
import com.packmuleforge.carfindermvp.shared.platform.Capability.NOTIFICATIONS
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import com.packmuleforge.carfindermvp.shared.testing.FakePermissionController
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class HomeScreenPresenterPermissionTest {

    private class Fixture(val platform: FakePlatform, val engine: ParkingEngine, val presenter: HomeScreenPresenter) {
        val permissions: FakePermissionController get() = platform.permissions
    }

    /** A presenter over [record] with nothing requested yet. */
    private fun TestScope.fixture(record: PersistedParkingRecord = PersistedParkingRecord.DEFAULT): Fixture {
        val platform = FakePlatform(testScheduler)
        platform.store.seed(record)
        Capability.entries.forEach { platform.permissions.setStatus(it, PermissionStatus.NOT_REQUESTED) }
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        val presenter = HomeScreenPresenter(engine, platform.adapters, backgroundScope)
        return Fixture(platform, engine, presenter)
    }

    private fun TestScope.deniedAt(f: Fixture, capability: Capability) {
        f.permissions.enqueueAnswers(capability, false)
        f.presenter.runPermissionSequence()
        runCurrent()
    }

    /** @requirement FR-042, FR-056 */
    @Test
    fun aDeniedRequiredRequestShowsTheConfirmationInEveryLifecycleState() = runTest {
        for (capability in PermissionState.REQUIRED) for (lifecycle in LifecycleState.entries) {
            val record = PersistedParkingRecord(
                state = lifecycle,
                parkedLocation = if (lifecycle == LifecycleState.PARKED) oldParkedLocation() else null,
            )
            val f = fixture(record)
            f.engine.restore()
            deniedAt(f, capability)
            assertEquals(HomeScreenState.PermissionRequired(capability), f.presenter.state.value, "$capability $lifecycle")
        }
    }

    /** @requirement FR-056 */
    @Test
    fun dismissingClearsTheConfirmationAndRequestsTheSameCapabilityAgain() = runTest {
        val f = fixture()
        f.permissions.enqueueAnswers(FINE_LOCATION, false, false)
        f.presenter.runPermissionSequence()
        runCurrent()
        f.presenter.onDenialDismissed()
        runCurrent()
        assertEquals(listOf(FINE_LOCATION, FINE_LOCATION), f.permissions.requests)
        assertEquals(HomeScreenState.PermissionRequired(FINE_LOCATION), f.presenter.state.value)
        assertFalse(f.presenter.isClosePending.value)
    }

    /** @requirement FR-056 */
    @Test
    fun confirmingShowsClosingAndRaisesTheCloseExactlyAfterTheNoticeDuration() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.onDenialConfirmed()
        runCurrent()
        assertEquals(HomeScreenState.Closing, f.presenter.state.value)
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS - 1)
        runCurrent()
        assertFalse(f.presenter.isClosePending.value)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(f.presenter.isClosePending.value)
        // It is state, not an event: it stays raised with no one watching.
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS * 10)
        runCurrent()
        assertTrue(f.presenter.isClosePending.value)
        assertEquals(listOf(FINE_LOCATION), f.permissions.requests)
    }

    /** @requirement FR-056 */
    @Test
    fun aConfirmedNotificationDenialRequestsNothingAfterIt() = runTest {
        val f = fixture()
        deniedAt(f, NOTIFICATIONS)
        f.presenter.onDenialConfirmed()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS + 1)
        runCurrent()
        assertEquals(Capability.entries.toList(), f.permissions.requests)
        assertTrue(f.presenter.isClosePending.value)
    }

    /** @requirement FR-056 */
    @Test
    fun aDismissalNeverRaisesTheClose() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.onDenialDismissed()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS * 3)
        runCurrent()
        assertFalse(f.presenter.isClosePending.value)
    }

    /** @requirement FR-048 */
    @Test
    fun runPermissionSequenceDoesNothingWhileOneRunsOrACloseIsPending() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.runPermissionSequence()
        runCurrent()
        assertEquals(1, f.permissions.requests.size)
        f.presenter.onDenialConfirmed()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS + 1)
        runCurrent()
        f.presenter.runPermissionSequence()
        runCurrent()
        assertEquals(1, f.permissions.requests.size)
    }

    /** @requirement FR-056 */
    @Test
    fun afterOnClosedNothingCarriesOverAndTheDenialIsRequestedAgain() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.onDenialConfirmed()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS + 1)
        runCurrent()
        f.presenter.onClosed()
        runCurrent()
        assertFalse(f.presenter.isClosePending.value)
        assertTrue(f.presenter.state.value !is HomeScreenState.Closing)
        assertTrue(f.presenter.state.value !is HomeScreenState.PermissionRequired)
        deniedAt(f, FINE_LOCATION)
        assertEquals(listOf(FINE_LOCATION, FINE_LOCATION), f.permissions.requests)
        assertEquals(HomeScreenState.PermissionRequired(FINE_LOCATION), f.presenter.state.value)
    }

    /** @requirement FR-056 */
    @Test
    fun cancellingWhileWaitingOnARequestClearsEverything() = runTest {
        val f = fixture()
        f.permissions.holdRequests = true
        f.presenter.runPermissionSequence()
        runCurrent()
        assertTrue(f.permissions.isRequestPending)
        f.presenter.cancelPermissionSequence()
        runCurrent()
        assertFalse(f.permissions.isRequestPending)
        f.permissions.holdRequests = false
        f.presenter.runPermissionSequence()
        runCurrent()
        assertEquals(FINE_LOCATION, f.permissions.requests[1])
    }

    /** @requirement FR-056 */
    @Test
    fun cancellingWhileWaitingOnAConfirmationClearsEverything() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.cancelPermissionSequence()
        runCurrent()
        assertTrue(f.presenter.state.value !is HomeScreenState.PermissionRequired)
        deniedAt(f, FINE_LOCATION)
        assertEquals(2, f.permissions.requests.count { it == FINE_LOCATION })
        assertEquals(HomeScreenState.PermissionRequired(FINE_LOCATION), f.presenter.state.value)
    }

    /** @requirement FR-056 */
    @Test
    fun cancellingDuringTheClosingWaitClearsEverything() = runTest {
        val f = fixture()
        deniedAt(f, FINE_LOCATION)
        f.presenter.onDenialConfirmed()
        runCurrent()
        f.presenter.cancelPermissionSequence()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS * 2)
        runCurrent()
        assertFalse(f.presenter.isClosePending.value)
        assertTrue(f.presenter.state.value !is HomeScreenState.Closing)
        deniedAt(f, FINE_LOCATION)
        assertEquals(HomeScreenState.PermissionRequired(FINE_LOCATION), f.presenter.state.value)
    }

    /** @requirement FR-049 */
    @Test
    fun aRequestThatThrowsClearsTheGuard() = runTest {
        val f = fixture()
        val throwing = object : com.packmuleforge.carfindermvp.shared.platform.PermissionController by f.permissions {
            var calls = 0
            override suspend fun request(capability: Capability): Boolean {
                calls++
                if (calls == 1) error("platform failure")
                return f.permissions.request(capability)
            }
        }
        val adapters = f.platform.adapters.let {
            com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters(
                it.location, it.heading, it.activity, it.store, throwing, it.monotonicClock, it.wallClock, it.log,
            )
        }
        val presenter = HomeScreenPresenter(ParkingEngine(adapters, backgroundScope), adapters, backgroundScope)
        presenter.runPermissionSequence()
        runCurrent()
        assertEquals(1, throwing.calls)
        // The guard was cleared, so a second run starts and asks for all four capabilities.
        presenter.runPermissionSequence()
        runCurrent()
        assertEquals(1 + Capability.entries.size, throwing.calls)
    }

    /** @requirement FR-049 */
    @Test
    fun neitherAnswerChangesTheLifecycleTheLocationOrTheStore() = runTest {
        val location = oldParkedLocation()
        val f = fixture(parkedRecord(location))
        f.engine.restore()
        deniedAt(f, FINE_LOCATION)
        f.presenter.onDenialDismissed()
        runCurrent()
        f.presenter.onDenialConfirmed()
        advanceTimeBy(SHUTDOWN_NOTICE_DURATION_MILLIS + 1)
        runCurrent()
        f.presenter.onClosed()
        runCurrent()
        assertEquals(LifecycleState.PARKED, f.engine.state.value.lifecycle)
        assertEquals(location, f.engine.state.value.parkedLocation)
        assertTrue(f.platform.store.writes.isEmpty())
    }
}
