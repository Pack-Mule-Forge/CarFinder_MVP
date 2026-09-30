package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scripted replay of whole sessions through the engine (SC-001, SC-002).
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingReplayTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private var road = 0

    /** Readings along a road, spaced so they never converge by accident. */
    private fun travel(mph: Double, n: Int) = List(n) {
        road++
        Readings.readingOffset(northMeters = radius * 3 * road, eastMeters = 0.0, speedMph = mph)
    }

    private fun parkedCluster() = List(CarFinderConstants.CONVERGENCE_SAMPLE_COUNT) { i ->
        Readings.readingOffset(northMeters = -radius * 50 + radius * 0.1 * i, eastMeters = radius * 0.1 * i)
    }

    private data class Session(val lifecycles: List<LifecycleState>, val platform: FakePlatform)

    private fun replay(readings: List<LocationReading>): Session {
        val platform = FakePlatform()
        val lifecycles = mutableListOf<LifecycleState>()
        runTest {
            val engine = ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                engine.state.collect { lifecycles += it.lifecycle }
            }
            engine.start()
            readings.forEach { platform.location.emit(it) }
        }
        return Session(lifecycles.distinct(), platform)
    }

    /** @requirement FR-002 */
    @Test
    fun driveStopConverge_storesParkedLocationAtCentroid_withNoManualAction() {
        val cluster = parkedCluster()
        val session = replay(travel(Readings.DRIVING_MPH, window * 2) + travel(Readings.PARKED_MPH, window) + cluster)

        val stored = assertNotNull(session.platform.store.record.parkedLocation)
        assertEquals(LifecycleState.PARKED, session.platform.store.record.state)
        val centroid = GeoMath.centroid(cluster.map { LatLon(it.latitude, it.longitude) })
        assertEquals(centroid.latitude, stored.latitude, 1e-12)
        assertEquals(centroid.longitude, stored.longitude, 1e-12)
    }

    /** @requirement FR-005 */
    @Test
    fun deadZoneOnlySession_neverChangesState() {
        val session = replay(travel(Readings.DEAD_ZONE_MPH, window * 10))
        assertEquals(listOf(LifecycleState.FINDING), session.lifecycles)
    }

    /** @requirement FR-011 */
    @Test
    fun freshInstallWithoutADrive_storesNoLocation() {
        val session = replay(travel(Readings.PARKED_MPH, window * 5) + parkedCluster() + parkedCluster())
        assertEquals(listOf(LifecycleState.FINDING), session.lifecycles)
        assertNull(session.platform.store.record.parkedLocation)
        assertTrue(session.platform.store.writes.isEmpty())
    }

    /** @requirement FR-010 */
    @Test
    fun longRedLight_returnsToDriving_withNothingStored() {
        val session = replay(
            travel(Readings.DRIVING_MPH, window) +
                travel(Readings.PARKED_MPH, window) +
                parkedCluster().dropLast(1) +
                travel(Readings.DRIVING_MPH, window),
        )
        assertTrue(LifecycleState.PARKING in session.lifecycles)
        assertEquals(LifecycleState.DRIVING, session.platform.store.record.state)
        assertNull(session.platform.store.record.parkedLocation)
        assertTrue(session.platform.store.writes.none { it.parkedLocation != null })
    }
}
