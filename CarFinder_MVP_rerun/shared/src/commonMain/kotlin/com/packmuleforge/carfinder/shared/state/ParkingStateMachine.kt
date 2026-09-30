package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.platform.Clock
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The parking state machine. Maintains exactly one parking state (DRIVING, PARKING, PARKED, FINDING)
 * and drives transitions based on observed speed and location convergence (FR-001, FR-002–FR-010).
 *
 * The state machine runs as a background operation independently of app foreground state (FR-014).
 * All state changes are persisted to the repository. [onLocationSample] is the sole mutator;
 * the machine is otherwise read-only.
 */
@Requirement("FR-001", "FR-002", "FR-003", "FR-004", "FR-005", "FR-006", "FR-007", "FR-008", "FR-009", "FR-010", "FR-010a")
class ParkingStateMachine(
    private val repository: ParkedLocationRepository,
    private val clock: Clock
) {
    private val speedSmoother = SpeedSmoother()
    private val convergenceWindow = ConvergenceWindow()
    private val stateFlow = MutableStateFlow(ParkingState.FINDING)

    fun state(): StateFlow<ParkingState> = stateFlow

    fun currentState(): ParkingState = stateFlow.value

    /**
     * Restore state from persistence on startup (FR-011).
     */
    suspend fun restore() {
        val persisted = repository.load()
        stateFlow.value = persisted.state
    }

    /**
     * Process a location sample. This is the sole mutator and the entry point for the service
     * (T040). Evaluates speed thresholds and convergence, driving state transitions (FR-002–FR-010).
     */
    suspend fun onLocationSample(sample: LocationSample) {
        val smoothedSpeed = speedSmoother.smooth(sample.speedMetersPerSecond)

        val nextState = when {
            // FR-002: Speed above driving threshold → always enter DRIVING
            smoothedSpeed > ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS -> {
                handleDriveAway()
                ParkingState.DRIVING
            }

            // FR-003: From DRIVING, speed at or below parking threshold → enter PARKING
            currentState() == ParkingState.DRIVING &&
                smoothedSpeed <= ParkingConstants.PARKING_SPEED_THRESHOLD_MPS -> {
                convergenceWindow.clear()
                ParkingState.PARKING
            }

            // FR-006, FR-007: In PARKING, check for convergence
            currentState() == ParkingState.PARKING -> {
                convergenceWindow.add(sample)
                if (convergenceWindow.isConverged()) {
                    val centroid = convergenceWindow.centroid()
                    val location = ParkedLocation(
                        point = centroid,
                        capturedAtEpochMillis = clock.nowEpochMillis()
                    )
                    repository.save(ParkingState.PARKED, location)
                    ParkingState.PARKED
                } else {
                    // FR-008: No timeout, no failure state, just slide the window
                    currentState()
                }
            }

            // FR-004: Dead zone (above parking threshold, at or below driving threshold)
            // No transition in either direction
            else -> currentState()
        }

        if (nextState != currentState()) {
            stateFlow.value = nextState
            repository.save(nextState, repository.load().parkedLocation)
        }
    }

    /**
     * FR-010: Handle drive-away. Delete the Parked Location and enter DRIVING.
     * If in FINDING, the deletion is a no-op (not an error).
     */
    private suspend fun handleDriveAway() {
        val currentData = repository.load()
        if (currentData.parkedLocation != null) {
            repository.clearLocation()
        }
    }
}
