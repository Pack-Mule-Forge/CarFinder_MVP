package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.InMemoryParkingStore
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Drive-away through the engine, with no UI: the stale location is gone, including across restarts.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingEngineDriveAwayTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private val pastRecoveryWindow = CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS + 1
    private var road = 0

    private fun travel(mph: Double, n: Int = window) = List(n) {
        road++
        Readings.readingOffset(northMeters = radius * 3 * road, eastMeters = 0.0, speedMph = mph)
    }

    private fun clusterAt(northMeters: Double) = List(CarFinderConstants.CONVERGENCE_SAMPLE_COUNT) { i ->
        Readings.readingOffset(northMeters = northMeters + radius * 0.1 * i, eastMeters = 0.0)
    }

    private fun parkAt(northMeters: Double) = travel(Readings.DRIVING_MPH) + travel(Readings.PARKED_MPH) + clusterAt(northMeters)

    private val seeded = PersistedParkingRecord(
        state = LifecycleState.PARKED,
        parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
    )

    private fun TestScope.started(platform: FakePlatform) =
        ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler)).apply { start() }

    private suspend fun FakePlatform.emitAll(readings: List<LocationReading>) = readings.forEach { location.emit(it) }

    /** @requirement FR-013 */
    @Test
    fun twoParkCycles_leaveExactlyOneLocation_theSecond() = runTest {
        val platform = FakePlatform()
        started(platform)
        platform.emitAll(parkAt(northMeters = -radius * 100))
        val first = assertNotNull(platform.store.record.parkedLocation)

        val secondCluster = clusterAt(northMeters = radius * 500)
        platform.emitAll(travel(Readings.DRIVING_MPH) + travel(Readings.PARKED_MPH) + secondCluster)

        val second = assertNotNull(platform.store.record.parkedLocation)
        assertNotEquals(first, second)
        val centroid = GeoMath.centroid(secondCluster.map { LatLon(it.latitude, it.longitude) })
        assertEquals(centroid.latitude, second.latitude, 1e-12)
        assertEquals(LifecycleState.PARKED, platform.store.record.state)
    }

    /** @requirement FR-015, FR-033 */
    @Test
    fun seededParked_thenDrivingWithNoUi_storesDrivingWithNoLocation() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(seeded))
        started(platform)
        platform.emitAll(travel(Readings.DRIVING_MPH))
        assertEquals(LifecycleState.DRIVING, platform.store.record.state)
        assertNull(platform.store.record.parkedLocation)
    }

    /** @requirement FR-015 */
    @Test
    fun afterDriveAway_recreatedEngine_isNotParked_andHasNoLocation() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(seeded))
        started(platform).also { platform.emitAll(travel(Readings.DRIVING_MPH)) }.stop()

        val restarted = started(FakePlatform(store = platform.store))
        assertNotEquals(LifecycleState.PARKED, restarted.state.value.lifecycle)
        assertNull(restarted.state.value.parkedLocation)
    }

    /** @requirement FR-006 */
    @Test
    fun inVehicleHint_upgradesIdleWatchToDriving_withoutChangingLifecycle_untilNextLifecycleChange() = runTest {
        val platform = FakePlatform(store = InMemoryParkingStore(seeded))
        platform.wallClock.now = pastRecoveryWindow
        val engine = started(platform)
        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last())

        platform.activity.emitInVehicle()
        assertEquals(SamplingProfile.DRIVING, platform.location.profileHistory.last())
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)

        platform.emitAll(travel(Readings.DRIVING_MPH))
        platform.emitAll(travel(Readings.PARKED_MPH))
        platform.emitAll(clusterAt(northMeters = radius * 900))
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        // The new location's recovery window (FR-035) holds the PARKING rate; let it lapse to see the idle profile.
        platform.wallClock.now += pastRecoveryWindow
        platform.emitAll(clusterAt(northMeters = radius * 900).take(1))
        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last(), "hint must reset after a lifecycle change")
    }
}
