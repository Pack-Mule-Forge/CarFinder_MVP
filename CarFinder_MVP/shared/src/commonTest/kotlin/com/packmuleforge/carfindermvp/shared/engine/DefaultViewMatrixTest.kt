package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every combination of required permissions, lifecycle state, fix and heading, through the real presenter, with no
 * denial confirmation pending (rule 1 is covered by DefaultViewSelectorTest and the permission presenter test).
 *
 * @requirement QR-001
 */
class DefaultViewMatrixTest {

    private enum class Fix { NONE, STALE, NO_ACCURACY, FRESH }
    private enum class Heading { NONE, STALE, FRESH }

    private val location = oldParkedLocation()

    private fun record(lifecycle: LifecycleState) = PersistedParkingRecord(
        state = lifecycle,
        parkedLocation = if (lifecycle == LifecycleState.PARKED) location else null,
    )

    /** FR-042's rules 2 to 7, written out independently of the selector. */
    private fun expected(fine: Boolean, notifications: Boolean, lifecycle: LifecycleState, fix: Fix, heading: Heading): KClass<*> = when {
        !fine || !notifications -> HomeScreenState.Unavailable::class
        lifecycle == LifecycleState.DRIVING -> HomeScreenState.Driving::class
        lifecycle == LifecycleState.FINDING -> HomeScreenState.Unavailable::class
        lifecycle == LifecycleState.PARKING -> HomeScreenState.Parking::class
        fix == Fix.FRESH && heading == Heading.FRESH -> HomeScreenState.Guidance::class
        else -> HomeScreenState.Unavailable::class
    }

    private suspend fun TestScope.arrange(platform: FakePlatform, fix: Fix, heading: Heading) {
        val stalePast = maxOf(FIX_STALENESS_TIMEOUT_MILLIS, HEADING_STALENESS_TIMEOUT_MILLIS) + 1
        // Inputs that must end up stale are given first and then aged; fresh ones are given afterwards.
        if (fix == Fix.STALE) send(platform, readingOffset(-100.0, 0.0))
        if (heading == Heading.STALE) emitHeading(platform)
        if (fix == Fix.STALE || heading == Heading.STALE) {
            advanceTimeBy(stalePast)
            runCurrent()
        }
        when (fix) {
            Fix.FRESH -> send(platform, readingOffset(-100.0, 0.0))
            Fix.NO_ACCURACY -> send(platform, readingOffset(-100.0, 0.0, accuracyMeters = null))
            else -> Unit
        }
        when (heading) {
            Heading.FRESH -> emitHeading(platform)
            Heading.NONE -> platform.heading.emit(null)
            Heading.STALE -> Unit
        }
        runCurrent()
    }

    private fun TestScope.emitHeading(platform: FakePlatform) {
        platform.heading.emit(HeadingReading(30.0, platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
    }

    /** @requirement FR-002, FR-042, FR-049 */
    @Test
    fun everyCombinationShowsExactlyTheViewFr042Names() = runTest {
        val failures = mutableListOf<String>()
        for (fine in listOf(true, false)) for (notifications in listOf(true, false)) {
            for (lifecycle in LifecycleState.entries) for (fix in Fix.entries) for (heading in Heading.entries) {
                val platform = FakePlatform(testScheduler)
                platform.store.seed(record(lifecycle))
                if (!fine) platform.permissions.setStatus(Capability.FINE_LOCATION, PermissionStatus.DENIED)
                if (!notifications) platform.permissions.setStatus(Capability.NOTIFICATIONS, PermissionStatus.DENIED)
                val engine = ParkingEngine(platform.adapters, backgroundScope)
                engine.start()
                runCurrent()
                val presenter = HomeScreenPresenter(engine, platform.adapters, backgroundScope)
                presenter.onGuidanceVisible(true)
                runCurrent()
                arrange(platform, fix, heading)

                val label = "fine=$fine notifications=$notifications $lifecycle fix=$fix heading=$heading"
                val shown = presenter.state.value
                val want = expected(fine, notifications, lifecycle, fix, heading)
                if (shown::class != want) failures += "$label: showed ${shown::class.simpleName}, expected ${want.simpleName}"
                // Neither a missing permission nor a lost fix or heading changes the lifecycle or the location.
                if (engine.state.value.lifecycle != lifecycle) failures += "$label: lifecycle became ${engine.state.value.lifecycle}"
                if (engine.state.value.parkedLocation != record(lifecycle).parkedLocation) failures += "$label: location changed"
                presenter.onGuidanceVisible(false)
                engine.stop()
                runCurrent()
            }
        }
        assertEquals(emptyList(), failures)
    }
}
