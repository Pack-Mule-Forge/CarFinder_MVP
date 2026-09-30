package com.packmuleforge.carfinder_mvp.ui

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T117/FR-033 (escalated finding R1-G2): answering the arrival prompt must dismiss it, and it
 * must not reappear while arrival continues to hold, but must become eligible again once the
 * user leaves the arrival zone (half-angle drops back below ARRIVAL_CONE_HALF_ANGLE) and returns
 * (spec.md Assumptions). The dismiss flag lives in GuidanceViewModel rather than in the
 * Composable so it survives recomposition/rotation, matching the persisted-elsewhere pattern
 * already used for the rest of this ViewModel's state.
 */
@Requirement("FR-031", "FR-032", "FR-033")
class GuidanceArrivalPromptDismissalTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val parkedPoint = GeoPoint(latitudeDegrees = 37.0, longitudeDegrees = -122.0, accuracyRadiusMeters = 5.0)
    private val parkedLocation = ParkedLocation(point = parkedPoint, capturedAtEpochMillis = 0L)

    // Same point as parkedLocation -> distance 0 -> half-angle pi/2 -> hasArrived == true.
    private val arrivedFix = LocationSample(
        point = parkedPoint,
        speedMetersPerSecond = 0.0,
        timestampEpochMillis = 0L
    )

    // ~1.1 km away with a small accuracy radius -> half-angle well under the arrival threshold.
    private val farFix = LocationSample(
        point = GeoPoint(latitudeDegrees = 37.01, longitudeDegrees = -122.0, accuracyRadiusMeters = 5.0),
        speedMetersPerSecond = 0.0,
        timestampEpochMillis = 0L
    )

    private class ControllableLocationProvider : LocationProvider {
        val currentSample = MutableStateFlow<LocationSample?>(null)

        override fun samples(request: LocationRequestTier): Flow<LocationSample> = flow {
            currentSample.collect { sample -> if (sample != null) emit(sample) }
        }
    }

    private class FixedHeadingProvider(private val degrees: Double) : HeadingProvider {
        override fun headingDegrees(): Flow<Double> = flow {
            emit(degrees)
            awaitCancellation()
        }
    }

    private class ParkedRepository(private val location: ParkedLocation) : ParkedLocationRepository {
        override fun observe(): Flow<PersistedParkingData> = flow {
            emit(PersistedParkingData(ParkingState.PARKED, location))
            awaitCancellation()
        }

        override suspend fun load(): PersistedParkingData = PersistedParkingData(ParkingState.PARKED, location)
        override suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?) = Unit
        override suspend fun clearLocation() = Unit
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun nowEpochMillis(): Long = millis
    }

    @Test
    fun answeringDismissesPromptUntilArrivalIsLeftAndReentered() = runTest {
        val locationProvider = ControllableLocationProvider()
        val viewModel = GuidanceViewModel(
            repository = ParkedRepository(parkedLocation),
            locationProvider = locationProvider,
            headingProvider = FixedHeadingProvider(degrees = 0.0),
            clock = FixedClock(0L)
        )

        viewModel.startEphemeralCollection()
        dispatcher.scheduler.runCurrent()

        // Arrive: prompt is eligible to show (not dismissed).
        locationProvider.currentSample.value = arrivedFix
        dispatcher.scheduler.runCurrent()
        assertTrue((viewModel.viewState.value as GuidanceViewState.Guidance).hasArrived, "expected arrival")
        assertFalse(viewModel.arrivalPromptDismissed.value, "prompt should be showing on first arrival")

        // Answer (either answer dismisses, per FR-033).
        viewModel.onArrivalAnswered(true)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.arrivalPromptDismissed.value, "prompt must dismiss once answered")

        // Still arrived: must not reappear while hasArrived stays true.
        locationProvider.currentSample.value = arrivedFix.copy(timestampEpochMillis = 1L)
        dispatcher.scheduler.runCurrent()
        assertTrue((viewModel.viewState.value as GuidanceViewState.Guidance).hasArrived, "still in arrival range")
        assertTrue(viewModel.arrivalPromptDismissed.value, "prompt must stay dismissed while still arrived")

        // Leave arrival range: half-angle drops back below the threshold.
        locationProvider.currentSample.value = farFix
        dispatcher.scheduler.runCurrent()
        assertFalse((viewModel.viewState.value as GuidanceViewState.Guidance).hasArrived, "expected to leave arrival range")
        assertFalse(viewModel.arrivalPromptDismissed.value, "leaving arrival range resets dismissal")

        // Re-enter arrival range: prompt is eligible to show again.
        locationProvider.currentSample.value = arrivedFix.copy(timestampEpochMillis = 2L)
        dispatcher.scheduler.runCurrent()
        assertTrue((viewModel.viewState.value as GuidanceViewState.Guidance).hasArrived, "expected to re-enter arrival range")
        assertFalse(viewModel.arrivalPromptDismissed.value, "prompt must be shown again on re-arrival")

        viewModel.stopEphemeralCollection()
        dispatcher.scheduler.runCurrent()
    }
}
