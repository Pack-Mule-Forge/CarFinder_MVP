package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.domain.Transition
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.InMemoryParkingStore
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine wiring for the park path: restore, persistence ordering, sampling profile.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingEngineParkTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS

    private fun cluster() = List(CarFinderConstants.CONVERGENCE_SAMPLE_COUNT) { i ->
        Readings.readingOffset(radius * 0.1 * i, 0.0)
    }

    private fun parkSequence(): List<LocationReading> =
        List(window) { Readings.speed(Readings.DRIVING_MPH) } +
            List(window) { Readings.speed(Readings.PARKED_MPH) } +
            cluster()

    private fun TestScope.engineOn(platform: FakePlatform): ParkingEngine =
        ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    private suspend fun FakePlatform.emitAll(readings: List<LocationReading>) = readings.forEach { location.emit(it) }

    /** @requirement FR-014 */
    @Test
    fun start_restoresPersistedRecord() = runTest {
        val platform = FakePlatform()
        val parked = Readings.readingAt()
        platform.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = com.packmuleforge.carfindermvp.shared.domain.ParkedLocation(
                    parked.latitude, parked.longitude, Readings.GOOD_ACCURACY_METERS, capturedAtEpochMillis = 0L,
                ),
            ),
        )
        val engine = engineOn(platform).apply { start() }
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertNotNull(engine.state.value.parkedLocation)
    }

    /** @requirement FR-006 */
    @Test
    fun samplingProfile_followsLifecycle() = runTest {
        val platform = FakePlatform()
        engineOn(platform).start()
        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last())

        platform.emitAll(List(window) { Readings.speed(Readings.DRIVING_MPH) })
        assertEquals(SamplingProfile.DRIVING, platform.location.profileHistory.last())

        platform.emitAll(List(window) { Readings.speed(Readings.PARKED_MPH) })
        assertEquals(SamplingProfile.PARKING, platform.location.profileHistory.last())
        assertEquals(CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS, SamplingProfile.PARKING.intervalMillis)
    }

    /** @requirement FR-002, FR-012, FR-014 */
    @Test
    fun parking_persistsRecordBeforePublishingState() = runTest {
        val platform = FakePlatform()
        val engine = engineOn(platform)
        val observedStoreStates = mutableListOf<Pair<LifecycleState, LifecycleState>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.state.collect { observedStoreStates += it.lifecycle to platform.store.record.state }
        }
        engine.start()
        platform.emitAll(parkSequence())

        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(LifecycleState.PARKED, platform.store.record.state)
        assertNotNull(platform.store.record.parkedLocation)
        // Whenever a lifecycle was published, the store already held that lifecycle.
        observedStoreStates.filter { it.first != LifecycleState.FINDING }.forEach { (published, stored) ->
            assertEquals(published, stored)
        }
    }

    /** @requirement FR-018 */
    @Test
    fun restoredParkedRecordWithoutLocation_becomesFinding_andIsRewritten() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(PersistedParkingRecord(state = LifecycleState.PARKED)))
        val engine = engineOn(platform).apply { start() }
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertEquals(listOf(PersistedParkingRecord.DEFAULT), platform.store.writes)
    }

    /** @requirement FR-014, FR-033 */
    @Test
    fun recreatedEngineOverSameStore_restoresParkedWithSameLocation() = runTest {
        val platform = FakePlatform()
        val first = engineOn(platform).apply { start() }
        platform.emitAll(parkSequence())
        val location = assertNotNull(first.state.value.parkedLocation)
        first.stop()

        val second = engineOn(FakePlatform(store = platform.store)).apply { start() }
        assertEquals(LifecycleState.PARKED, second.state.value.lifecycle)
        assertEquals(location, second.state.value.parkedLocation)
    }

    /** @requirement FR-033 */
    @Test
    fun start_isIdempotent() = runTest {
        val platform = FakePlatform()
        val engine = engineOn(platform)
        engine.start()
        engine.start()
        platform.emitAll(List(window) { Readings.speed(Readings.DRIVING_MPH) })
        assertEquals(1, platform.store.writes.count { it.state == LifecycleState.DRIVING })
    }

    /** @requirement FR-002 */
    @Test
    fun transitions_emitsEachLifecycleChange() = runTest {
        val platform = FakePlatform()
        val engine = engineOn(platform)
        val seen = mutableListOf<Transition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.transitions.collect { seen += it } }
        engine.start()
        platform.emitAll(parkSequence())
        assertEquals(
            listOf(
                LifecycleState.FINDING to LifecycleState.DRIVING,
                LifecycleState.DRIVING to LifecycleState.PARKING,
                LifecycleState.PARKING to LifecycleState.PARKED,
            ),
            seen.map { it.from to it.to },
        )
        assertTrue(seen.all { it.persist })
        assertNull(seen.first().parkedLocation)
        assertEquals(engine.state.first().parkedLocation, seen.last().parkedLocation)
    }
}
