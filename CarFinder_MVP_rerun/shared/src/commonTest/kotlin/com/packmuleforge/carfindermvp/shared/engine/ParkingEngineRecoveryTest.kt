package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.Transition
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingEngineRecoveryTest {

    private val r = CONVERGENCE_RADIUS_METERS

    /** A Parked Location declared [ageMillis] before the fake wall clock's start. */
    private fun declaredAgo(ageMillis: Long) = Readings.readingOffset(0.0, 0.0).let {
        ParkedLocation(it.latitude, it.longitude, Readings.GOOD_ACCURACY_METERS, FakeWallClock.DEFAULT_START_EPOCH_MILLIS - ageMillis)
    }

    private fun converging(northMeters: Double) =
        pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, northOriginMeters = northMeters)

    private suspend fun TestScope.sendSpaced(platform: FakePlatform, readings: List<com.packmuleforge.carfindermvp.shared.domain.LocationReading>) {
        for (reading in readings) {
            send(platform, reading)
            advanceTimeBy(SAMPLING_INTERVAL_PARKING_MILLIS)
            runCurrent()
        }
    }

    private fun TestScope.started(record: ParkedLocation?): Pair<FakePlatform, ParkingEngine> {
        val platform = FakePlatform(testScheduler)
        record?.let { platform.store.seed(parkedRecord(it)) }
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        return platform to engine
    }

    /** @requirement FR-022 */
    @Test
    fun aCorrectionIsWrittenAndPublishedButNotEmittedAsATransition() = runTest {
        val (platform, engine) = started(declaredAgo(0))
        val transitions = mutableListOf<Transition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.transitions.collect { transitions += it } }
        val before = engine.state.value.parkedLocation
        sendSpaced(platform, converging(r * 5))
        assertNotEquals(before, engine.state.value.parkedLocation)
        assertEquals(engine.state.value.parkedLocation, platform.store.record.parkedLocation)
        assertEquals(before?.declaredAtEpochMillis, platform.store.record.parkedLocation?.declaredAtEpochMillis)
        assertTrue(transitions.isEmpty())
    }

    /** @requirement FR-027 */
    @Test
    fun afterParkingTheIntervalStaysTheParkingIntervalEvenWithGuidanceVisible() = runTest {
        val (platform, engine) = started(null)
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        sendSpaced(platform, converging(0.0))
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
        engine.setGuidanceVisible(true)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
    }

    /** @requirement FR-024, FR-027 */
    @Test
    fun whenTheWindowEndsWithNoReadingTheIntervalDropsToGuidanceWhenVisible() = runTest {
        val (platform, engine) = started(declaredAgo(0))
        engine.setGuidanceVisible(true)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last(), "still open at the end")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_GUIDANCE_MILLIS, platform.location.intervalHistory.last())
    }

    /** @requirement FR-024, FR-027 */
    @Test
    fun whenTheWindowEndsWithNoReadingTheIntervalDropsToIdleWhenHidden() = runTest {
        val (platform, _) = started(declaredAgo(0))
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS + 2)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, platform.location.intervalHistory.last())
    }

    /** @requirement FR-023, FR-024 */
    @Test
    fun anEngineStartedInsideTheWindowRecoversAndTimesTheRemainder() = runTest {
        val (platform, engine) = started(declaredAgo(PARKED_RECOVERY_WINDOW_MILLIS / 2))
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
        val before = engine.state.value.parkedLocation
        sendSpaced(platform, converging(r * 5))
        assertNotEquals(before, engine.state.value.parkedLocation)
        // Three readings took three parking intervals; the rest of the half window is still open.
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS / 2 - 3 * SAMPLING_INTERVAL_PARKING_MILLIS)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, platform.location.intervalHistory.last())
    }

    /** @requirement FR-024 */
    @Test
    fun anEngineStartedAfterTheWindowNeitherRecoversNorWaits() = runTest {
        val (platform, engine) = started(declaredAgo(PARKED_RECOVERY_WINDOW_MILLIS * 2))
        assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, platform.location.intervalHistory.last())
        val before = engine.state.value.parkedLocation
        sendSpaced(platform, converging(r * 5))
        assertEquals(before, engine.state.value.parkedLocation)
        assertEquals(listOf(SAMPLING_INTERVAL_IDLE_MILLIS), platform.location.intervalHistory)
    }
}
