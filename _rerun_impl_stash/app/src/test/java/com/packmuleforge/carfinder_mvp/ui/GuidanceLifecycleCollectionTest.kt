package com.packmuleforge.carfinder_mvp.ui

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.platform.Clock
import com.packmuleforge.carfinder.shared.platform.HeadingProvider
import com.packmuleforge.carfinder.shared.platform.LocationProvider
import com.packmuleforge.carfinder.shared.platform.LocationRequestTier
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.repository.PersistedParkingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * FR-044: the ephemeral location/heading (and staleness ticker) collection must run only while
 * the default view is STARTED, while the persisted repository.observe() path keeps running
 * independent of visibility (FR-014).
 */
@Requirement("FR-044")
class GuidanceLifecycleCollectionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TrackingLocationProvider : LocationProvider {
        var activeCollectors = 0
            private set

        override fun samples(request: LocationRequestTier): Flow<LocationSample> = flow {
            activeCollectors++
            try {
                awaitCancellation()
            } finally {
                activeCollectors--
            }
        }
    }

    private class TrackingHeadingProvider : HeadingProvider {
        var activeCollectors = 0
            private set

        override fun headingDegrees(): Flow<Double> = flow {
            activeCollectors++
            try {
                awaitCancellation()
            } finally {
                activeCollectors--
            }
        }
    }

    private class TrackingRepository : ParkedLocationRepository {
        var activeObservers = 0
            private set

        override fun observe(): Flow<PersistedParkingData> = flow {
            activeObservers++
            try {
                emit(PersistedParkingData(ParkingState.FINDING, null))
                awaitCancellation()
            } finally {
                activeObservers--
            }
        }

        override suspend fun load(): PersistedParkingData = PersistedParkingData(ParkingState.FINDING, null)
        override suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?) = Unit
        override suspend fun clearLocation() = Unit
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun nowEpochMillis(): Long = millis
    }

    @Test
    fun ephemeralCollectionOnlyRunsBetweenStartAndStopWhilePersistedPathIsAlwaysOn() = runTest {
        val locationProvider = TrackingLocationProvider()
        val headingProvider = TrackingHeadingProvider()
        val repository = TrackingRepository()
        val viewModel = GuidanceViewModel(repository, locationProvider, headingProvider, FixedClock(0L))

        dispatcher.scheduler.runCurrent()

        // Persisted path starts immediately at construction and is unaffected by ephemeral start/stop.
        assertEquals(1, repository.activeObservers)
        assertEquals(0, locationProvider.activeCollectors, "no location collection before start()")
        assertEquals(0, headingProvider.activeCollectors, "no heading collection before start()")

        viewModel.startEphemeralCollection()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, locationProvider.activeCollectors, "location collection active once started")
        assertEquals(1, headingProvider.activeCollectors, "heading collection active once started")
        assertEquals(1, repository.activeObservers)

        viewModel.stopEphemeralCollection()
        dispatcher.scheduler.runCurrent()
        assertEquals(0, locationProvider.activeCollectors, "location collection stops when told")
        assertEquals(0, headingProvider.activeCollectors, "heading collection stops when told")
        assertEquals(1, repository.activeObservers, "persisted path keeps running after ephemeral stop")

        // Collection restarts cleanly on the next start.
        viewModel.startEphemeralCollection()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, locationProvider.activeCollectors, "location collection resumes on restart")
        assertEquals(1, headingProvider.activeCollectors, "heading collection resumes on restart")
        assertEquals(1, repository.activeObservers)

        // Stop the ephemeral collection before the test body returns. The age ticker inside it
        // re-queues a delay() on every tick forever; if it's left running, runTest's teardown
        // (advanceUntilIdleOr on Dispatchers.Main) spins indefinitely trying to reach an idle
        // state that a live infinite-delay loop can never reach.
        viewModel.stopEphemeralCollection()
        dispatcher.scheduler.runCurrent()
    }
}
