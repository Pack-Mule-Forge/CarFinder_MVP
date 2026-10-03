package com.packmuleforge.carfinder_mvp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.platform.Clock
import com.packmuleforge.carfinder.shared.platform.HeadingProvider
import com.packmuleforge.carfinder.shared.platform.LocationProvider
import com.packmuleforge.carfinder.shared.platform.LocationRequestTier
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.view.GuidanceViewStateCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val AGE_TICK_INTERVAL_MILLIS = 1_000L

/**
 * ViewModel that orchestrates the guidance display. Combines:
 * - Slow persisted path: parking state + location (from repository) — runs for the ViewModel's
 *   whole lifetime, independent of view visibility (FR-014).
 * - Fast ephemeral path: current fix + heading + an age ticker (from providers) — runs only
 *   while the default view is STARTED (FR-044); started/stopped by the Composable.
 *
 * All calculation is delegated to GuidanceViewStateCalculator (pure function, no I/O).
 * No `when`, `atan`, unit conversion, or threshold comparison here (FR-042).
 */
@Requirement("FR-017", "FR-018", "FR-042", "FR-043", "FR-044", "SC-004")
class GuidanceViewModel(
    private val repository: ParkedLocationRepository,
    private val locationProvider: LocationProvider,
    private val headingProvider: HeadingProvider,
    private val clock: Clock
) : ViewModel() {

    private val persistedState = MutableStateFlow(ParkingState.FINDING)
    private val persistedLocation = MutableStateFlow<ParkedLocation?>(null)

    // FR-044: seeded with null so the combined view state computes before the first ephemeral
    // fix/heading arrives, showing NoParkedLocation instead of a blank screen.
    private val currentFix = MutableStateFlow<LocationSample?>(null)
    private val deviceHeading = MutableStateFlow<Double?>(null)
    private val nowMillis = MutableStateFlow(clock.nowEpochMillis())

    private val _viewState = MutableStateFlow<GuidanceViewState>(GuidanceViewState.NoParkedLocation)
    val viewState: StateFlow<GuidanceViewState> = _viewState

    // T117/FR-033: local presentation state only — answering the arrival prompt must not touch
    // parkingState/parkedLocation, so this lives here rather than in GuidanceViewStateCalculator.
    // Reset back to false as soon as hasArrived goes false, per the spec's Assumptions ("not
    // shown again until the user leaves the arrival zone ... and returns").
    private val _arrivalPromptDismissed = MutableStateFlow(false)
    val arrivalPromptDismissed: StateFlow<Boolean> = _arrivalPromptDismissed

    private var ephemeralJob: Job? = null

    init {
        // Persisted path: always running, independent of view visibility (FR-014).
        viewModelScope.launch {
            repository.observe().collect { persisted ->
                persistedState.value = persisted.state
                persistedLocation.value = persisted.parkedLocation
            }
        }

        viewModelScope.launch {
            combine(
                persistedState,
                persistedLocation,
                currentFix,
                deviceHeading,
                nowMillis
            ) { state, location, fix, heading, now ->
                // FR-043: age is measured from the fix's own timestamp against the shared Clock,
                // re-evaluated on every tick so a fix that goes stale with no new sample is caught.
                GuidanceViewStateCalculator.calculate(
                    parkingState = state,
                    parkedLocation = location,
                    currentFix = fix?.point,
                    currentFixAgeMillis = fix?.let { now - it.timestampEpochMillis },
                    deviceHeading = heading
                )
            }.collect { _viewState.value = it }
        }

        // FR-033/Assumptions: once arrival is left (half-angle drops back below
        // ARRIVAL_CONE_HALF_ANGLE), the prompt becomes eligible to show again.
        viewModelScope.launch {
            viewState.collect { state ->
                val arrived = (state as? GuidanceViewState.Guidance)?.hasArrived == true
                if (!arrived) _arrivalPromptDismissed.value = false
            }
        }
    }

    /**
     * FR-033: dismiss the arrival prompt on either answer. Local UI presentation state only —
     * does not touch parkingState or the Parked Location. [yesClicked] is accepted to match the
     * prompt's answer callback shape but is otherwise unused: both answers dismiss identically.
     */
    fun onArrivalAnswered(yesClicked: Boolean) {
        _arrivalPromptDismissed.value = true
    }

    /**
     * FR-044: begin collecting the ephemeral location, heading, and staleness-ticker inputs.
     * Call when the default view becomes STARTED. Idempotent while already started.
     */
    fun startEphemeralCollection() {
        if (ephemeralJob != null) return
        ephemeralJob = viewModelScope.launch {
            launch {
                locationProvider.samples(LocationRequestTier.PARKING).collect { currentFix.value = it }
            }
            launch {
                headingProvider.headingDegrees().collect { deviceHeading.value = it }
            }
            launch {
                while (true) {
                    nowMillis.value = clock.nowEpochMillis()
                    delay(AGE_TICK_INTERVAL_MILLIS)
                }
            }
        }
    }

    /**
     * FR-044: stop collecting the ephemeral inputs. Call when the default view leaves STARTED.
     * The persisted path in [init] is unaffected.
     */
    fun stopEphemeralCollection() {
        ephemeralJob?.cancel()
        ephemeralJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopEphemeralCollection()
    }
}
