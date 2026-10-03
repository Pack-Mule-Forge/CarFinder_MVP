package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.oldParkedLocation
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.parkedRecord
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingEngineParkTest {

    private val r = CONVERGENCE_RADIUS_METERS

    /** @requirement FR-018 */
    @Test
    fun givenAStoredParkedRecord_whenStarted_thenItIsRestored() = runTest {
        val platform = FakePlatform(testScheduler)
        val location = oldParkedLocation()
        platform.store.seed(parkedRecord(location))
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(location, engine.state.value.parkedLocation)
    }

    /** @requirement FR-003, FR-018 */
    @Test
    fun everyChangeIsWrittenBeforeItIsPublished() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        val mismatches = mutableListOf<String>()
        val seen = mutableListOf<LifecycleState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.state.collect { state ->
                seen += state.lifecycle
                val stored = platform.store.record
                if (stored.state != state.lifecycle || stored.parkedLocation != state.parkedLocation) {
                    mismatches += "published ${state.lifecycle} while stored ${stored.state}"
                }
            }
        }
        engine.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH) + pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10))
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertTrue(LifecycleState.DRIVING in seen && LifecycleState.PARKING in seen)
        assertTrue(mismatches.isEmpty(), mismatches.joinToString())
    }

    /** @requirement FR-020 */
    @Test
    fun givenAnUnreadableStore_thenFindingIsLoggedAndTheNextWriteSucceeds() = runTest {
        val platform = FakePlatform(testScheduler)
        platform.store.seed(parkedRecord())
        platform.store.makeUnreadable()
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertTrue(DiagnosticEvent.StoreUnreadable in platform.log.events)
        send(platform, speeds(DRIVING_MPH))
        assertEquals(LifecycleState.DRIVING, platform.store.record.state)
    }

    /** @requirement FR-020 */
    @Test
    fun givenAnInconsistentRecord_thenItIsNormalizedLoggedAndRewrittenOnce() = runTest {
        val platform = FakePlatform(testScheduler)
        platform.store.seed(PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = null))
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertEquals(listOf(PersistedParkingRecord.DEFAULT), platform.store.writes)
        assertEquals(1, platform.log.events.count { it == DiagnosticEvent.RecordNormalized })
    }

    /** @requirement FR-027 */
    @Test
    fun theRequestedIntervalFollowsTheLifecycleWithNoRepeatedRequest() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        assertEquals(LifecycleState.PARKING, engine.state.value.lifecycle)
        assertEquals(listOf(SAMPLING_INTERVAL_IDLE_MILLIS, SAMPLING_INTERVAL_PARKING_MILLIS), platform.location.intervalHistory)
        assertEquals(SAMPLING_INTERVAL_PARKING_MILLIS, engine.state.value.samplingIntervalMillis)
    }

    /** @requirement FR-029 */
    @Test
    fun startingTwiceSubscribesOnce() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        engine.start()
        runCurrent()
        assertEquals(1, platform.location.startCount)
        assertTrue(engine.isRunning)
    }

    /** @requirement FR-018 */
    @Test
    fun restoreBeforeStartPublishesTheStoredStateAndStartsAndWritesNothing() = runTest {
        val platform = FakePlatform(testScheduler)
        val location = oldParkedLocation()
        platform.store.seed(parkedRecord(location))
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.restore()
        runCurrent()
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(location, engine.state.value.parkedLocation)
        assertEquals(0, platform.location.startCount)
        assertFalse(platform.location.hasSubscriber)
        assertTrue(platform.store.writes.isEmpty())
        assertFalse(engine.isRunning)
    }
}
