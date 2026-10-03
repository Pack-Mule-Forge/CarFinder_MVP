package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.ConvergenceWindow
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.engine.EngineTestSupport.speeds
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

/**
 * Premature-park sessions played through the real engine (SC-003).
 *
 * @requirement QR-016
 */
class RecoveryReplayTest {

    private val r = CONVERGENCE_RADIUS_METERS

    private fun converging(northMeters: Double, mph: Double? = PARKED_MPH) =
        pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, speedMph = mph, northOriginMeters = northMeters)

    /** A slow creep north from [fromMeters] that never converges and never reaches driving speed. */
    private fun creep(fromMeters: Double, steps: Int) =
        List(steps) { readingOffset(fromMeters + r * 2 * (it + 1), 0.0, speedMph = DEAD_ZONE_MPH) }

    private fun centroidOf(readings: List<LocationReading>) =
        readings.fold(ConvergenceWindow()) { w, x -> w.add(x) }.centroid

    private suspend fun TestScope.sendSpaced(platform: FakePlatform, readings: List<LocationReading>) {
        for (reading in readings) {
            send(platform, reading)
            advanceTimeBy(SAMPLING_INTERVAL_PARKING_MILLIS)
            runCurrent()
        }
    }

    private suspend fun TestScope.parkedAtTheBriefStop(): Pair<FakePlatform, ParkingEngine> {
        val platform = FakePlatform(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope)
        engine.start()
        runCurrent()
        send(platform, speeds(DRIVING_MPH) + speeds(PARKED_MPH))
        sendSpaced(platform, converging(0.0))
        assertEquals(LifecycleState.PARKED, engine.state.value.lifecycle)
        return platform to engine
    }

    private fun stored(platform: FakePlatform) =
        platform.store.record.parkedLocation!!.let { it.latitude to it.longitude }

    /** @requirement FR-021, FR-022 */
    @Test
    fun aSecondConvergenceInsideTheWindowStoresTheRealSpace() = runTest {
        val (platform, _) = parkedAtTheBriefStop()
        val realSpace = converging(r * 15)
        sendSpaced(platform, creep(0.0, 4) + realSpace)
        assertEquals(centroidOf(realSpace), stored(platform))
    }

    /** @requirement FR-024 */
    @Test
    fun aSecondConvergenceAfterTheWindowKeepsTheFirstLocation() = runTest {
        val (platform, _) = parkedAtTheBriefStop()
        val briefStop = stored(platform)
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS)
        sendSpaced(platform, creep(0.0, 4) + converging(r * 15))
        assertEquals(briefStop, stored(platform))
    }

    /** @requirement FR-024 */
    @Test
    fun parkingThenWalkingAwayAndSettlingAfterTheWindowKeepsTheLocation() = runTest {
        val (platform, _) = parkedAtTheBriefStop()
        val parked = stored(platform)
        advanceTimeBy(PARKED_RECOVERY_WINDOW_MILLIS)
        val walk = List(8) { readingOffset(r * 3 * (it + 1), 0.0, speedMph = PARKED_MPH) }
        sendSpaced(platform, walk + converging(r * 30, mph = 0.0))
        assertEquals(parked, stored(platform))
    }
}
