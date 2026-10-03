package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.domain.ConvergenceWindow
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings.DEAD_ZONE_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drive-and-park sessions played through the real engine (SC-001, SC-002, SC-004).
 *
 * @requirement QR-016
 */
class ParkReplayTest {

    private val r = CONVERGENCE_RADIUS_METERS

    /** @requirement FR-003, FR-012, FR-015 */
    @Test
    fun driveStopAndConvergeStoresTheCentroidOnTheThirdConvergingReading() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        val converging = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, speedMph = PARKED_MPH)
        send(platform, converging.take(2))
        assertEquals(LifecycleState.PARKING, engine.state.value.lifecycle)
        send(platform, converging.last())
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        val expected = converging.fold(ConvergenceWindow()) { w, x -> w.add(x) }
        val stored = platform.store.record.parkedLocation!!
        assertEquals(expected.centroid, stored.latitude to stored.longitude)
        assertEquals(expected.accuracyRadiusMeters, stored.accuracyMeters, r / 1_000_000)
    }

    /** @requirement FR-006 */
    @Test
    fun aSessionOfDeadZoneSpeedsOnlyChangesNothing() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, speeds(DEAD_ZONE_MPH, SPEED_FILTER_WINDOW_SIZE * 5))
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertTrue(platform.store.writes.isEmpty())
    }

    /** @requirement FR-005 */
    @Test
    fun aSessionWithNoDriveStoresNothing() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, List(SPEED_FILTER_WINDOW_SIZE * 5) { readingOffset(0.0, 0.0, speedMph = PARKED_MPH) })
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
        assertNull(platform.store.record.parkedLocation)
        assertTrue(platform.store.writes.isEmpty())
    }

    /** @requirement FR-008 */
    @Test
    fun aVeryFastFirstReadingChangesNothing() = runTest {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH * 4, 1))
        assertEquals(LifecycleState.FINDING, engine.state.value.lifecycle)
    }

    /** @requirement FR-018 */
    @Test
    fun aSecondEngineOnTheSameStoreRestoresTheParkedLocation() = runTest {
        val platform = FakePlatform(testScheduler)
        val first = ParkingEngine(platform.adapters, backgroundScope)
        first.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH) + pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10))
        val parked = first.state.value.parkedLocation
        first.stop()
        runCurrent()
        val second = ParkingEngine(platform.adapters, backgroundScope)
        second.start()
        runCurrent()
        assertEquals(LifecycleState.PARKED, second.state.value.lifecycle)
        assertEquals(parked, second.state.value.parkedLocation)
    }
}
