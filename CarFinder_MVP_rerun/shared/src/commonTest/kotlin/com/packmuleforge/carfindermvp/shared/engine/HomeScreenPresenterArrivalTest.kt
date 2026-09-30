package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Arrival in the presenter: the prompt is display-only and never touches the lifecycle or the store.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenPresenterArrivalTest {

    private val parked = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)

    private class Harness(val platform: FakePlatform, val presenter: HomeScreenPresenter, val engine: ParkingEngine) {
        /** A fix [metersSouth] from the car; accuracy large enough that 1 m away is inside the uncertainty. */
        suspend fun fixAt(metersSouth: Double) {
            val now = platform.monotonicClock.now
            platform.location.emit(Readings.readingOffset(-metersSouth, 0.0, accuracy = 3.0, speedMph = null, elapsedMillis = now))
            platform.heading.emit(HeadingReading(0.0, now))
        }

        fun guidance() = assertIs<HomeScreenState.Guidance>(presenter.state.value)
    }

    private fun TestScope.harness(): Harness {
        val platform = FakePlatform()
        platform.monotonicClock.now = 1_000_000L
        platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = parked))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope + dispatcher)
        val presenter = HomeScreenPresenter(engine, platform.heading, platform.monotonicClock, backgroundScope + dispatcher)
        backgroundScope.launch(dispatcher) { presenter.state.collect {} }
        engine.start()
        return Harness(platform, presenter, engine)
    }

    /** @requirement FR-028 */
    @Test
    fun atArrival_guidanceIsArrived_andPromptIsVisible() = runTest {
        val h = harness()
        h.fixAt(metersSouth = 60.0)
        assertFalse(h.guidance().isArrived)
        h.fixAt(metersSouth = 1.0)
        assertTrue(h.guidance().isArrived)
        assertTrue(h.guidance().isArrivalPromptVisible)
    }

    /** @requirement FR-029 */
    @Test
    fun answeringYesOrNo_hidesPrompt_withoutTouchingLifecycleOrStore() = runTest {
        for (answer in listOf(true, false)) {
            val h = harness()
            h.fixAt(metersSouth = 1.0)
            val writesBefore = h.platform.store.writes.toList()

            h.presenter.onArrivalAnswered(answer)

            assertTrue(h.guidance().isArrived, "arrival message stays while still arrived")
            assertFalse(h.guidance().isArrivalPromptVisible)
            assertEquals(LifecycleState.PARKED, h.engine.state.value.lifecycle)
            assertEquals(parked, h.platform.store.record.parkedLocation)
            assertEquals(writesBefore, h.platform.store.writes)
        }
    }

    /** @requirement FR-028 */
    @Test
    fun promptReturns_onlyAfterLeavingAndReArriving() = runTest {
        val h = harness()
        h.fixAt(metersSouth = 1.0)
        h.presenter.onArrivalAnswered(true)
        h.fixAt(metersSouth = 1.5)
        assertFalse(h.guidance().isArrivalPromptVisible)

        h.fixAt(metersSouth = 60.0)
        h.fixAt(metersSouth = 1.0)
        assertTrue(h.guidance().isArrivalPromptVisible)
    }
}
