package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.InMemoryParkingStore
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The UI's restore() is read-only: it shows persisted state but never starts detection or writes storage.
 * Only the service's start() runs the actor loop and the location and activity subscriptions.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingEngineRestoreTest {

    private val location = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)
    private val parked = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)

    private fun TestScope.engineOver(platform: FakePlatform) =
        ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    private fun FakePlatform.assertNothingStarted() {
        assertFalse(location.isStarted, "restore must not start location sampling")
        assertFalse(activity.isStarted, "restore must not subscribe to activity recognition")
        assertTrue(location.profileHistory.isEmpty(), "restore must not request a sampling profile")
        assertTrue(store.writes.isEmpty(), "restore must not write the store")
    }

    /** @requirement FR-014 */
    @Test
    fun restore_publishesPersistedState_withoutStartingAnything() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(parked))
        val engine = engineOver(platform)

        engine.restore()

        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        assertEquals(location, engine.state.value.parkedLocation)
        assertFalse(engine.isRunning)
        platform.assertNothingStarted()
    }

    /** @requirement FR-018 */
    @Test
    fun restore_ofRecordNeedingNormalization_showsFinding_butDoesNotRewriteTheStore() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(PersistedParkingRecord(state = LifecycleState.PARKED)))
        val engine = engineOver(platform)

        engine.restore()

        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertNull(engine.state.value.parkedLocation)
        platform.assertNothingStarted()
    }

    /** @requirement FR-014 */
    @Test
    fun guidanceVisibilityBeforeStart_startsNothing_andIsHonoredOnceTheServiceStarts() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(parked))
        val engine = engineOver(platform)
        val published = mutableListOf<LifecycleState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.state.collect { published += it.lifecycle }
        }

        engine.restore()
        engine.setGuidanceVisible(true)
        platform.assertNothingStarted()

        engine.start()
        assertTrue(platform.location.isStarted)
        assertEquals(SamplingProfile.GUIDANCE, platform.location.profileHistory.last())
        // No flicker back to the blank FINDING snapshot between the UI's restore and the service's start.
        assertEquals(listOf(LifecycleState.FINDING, LifecycleState.PARKED), published.distinct())
        assertTrue(published.dropWhile { it == LifecycleState.FINDING }.all { it == LifecycleState.PARKED })
    }

    /** @requirement FR-014, FR-033 */
    @Test
    fun restoreAfterStart_isANoOp_andDoesNotOverrideTheRunningEngine() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(parked))
        val engine = engineOver(platform).apply { start() }
        repeat(CarFinderConstants.SPEED_FILTER_WINDOW_SIZE) {
            platform.location.emit(Readings.readingOffset(100.0 * (it + 1), 0.0, speedMph = Readings.DRIVING_MPH))
        }
        assertEquals(LifecycleState.DRIVING, engine.state.value.lifecycle)
        val writes = platform.store.writes.size

        engine.restore()

        assertEquals(LifecycleState.DRIVING, engine.state.value.lifecycle)
        assertEquals(writes, platform.store.writes.size)
    }
}
