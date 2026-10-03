package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingEngineDriveAwayTest {

    private val r = CONVERGENCE_RADIUS_METERS

    private fun TestScope.started(record: PersistedParkingRecord? = null): Pair<FakePlatform, ParkingEngine> {
        val platform = FakePlatform(testScheduler)
        record?.let(platform.store::seed)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        return platform to engine
    }

    private suspend fun TestScope.sendSpaced(platform: FakePlatform, readings: List<LocationReading>) {
        for (reading in readings) {
            send(platform, reading)
            advanceTimeBy(SAMPLING_INTERVAL_PARKING_MILLIS)
            runCurrent()
        }
    }

    /** @requirement FR-019 */
    @Test
    fun drivingAwayWritesDrivingAndNoLocationInOneWrite() = runTest {
        val (platform, engine) = started(parkedRecord())
        send(platform, speeds(PARKED_MPH) + speeds(DRIVING_MPH))
        assertEquals(LifecycleState.DRIVING, engine.state.value.lifecycle)
        assertEquals(listOf(PersistedParkingRecord(state = LifecycleState.DRIVING, parkedLocation = null)), platform.store.writes)
    }

    /** @requirement FR-019 */
    @Test
    fun aNewEngineAfterDrivingAwayHoldsNoLocation() = runTest {
        val (platform, first) = started(parkedRecord())
        send(platform, speeds(PARKED_MPH) + speeds(DRIVING_MPH))
        first.stop()
        val second = ParkingEngine(platform.adapters, backgroundScope)
        second.start()
        runCurrent()
        assertNotEquals(LifecycleState.PARKED, second.state.value.lifecycle)
        assertNull(second.state.value.parkedLocation)
    }

    /** @requirement FR-017, FR-019 */
    @Test
    fun twoParkCyclesLeaveExactlyTheSecondLocation() = runTest {
        val (platform, engine) = started()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        sendSpaced(platform, pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10))
        val first = engine.state.value.parkedLocation
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        val secondSpot = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, northOriginMeters = r * 100)
        sendSpaced(platform, secondSpot)
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertNotEquals(first, platform.store.record.parkedLocation)
        assertEquals(engine.state.value.parkedLocation, platform.store.record.parkedLocation)
    }

    /** @requirement FR-028 */
    @Test
    fun theInVehicleHintRaisesAndRestoresTheIntervalWithoutChangingTheLifecycle() = runTest {
        for (record in listOf(null, parkedRecord(oldParkedLocation()))) {
            val (platform, engine) = started(record)
            val lifecycle = engine.state.value.lifecycle
            assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, platform.location.intervalHistory.last())
            platform.activity.set(true)
            runCurrent()
            assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, platform.location.intervalHistory.last())
            platform.activity.set(false)
            runCurrent()
            assertEquals(SAMPLING_INTERVAL_IDLE_MILLIS, platform.location.intervalHistory.last())
            assertEquals(lifecycle, engine.state.value.lifecycle)
            assertTrue(platform.location.isStarted)
            engine.stop()
        }
    }

    /** @requirement FR-026 */
    @Test
    fun recoveryGivesTheSameCorrectionWhateverTheInVehicleSignal() = runTest {
        val results = mutableListOf<ParkedLocation?>()
        for (signal in listOf(listOf(true), listOf(false), listOf(true, false, true))) {
            val fresh = oldParkedLocation().copy(declaredAtEpochMillis = FakeWallClock.DEFAULT_START_EPOCH_MILLIS + testScheduler.currentTime)
            val (platform, engine) = started(parkedRecord(fresh))
            val readings = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, northOriginMeters = r * 5)
            for ((index, reading) in readings.withIndex()) {
                platform.activity.set(signal[index % signal.size])
                sendSpaced(platform, listOf(reading))
            }
            results += engine.state.value.parkedLocation?.let { it.copy(declaredAtEpochMillis = 0) }
            engine.stop()
        }
        assertNotEquals(oldParkedLocation().copy(declaredAtEpochMillis = 0), results.first())
        assertEquals(1, results.toSet().size, "results: $results")
    }
}
