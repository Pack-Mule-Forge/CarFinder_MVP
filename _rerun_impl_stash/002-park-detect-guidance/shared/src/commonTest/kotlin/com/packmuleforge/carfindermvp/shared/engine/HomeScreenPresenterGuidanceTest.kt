package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The presenter turns engine, heading and clock into exactly one view state; the UI only renders it.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenPresenterGuidanceTest {

    private val recheck = TuningConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS
    private val parked = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)

    private class Harness(val platform: FakePlatform, val presenter: HomeScreenPresenter, val engine: ParkingEngine) {
        val states = mutableListOf<HomeScreenState>()
        val now get() = platform.monotonicClock.now

        suspend fun fix(northMeters: Double = -60.0, accuracy: Double? = Readings.GOOD_ACCURACY_METERS) =
            platform.location.emit(
                Readings.readingOffset(northMeters, 0.0, accuracy = accuracy, speedMph = null, elapsedMillis = now),
            )

        fun heading(degrees: Double = 0.0) = platform.heading.emit(HeadingReading(degrees, now))
    }

    private fun TestScope.harness(): Harness {
        val platform = FakePlatform()
        platform.monotonicClock.now = 1_000_000L
        platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = parked))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope + dispatcher)
        val presenter = HomeScreenPresenter(engine, platform.heading, platform.monotonicClock, backgroundScope + dispatcher)
        val h = Harness(platform, presenter, engine)
        backgroundScope.launch(dispatcher) { presenter.state.collect { h.states += it } }
        engine.start()
        return h
    }

    /** @requirement FR-017, FR-019, QR-009 */
    @Test
    fun parkedWithCurrentFixAndHeading_showsGuidance() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        val guidance = assertIs<HomeScreenState.Guidance>(h.presenter.state.value)
        assertEquals(com.packmuleforge.carfindermvp.shared.guidance.DistanceUnit.FEET, guidance.distance.unit)
        assertEquals(0.0, guidance.cone.displayBearingDegrees, 0.01)
    }

    /** @requirement FR-031 */
    @Test
    fun missingHeading_showsUnavailable_andKeepsParkedLocation() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        h.platform.heading.emit(null)
        assertIs<HomeScreenState.Unavailable>(h.presenter.state.value)
        assertEquals(LifecycleState.PARKED, h.engine.state.value.lifecycle)
        assertNotNull(h.platform.store.record.parkedLocation)
    }

    /** @requirement FR-034 */
    @Test
    fun fixGoingStaleWithoutNewFix_showsUnavailableWithinOneRecheckTick() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        assertIs<HomeScreenState.Guidance>(h.presenter.state.value)

        h.platform.monotonicClock.advanceBy(CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1)
        h.heading() // keep the heading fresh so only the fix is stale
        advanceTimeBy(recheck + 1)
        assertIs<HomeScreenState.Unavailable>(h.presenter.state.value)
    }

    /** @requirement FR-031 */
    @Test
    fun headingGoingStaleWithoutNewEvent_showsUnavailableWithinOneRecheckTick() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        h.platform.monotonicClock.advanceBy(CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS + 1)
        advanceTimeBy(recheck + 1)
        assertIs<HomeScreenState.Unavailable>(h.presenter.state.value)
    }

    /** @requirement FR-034 */
    @Test
    fun freshFixAfterStaleness_restoresGuidance() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        h.platform.monotonicClock.advanceBy(CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS + 1)
        h.heading()
        advanceTimeBy(recheck + 1)
        assertIs<HomeScreenState.Unavailable>(h.presenter.state.value)

        h.fix()
        assertIs<HomeScreenState.Guidance>(h.presenter.state.value)
    }

    /** @requirement FR-031 */
    @Test
    fun fixWithoutAccuracy_showsUnavailable() = runTest {
        val h = harness()
        h.fix(accuracy = null)
        h.heading()
        assertIs<HomeScreenState.Unavailable>(h.presenter.state.value)
    }

    /** @requirement QR-009 */
    @Test
    fun equalConsecutiveStates_areNotReEmitted() = runTest {
        val h = harness()
        h.fix()
        h.heading()
        val before = h.states.size
        advanceTimeBy(recheck * 3)
        assertEquals(before, h.states.size)
        assertEquals(h.states.zipWithNext().count { (a, b) -> a == b }, 0)
    }

    /** @requirement FR-021 */
    @Test
    fun latestFix_isForwardedForDeclinationCorrection() = runTest {
        val h = harness()
        h.fix()
        assertEquals(1, h.platform.heading.declinationFixes.size)
    }
}
