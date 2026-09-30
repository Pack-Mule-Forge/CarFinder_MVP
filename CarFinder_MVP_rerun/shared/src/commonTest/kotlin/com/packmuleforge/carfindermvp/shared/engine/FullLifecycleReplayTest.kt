package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One whole trip through the engine and presenter: park, guidance, arrival, answer, drive away.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullLifecycleReplayTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private var road = 0

    private fun travel(mph: Double) = List(window) {
        road++
        Readings.readingOffset(northMeters = radius * 3 * road, eastMeters = 0.0, speedMph = mph)
    }

    /** @requirement FR-001, FR-009 */
    @Test
    fun wholeTrip_parkGuideArriveAnswerDriveAway() = runTest {
        val platform = FakePlatform()
        platform.monotonicClock.now = 1_000_000L
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope + dispatcher)
        val presenter = HomeScreenPresenter(engine, platform.heading, platform.monotonicClock, backgroundScope + dispatcher)
        backgroundScope.launch(dispatcher) { presenter.state.collect {} }
        engine.start()

        suspend fun emit(readings: List<LocationReading>) = readings.forEach { platform.location.emit(it) }
        fun records() = platform.store.writes.map { it.state }

        // FINDING on a fresh install.
        assertIs<HomeScreenState.Unavailable>(presenter.state.value)

        // Drive: DRIVING.
        emit(travel(Readings.DRIVING_MPH))
        assertIs<HomeScreenState.Driving>(presenter.state.value)

        // Slow down: PARKING.
        emit(travel(Readings.PARKED_MPH))
        assertIs<HomeScreenState.Parking>(presenter.state.value)

        // Converge at the car: PARKED, location stored.
        val carNorth = radius * 3 * (road + 20)
        emit(List(CarFinderConstants.CONVERGENCE_SAMPLE_COUNT) { i -> Readings.readingOffset(carNorth + radius * 0.1 * i, 0.0) })
        assertEquals(LifecycleState.PARKED, platform.store.record.state)
        val car = assertNotNull(platform.store.record.parkedLocation)
        assertEquals(listOf(LifecycleState.DRIVING, LifecycleState.PARKING, LifecycleState.PARKED), records())

        // Walk back later: guidance appears once there is a current fix and a heading (no speed, so no transition).
        platform.monotonicClock.advanceBy(CarFinderConstants.FIX_STALENESS_TIMEOUT_MILLIS * 10)
        advanceTimeBy(1)
        suspend fun walkTo(metersSouthOfCar: Double) {
            val now = platform.monotonicClock.now
            platform.location.emit(
                Readings.readingOffset(carNorth - metersSouthOfCar, 0.0, accuracy = 3.0, speedMph = null, elapsedMillis = now),
            )
            platform.heading.emit(HeadingReading(0.0, now))
        }
        walkTo(80.0)
        val far = assertIs<HomeScreenState.Guidance>(presenter.state.value)
        assertFalse(far.isArrived)

        // Arrive: the cone gives way to the arrival message and prompt.
        walkTo(1.0)
        val arrived = assertIs<HomeScreenState.Guidance>(presenter.state.value)
        assertTrue(arrived.isArrived && arrived.isArrivalPromptVisible)

        // Answer: prompt dismissed, nothing persisted, still PARKED with the same location.
        val writesBefore = platform.store.writes.size
        presenter.onArrivalAnswered(true)
        assertFalse(assertIs<HomeScreenState.Guidance>(presenter.state.value).isArrivalPromptVisible)
        assertEquals(writesBefore, platform.store.writes.size)
        assertEquals(car, platform.store.record.parkedLocation)

        // Drive away: DRIVING, location deleted.
        emit(travel(Readings.DRIVING_MPH))
        assertIs<HomeScreenState.Driving>(presenter.state.value)
        assertEquals(PersistedParkingRecord(state = LifecycleState.DRIVING), platform.store.record)
        assertNull(engine.state.value.parkedLocation)
        assertEquals(LifecycleState.DRIVING, records().last())
    }
}
