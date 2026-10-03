package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingPolicy
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings.DEAD_ZONE_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * One trip through every stage: park, an early-stop correction, guidance, arrival, an answer and drive-away. After
 * every step the requested interval equals the sampling policy for the engine's inputs (SC-012).
 *
 * @requirement QR-016
 */
class FullTripReplayTest {

    private val r = CONVERGENCE_RADIUS_METERS

    private class Trip(val platform: FakePlatform, val engine: ParkingEngine, val presenter: HomeScreenPresenter) {
        var visible = false
        var inVehicle = false
    }

    /** SC-012: the interval in effect matches FR-027 for the engine's current inputs. */
    private fun TestScope.assertIntervalMatchesPolicy(trip: Trip, step: String) {
        runCurrent()
        val state = trip.engine.state.value
        val open = state.parkedLocation?.isRecoveryOpen(trip.platform.wallClock.epochMillis()) == true
        val expected = SamplingPolicy.intervalFor(state.lifecycle, open, trip.visible, trip.inVehicle)
        assertEquals(expected, trip.platform.location.intervalHistory.last(), "after $step")
    }

    private suspend fun TestScope.step(trip: Trip, name: String, readings: List<LocationReading>, spacing: Long = 0) {
        for (reading in readings) {
            send(trip.platform, reading)
            if (spacing > 0) advanceTimeBy(spacing)
            assertIntervalMatchesPolicy(trip, name)
        }
    }

    /** @requirement FR-001, FR-027 */
    @Test
    fun aWholeTripPassesThroughEveryStageWithTheRightSamplingThroughout() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        val trip = Trip(platform, engine, HomeScreenPresenter(engine, platform.adapters, backgroundScope))
        assertIntervalMatchesPolicy(trip, "start")
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)

        trip.inVehicle = true
        platform.activity.set(true)
        assertIntervalMatchesPolicy(trip, "entering the vehicle")

        step(trip, "driving", speeds(DRIVING_MPH))
        assertEquals(LifecycleState.DRIVING, engine.state.value.lifecycle)
        step(trip, "slowing", speeds(PARKED_MPH))
        assertEquals(LifecycleState.PARKING, engine.state.value.lifecycle)

        step(trip, "brief stop", pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, speedMph = PARKED_MPH), SAMPLING_INTERVAL_PARKING_MILLIS)
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        val briefStop = engine.state.value.parkedLocation

        trip.inVehicle = false
        platform.activity.set(false)
        assertIntervalMatchesPolicy(trip, "leaving the vehicle")

        val creep = List(4) { readingOffset(r * 2 * (it + 1), 0.0, speedMph = DEAD_ZONE_MPH) }
        step(trip, "creeping", creep, SAMPLING_INTERVAL_PARKING_MILLIS)
        val realSpace = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, speedMph = 0.0, northOriginMeters = r * 15)
        step(trip, "real space", realSpace, SAMPLING_INTERVAL_PARKING_MILLIS)
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        val car = engine.state.value.parkedLocation
        assertNotEquals(briefStop, car)
        assertEquals(briefStop?.declaredAtEpochMillis, car?.declaredAtEpochMillis)

        trip.visible = true
        trip.presenter.onGuidanceVisible(true)
        assertIntervalMatchesPolicy(trip, "guidance visible inside the window")
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS)
        assertIntervalMatchesPolicy(trip, "window closed")

        step(trip, "walking back", listOf(readingOffset(r * 15 - 120, 0.0)))
        platform.heading.emit(HeadingReading(0.0, platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
        assertIs<HomeScreenState.Guidance>(trip.presenter.state.value)

        step(trip, "arriving", listOf(readingOffset(r * 15, 0.0)))
        platform.heading.emit(HeadingReading(0.0, platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
        assertEquals(HomeScreenState.Arrived(isPromptVisible = true), trip.presenter.state.value)
        trip.presenter.onArrivalAnswered()
        runCurrent()
        assertEquals(HomeScreenState.Arrived(isPromptVisible = false), trip.presenter.state.value)
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(car, engine.state.value.parkedLocation)

        trip.visible = false
        trip.presenter.onGuidanceVisible(false)
        assertIntervalMatchesPolicy(trip, "guidance hidden")
        step(trip, "driving away", speeds(DRIVING_MPH))
        assertEquals(LifecycleState.DRIVING, engine.state.value.lifecycle)
        assertNull(platform.store.record.parkedLocation)
    }
}
