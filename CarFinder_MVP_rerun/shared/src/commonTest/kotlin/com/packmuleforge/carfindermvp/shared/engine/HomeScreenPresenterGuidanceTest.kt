package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
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
class HomeScreenPresenterGuidanceTest {

    private val location = oldParkedLocation()

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

    private fun TestScope.emitHeading(platform: FakePlatform, degrees: Double) {
        platform.heading.emit(HeadingReading(degrees, platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
    }

    private fun assertParkedAndKept(engine: ParkingEngine) {
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(location, engine.state.value.parkedLocation)
    }

    /** @requirement FR-042 */
    @Test
    fun givenParkedWithAFixAndHeading_thenGuidance() = runTest {
        val f = parkedAndVisible()
        send(f.platform, readingOffset(-100.0, 0.0))
        emitHeading(f.platform, 12.0)
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value)
    }

    /** @requirement FR-040 */
    @Test
    fun whenTheFixGoesStaleWithNoNewEvent_thenUnavailableWithinOneRecheck() = runTest {
        val f = parkedAndVisible()
        send(f.platform, readingOffset(-100.0, 0.0))
        emitHeading(f.platform, 12.0)
        // Keep the heading fresh while the fix ages to exactly its timeout.
        var aged = 0L
        while (aged < FIX_STALENESS_TIMEOUT_MILLIS) {
            val step = minOf(HEADING_STALENESS_TIMEOUT_MILLIS / 2, FIX_STALENESS_TIMEOUT_MILLIS - aged)
            advanceTimeBy(step)
            aged += step
            emitHeading(f.platform, 12.0)
        }
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value, "a fix exactly at the timeout is still fresh")
        advanceTimeBy(AVAILABILITY_RECHECK_INTERVAL_MILLIS)
        runCurrent()
        assertIs<HomeScreenState.Unavailable>(f.presenter.state.value)
        assertTrue(AVAILABILITY_RECHECK_INTERVAL_MILLIS <= 1_000, "SC-010: within 1 s")
        assertParkedAndKept(f.engine)
    }

    /** @requirement FR-041 */
    @Test
    fun whenTheHeadingGoesStaleWithNoNewEvent_thenUnavailableWithinOneRecheck() = runTest {
        val f = parkedAndVisible()
        send(f.platform, readingOffset(-100.0, 0.0))
        emitHeading(f.platform, 12.0)
        advanceTimeBy(HEADING_STALENESS_TIMEOUT_MILLIS)
        runCurrent()
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value, "a heading exactly at the timeout is still fresh")
        advanceTimeBy(AVAILABILITY_RECHECK_INTERVAL_MILLIS)
        runCurrent()
        assertIs<HomeScreenState.Unavailable>(f.presenter.state.value)
        assertParkedAndKept(f.engine)
    }

    /** @requirement FR-041 */
    @Test
    fun whenTheHeadingBecomesUnreliable_thenUnavailableOnThatEmission() = runTest {
        val f = parkedAndVisible()
        send(f.platform, readingOffset(-100.0, 0.0))
        emitHeading(f.platform, 12.0)
        f.platform.heading.emit(null)
        runCurrent()
        assertIs<HomeScreenState.Unavailable>(f.presenter.state.value)
        assertParkedAndKept(f.engine)
    }

    /** @requirement FR-040, FR-041 */
    @Test
    fun whenAFreshFixAndHeadingReturn_thenGuidanceAgain() = runTest {
        val f = parkedAndVisible()
        send(f.platform, readingOffset(-100.0, 0.0))
        emitHeading(f.platform, 12.0)
        f.platform.heading.emit(null)
        runCurrent()
        advanceTimeBy(FIX_STALENESS_TIMEOUT_MILLIS * 2)
        runCurrent()
        assertIs<HomeScreenState.Unavailable>(f.presenter.state.value)
        send(f.platform, readingOffset(-90.0, 0.0))
        emitHeading(f.platform, 14.0)
        assertIs<HomeScreenState.Guidance>(f.presenter.state.value)
        assertParkedAndKept(f.engine)
    }

    /** @requirement FR-027, FR-034 */
    @Test
    fun visibilityStartsAndStopsTheHeadingSourceAndTellsTheEngine() = runTest {
        val f = parkedAndVisible()
        assertTrue(f.platform.heading.isStarted)
        assertEquals(SAMPLING_INTERVAL_GUIDANCE_MILLIS, f.engine.state.value.samplingIntervalMillis)
        f.presenter.onGuidanceVisible(false)
        runCurrent()
        assertTrue(!f.platform.heading.isStarted)
        assertEquals(1, f.platform.heading.stopCount)
        assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, f.engine.state.value.samplingIntervalMillis)
    }
}
