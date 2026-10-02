package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.domain.Transition
import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Recovery from a premature PARKED, replayed through the engine: the brief-stop-then-creep trip is corrected, and
 * a walk that settles after the recovery window is not.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingEngineRecoveryTest {

    private val speedWindow = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private val sampleCount = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT
    private val recoveryWindow = CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
    private val interval = CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS

    private val entranceNorth = 0.0
    private val spotNorth = radius * 15

    private fun TestScope.started(platform: FakePlatform) =
        ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler)).apply { start() }

    /** Emits one reading per parking-sampling interval, advancing both clocks together. */
    private suspend fun FakePlatform.emitPaced(readings: List<LocationReading>) = readings.forEach {
        wallClock.now += interval
        monotonicClock.advanceBy(interval)
        location.emit(it.copy(elapsedRealtimeMillis = monotonicClock.now, epochMillis = wallClock.now))
    }

    private fun travel(mph: Double) = List(speedWindow) { i ->
        Readings.readingOffset(northMeters = -radius * 3 * (speedWindow - i), eastMeters = 0.0, speedMph = mph)
    }

    private fun stopAt(northMeters: Double) = List(sampleCount) { i ->
        Readings.readingOffset(northMeters = northMeters + radius * 0.1 * i, eastMeters = 0.0)
    }

    /** Low-speed movement between the two stops, too spread out to converge. */
    private fun creep() = List(sampleCount) { i ->
        Readings.readingOffset(northMeters = entranceNorth + radius * 3 * (i + 1), eastMeters = 0.0)
    }

    private fun centroidOf(readings: List<LocationReading>) =
        GeoMath.centroid(readings.map { LatLon(it.latitude, it.longitude) })

    private suspend fun FakePlatform.driveAndStopAtEntrance() =
        emitPaced(travel(Readings.DRIVING_MPH) + travel(Readings.PARKED_MPH) + stopAt(entranceNorth))

    /** @requirement FR-035 */
    @Test
    fun briefStopThenCreepToRealSpot_withinRecoveryWindow_storesTheRealSpot_silently() = runTest {
        val platform = FakePlatform()
        val engine = started(platform)
        val lifecycleChanges = mutableListOf<Transition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.transitions.collect { lifecycleChanges += it } }

        platform.driveAndStopAtEntrance()
        val premature = assertNotNull(platform.store.record.parkedLocation)
        val changesAtPrematurePark = lifecycleChanges.size

        val realSpot = stopAt(spotNorth)
        platform.emitPaced(creep() + realSpot)

        val stored = assertNotNull(platform.store.record.parkedLocation)
        val centroid = centroidOf(realSpot)
        assertEquals(centroid.latitude, stored.latitude, 1e-12)
        assertEquals(centroid.longitude, stored.longitude, 1e-12)
        assertEquals(premature.capturedAtEpochMillis, stored.capturedAtEpochMillis)
        assertEquals(LifecycleState.PARKED, platform.store.record.state)
        assertEquals(stored, engine.state.value.parkedLocation)
        assertEquals(changesAtPrematurePark, lifecycleChanges.size, "a correction must not be announced as a transition")
    }

    /** @requirement FR-035 */
    @Test
    fun parkThenWalkAwayAndSettle_afterRecoveryWindow_keepsTheParkedLocation() = runTest {
        val platform = FakePlatform()
        started(platform)
        platform.driveAndStopAtEntrance()
        val parkedAt = assertNotNull(platform.store.record.parkedLocation)

        platform.wallClock.now += recoveryWindow
        platform.monotonicClock.advanceBy(recoveryWindow)
        platform.emitPaced(creep() + stopAt(spotNorth))

        assertEquals(parkedAt, platform.store.record.parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun recoveryWindow_usesParkingSamplingProfile_thenFallsBackToIdleWatch() = runTest {
        val platform = FakePlatform()
        started(platform)
        platform.driveAndStopAtEntrance()
        assertEquals(SamplingProfile.PARKING, platform.location.profileHistory.last())

        platform.wallClock.now += recoveryWindow
        platform.emitPaced(listOf(Readings.readingOffset(entranceNorth, 0.0)))

        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last())
    }

    /** @requirement FR-035 */
    @Test
    fun guidanceVisibleDuringRecovery_keepsGuidanceProfile() = runTest {
        val platform = FakePlatform()
        val engine = started(platform)
        platform.driveAndStopAtEntrance()

        engine.setGuidanceVisible(true)

        assertEquals(SamplingProfile.GUIDANCE, platform.location.profileHistory.last())
    }

    /** @requirement FR-035 */
    @Test
    fun restoredWithinRecoveryWindow_stillRecovers_restoredAfterIt_doesNot() = runTest {
        val declaredAt = recoveryWindow * 10
        val seeded = PersistedParkingRecord(
            state = LifecycleState.PARKED,
            parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, declaredAt),
        )
        val recent = FakePlatform(store = InMemoryParkingStore(seeded)).apply { wallClock.now = declaredAt }
        val old = FakePlatform(store = InMemoryParkingStore(seeded)).apply { wallClock.now = declaredAt + recoveryWindow }
        started(recent)
        started(old)

        recent.emitPaced(stopAt(spotNorth))
        old.emitPaced(stopAt(spotNorth))

        assertEquals(centroidOf(stopAt(spotNorth)).latitude, recent.store.record.parkedLocation?.latitude)
        assertEquals(seeded.parkedLocation, old.store.record.parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun minimumSpacing_toleratesJitter_butStaysWellAboveTheGuidanceInterval() {
        val spacing = TuningConstants.RECOVERY_MIN_SAMPLE_SPACING_MILLIS
        assertTrue(spacing < interval, "a reading delivered slightly early must still be accepted")
        assertTrue(
            spacing > TuningConstants.GUIDANCE_SAMPLING_INTERVAL_MILLIS * (sampleCount - 1),
            "guidance-rate readings must be thinned",
        )
    }
}
