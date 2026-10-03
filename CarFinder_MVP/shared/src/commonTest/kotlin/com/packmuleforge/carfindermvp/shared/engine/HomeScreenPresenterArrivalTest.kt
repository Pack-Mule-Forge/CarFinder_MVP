package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings.GOOD_ACCURACY_METERS
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** @requirement QR-001 */
class HomeScreenPresenterArrivalTest {

    private val location = oldParkedLocation()

    /** Uncertainty is the stored plus the live accuracy; these distances put a fix inside or outside it. */
    private val uncertainty = location.accuracyMeters + GOOD_ACCURACY_METERS
    private val inside = uncertainty * 0.8
    private val exactly = uncertainty
    private val outside = uncertainty * 4

    private class Fixture(val platform: FakePlatform, val engine: ParkingEngine, val presenter: HomeScreenPresenter)

    private fun TestScope.parkedAndVisible(): Fixture {
        val platform = FakePlatform(testScheduler)
        platform.store.seed(parkedRecord(location))
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        val presenter = HomeScreenPresenter(engine, platform.adapters, backgroundScope)
        presenter.onGuidanceVisible(true)
        runCurrent()
        return Fixture(platform, engine, presenter)
    }

    private suspend fun TestScope.at(f: Fixture, southMeters: Double) {
        send(f.platform, readingOffset(-southMeters, 0.0))
        f.platform.heading.emit(HeadingReading(0.0, f.platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
    }

    /** @requirement FR-038, FR-039 */
    @Test
    fun arrivedWithThePromptWhenUncertaintyIsAtLeastTheDistanceAndGuidanceOtherwise() = runTest {
        val f = parkedAndVisible()
        at(f, outside)
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value)
        at(f, exactly)
        assertEquals(HomeScreenState.Arrived(isPromptVisible = true), f.presenter.state.value)
        at(f, outside)
        at(f, inside)
        assertEquals(HomeScreenState.Arrived(isPromptVisible = true), f.presenter.state.value)
    }

    /** @requirement FR-039 */
    @Test
    fun answeringDismissesThePromptOnlyAndChangesNothingElse() = runTest {
        val f = parkedAndVisible()
        at(f, inside)
        f.presenter.onArrivalAnswered()
        runCurrent()
        assertEquals(HomeScreenState.Arrived(isPromptVisible = false), f.presenter.state.value)
        assertEquals(LifecycleState.PARKED, f.engine.state.value.lifecycle)
        assertEquals(location, f.engine.state.value.parkedLocation)
        assertTrue(f.platform.store.writes.isEmpty())
    }

    /** @requirement FR-039 */
    @Test
    fun anUnavailableSpellInsideTheArrivalZoneDoesNotBringThePromptBack() = runTest {
        val f = parkedAndVisible()
        at(f, inside)
        f.presenter.onArrivalAnswered()
        runCurrent()
        f.platform.heading.emit(null)
        runCurrent()
        advanceTimeBy(FIX_STALENESS_TIMEOUT_MILLIS * 2)
        runCurrent()
        assertIs<HomeScreenState.Unavailable>(f.presenter.state.value)
        at(f, inside)
        assertEquals(HomeScreenState.Arrived(isPromptVisible = false), f.presenter.state.value)
    }

    /** @requirement FR-039 */
    @Test
    fun leavingAndReEnteringTheArrivalZonePromptsAgain() = runTest {
        val f = parkedAndVisible()
        at(f, inside)
        f.presenter.onArrivalAnswered()
        runCurrent()
        at(f, outside)
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value)
        at(f, inside)
        assertEquals(HomeScreenState.Arrived(isPromptVisible = true), f.presenter.state.value)
    }
}
