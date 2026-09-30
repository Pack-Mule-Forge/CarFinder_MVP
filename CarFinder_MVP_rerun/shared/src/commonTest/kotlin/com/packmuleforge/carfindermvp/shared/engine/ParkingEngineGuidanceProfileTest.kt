package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The 1 s GUIDANCE profile is used only while PARKED and the guidance screen is visible (research R2).
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParkingEngineGuidanceProfileTest {

    private fun TestScope.engineWith(state: LifecycleState): Pair<ParkingEngine, FakePlatform> {
        val platform = FakePlatform()
        val location = if (state == LifecycleState.PARKED) {
            ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)
        } else null
        platform.store.seed(PersistedParkingRecord(state = state, parkedLocation = location))
        val engine = ParkingEngine(platform.adapters, backgroundScope + UnconfinedTestDispatcher(testScheduler))
        engine.start()
        return engine to platform
    }

    /** @requirement FR-006 */
    @Test
    fun parked_guidanceVisibility_togglesGuidanceAndIdleWatch() = runTest {
        val (engine, platform) = engineWith(LifecycleState.PARKED)
        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last())

        engine.setGuidanceVisible(true)
        assertEquals(SamplingProfile.GUIDANCE, platform.location.profileHistory.last())
        assertEquals(SamplingProfile.GUIDANCE, engine.state.value.samplingProfile)

        engine.setGuidanceVisible(false)
        assertEquals(SamplingProfile.IDLE_WATCH, platform.location.profileHistory.last())
    }

    /** @requirement FR-006 */
    @Test
    fun otherStates_ignoreGuidanceVisibility() = runTest {
        for (state in listOf(LifecycleState.FINDING, LifecycleState.DRIVING, LifecycleState.PARKING)) {
            val (engine, platform) = engineWith(state)
            val before = platform.location.profileHistory.last()
            engine.setGuidanceVisible(true)
            assertEquals(before, platform.location.profileHistory.last(), "state $state")
        }
    }

    /** @requirement FR-006 */
    @Test
    fun profileChanges_areRequestedOnceEach_neverDuplicated() = runTest {
        val (engine, platform) = engineWith(LifecycleState.PARKED)
        engine.setGuidanceVisible(true)
        engine.setGuidanceVisible(true)
        engine.setGuidanceVisible(false)
        engine.setGuidanceVisible(false)
        assertEquals(
            listOf(SamplingProfile.IDLE_WATCH, SamplingProfile.GUIDANCE, SamplingProfile.IDLE_WATCH),
            platform.location.profileHistory,
        )
        assertEquals(CarFinderConstants.PARKING_SAMPLING_INTERVAL_MILLIS, SamplingProfile.PARKING.intervalMillis)
    }
}
