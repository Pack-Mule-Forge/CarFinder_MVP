package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * US4: whenever the app is open, exactly one view is chosen by FR-016's priority order.
 * @requirement QR-001
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenPresenterStatusTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE

    private fun TestScope.presenterFor(record: PersistedParkingRecord = PersistedParkingRecord.DEFAULT): Pair<HomeScreenPresenter, FakePlatform> {
        val platform = FakePlatform()
        platform.monotonicClock.now = 1_000_000L
        platform.store.seed(record)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ParkingEngine(platform.adapters, backgroundScope + dispatcher)
        val presenter = HomeScreenPresenter(engine, platform.heading, platform.monotonicClock, backgroundScope + dispatcher)
        backgroundScope.launch(dispatcher) { presenter.state.collect {} }
        engine.start()
        return presenter to platform
    }

    private suspend fun FakePlatform.drive(mph: Double) = repeat(window) {
        location.emit(Readings.readingOffset(northMeters = 100.0 * (it + 1), eastMeters = 0.0, speedMph = mph))
    }

    private val parked = PersistedParkingRecord(
        state = LifecycleState.PARKED,
        parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
    )

    /** @requirement FR-016 */
    @Test
    fun firstDriveAfterFreshInstall_showsDriving() = runTest {
        val (presenter, platform) = presenterFor()
        platform.drive(Readings.DRIVING_MPH)
        assertIs<HomeScreenState.Driving>(presenter.state.value)
    }

    /** @requirement FR-011, FR-016 */
    @Test
    fun freshInstall_showsUnavailable() = runTest {
        assertIs<HomeScreenState.Unavailable>(presenterFor().first.state.value)
    }

    /** @requirement FR-016 */
    @Test
    fun parking_showsParking() = runTest {
        val (presenter, platform) = presenterFor()
        platform.drive(Readings.DRIVING_MPH)
        platform.drive(Readings.PARKED_MPH)
        assertIs<HomeScreenState.Parking>(presenter.state.value)
    }

    /** @requirement FR-016, FR-031 */
    @Test
    fun parkedWithGuidance_showsGuidance_andWithoutIt_showsUnavailable() = runTest {
        val (presenter, platform) = presenterFor(parked)
        assertIs<HomeScreenState.Unavailable>(presenter.state.value)

        val now = platform.monotonicClock.now
        platform.location.emit(Readings.readingOffset(-60.0, 0.0, speedMph = null, elapsedMillis = now))
        platform.heading.emit(HeadingReading(0.0, now))
        assertIs<HomeScreenState.Guidance>(presenter.state.value)
    }
}
