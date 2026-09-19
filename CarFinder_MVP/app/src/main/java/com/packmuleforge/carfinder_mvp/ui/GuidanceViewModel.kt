package com.packmuleforge.carfinder_mvp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.platform.HeadingProvider
import com.packmuleforge.carfinder.shared.platform.LocationProvider
import com.packmuleforge.carfinder.shared.platform.LocationRequestTier
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.view.GuidanceViewStateCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel that orchestrates the guidance display. Combines:
 * - Slow persisted path: parking state + location (from repository)
 * - Fast ephemeral path: current fix + heading (from providers)
 *
 * All calculation is delegated to GuidanceViewStateCalculator (pure function, no I/O).
 * No `when`, `atan`, unit conversion, or threshold comparison here (FR-042).
 */
@Requirement("FR-017", "FR-018", "FR-042", "SC-004")
class GuidanceViewModel(
    private val repository: ParkedLocationRepository,
    private val locationProvider: LocationProvider,
    private val headingProvider: HeadingProvider
) : ViewModel() {

    private val _viewState = MutableStateFlow<GuidanceViewState>(GuidanceViewState.NoParkedLocation)
    val viewState: StateFlow<GuidanceViewState> = _viewState

    init {
        viewModelScope.launch {
            // Combine the slow persisted path and fast ephemeral path
            combine(
                repository.observe(),
                locationProvider.samples(LocationRequestTier.PARKING),
                headingProvider.headingDegrees()
            ) { persistedData, currentFix, deviceHeading ->
                // Calculate view state using the pure calculator
                GuidanceViewStateCalculator.calculate(
                    parkingState = persistedData.state,
                    parkedLocation = persistedData.parkedLocation,
                    currentFix = currentFix.point,
                    deviceHeading = deviceHeading,
                    currentHeading = currentFix.speedMetersPerSecond  // Repurpose for angle updates
                )
            }.collect { viewState ->
                _viewState.value = viewState
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Flow collection is automatically cancelled when viewModelScope is cleared
    }
}
