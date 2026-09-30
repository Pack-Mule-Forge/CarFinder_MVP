package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.MachineEvent
import com.packmuleforge.carfindermvp.shared.domain.MachineSnapshot
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.ParkingStateMachine
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.domain.Transition
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EngineState(
    val lifecycle: LifecycleState,
    val parkedLocation: ParkedLocation?,
    val samplingProfile: SamplingProfile,
    val latestFix: LocationReading?,
)

/**
 * Runs the parking lifecycle: a single-consumer actor that feeds platform inputs through [ParkingStateMachine],
 * persists every change before publishing it, and keeps the one location subscription on the right sampling
 * profile (research R2, R7).
 *
 * Two entry points with different powers:
 * - [start] runs detection: the actor loop, location sampling and activity recognition. Only the detection
 *   service's startup path may call it.
 * - [restore] is read-only, for the UI: it publishes the persisted state for display and never starts sampling,
 *   subscribes to anything, launches the actor, or writes the store.
 *
 * @requirement FR-002, FR-006, FR-012, FR-014, FR-018, FR-033
 */
class ParkingEngine(
    private val adapters: PlatformAdapters,
    private val scope: CoroutineScope,
) {
    private sealed interface EngineInput {
        data object Restore : EngineInput
        data class LocationReceived(val reading: LocationReading) : EngineInput
        data object ActivityInVehicle : EngineInput
        data class GuidanceVisibility(val visible: Boolean) : EngineInput
    }

    private val inputs = Channel<EngineInput>(Channel.UNLIMITED)
    private val running = MutableStateFlow(false)
    private var jobs = emptyList<Job>()

    // Touched only by the actor coroutine.
    private var snapshot = MachineSnapshot.initial()
    private var restored = false
    private var guidanceVisible = false
    private var inVehicleHint = false
    private var latestFix: LocationReading? = null
    private var requestedProfile: SamplingProfile? = null

    private val _state = MutableStateFlow(
        EngineState(LifecycleState.FINDING, null, SamplingProfile.IDLE_WATCH, null),
    )
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _transitions = MutableSharedFlow<Transition>(extraBufferCapacity = 64)

    /** Every lifecycle change, for future telemetry and parking-history consumers. */
    val transitions: SharedFlow<Transition> = _transitions.asSharedFlow()

    /** True once [start] has run and until [stop]. */
    val isRunning: Boolean get() = running.value

    /**
     * Read-only restore for display: loads the persisted record and publishes it through [state]. It never starts
     * location sampling, activity recognition or the actor loop, and never writes the store, even when the record
     * needs normalizing (the running engine rewrites it). Once the engine is running its state is authoritative,
     * so this has no effect.
     *
     * @requirement FR-014, FR-018
     */
    suspend fun restore() {
        if (running.value) return
        val record = adapters.store.read().normalized()
        // update() retries on contention; a running engine's published state always wins.
        _state.update { current ->
            if (running.value) current else current.copy(lifecycle = record.state, parkedLocation = record.parkedLocation)
        }
    }

    /**
     * Restores persisted state and starts detection: the actor loop, location sampling and activity recognition.
     * Only ParkingDetectionService's startup path calls this. Calling it again while running has no effect.
     */
    fun start() {
        if (!running.compareAndSet(expect = false, update = true)) return
        inputs.trySend(EngineInput.Restore)
        jobs = listOf(
            scope.launch { for (input in inputs) handle(input) },
            scope.launch { adapters.location.readings.collect { inputs.send(EngineInput.LocationReceived(it)) } },
            scope.launch { adapters.activity.inVehicleEntered.collect { inputs.send(EngineInput.ActivityInVehicle) } },
        )
        adapters.location.start()
        adapters.activity.start()
    }

    fun stop() {
        if (!running.compareAndSet(expect = true, update = false)) return
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        adapters.location.stop()
        adapters.activity.stop()
    }

    fun setGuidanceVisible(visible: Boolean) {
        inputs.trySend(EngineInput.GuidanceVisibility(visible))
    }

    private suspend fun handle(input: EngineInput) {
        when (input) {
            EngineInput.Restore -> apply(
                ParkingStateMachine.reduce(snapshot, MachineEvent.Restored(adapters.store.read())),
                isLifecycleChange = false,
            )

            is EngineInput.LocationReceived -> {
                latestFix = input.reading
                val event = MachineEvent.Reading(input.reading, adapters.wallClock.epochMillis())
                apply(ParkingStateMachine.reduce(snapshot, event), isLifecycleChange = true)
            }

            EngineInput.ActivityInVehicle -> {
                inVehicleHint = true
                if (restored) publish()
            }

            // Visibility can be queued by the UI before the service starts the engine. Until the restore has been
            // processed, only record it, so the blank initial snapshot is never published over the restored state.
            is EngineInput.GuidanceVisibility -> {
                guidanceVisible = input.visible
                if (restored) publish()
            }
        }
    }

    private suspend fun apply(transition: Transition, isLifecycleChange: Boolean) {
        if (transition.persist) {
            adapters.store.write(PersistedParkingRecord(state = transition.to, parkedLocation = transition.parkedLocation))
        }
        snapshot = transition.snapshot
        restored = true
        // The IN_VEHICLE hint only lasts until the next lifecycle change (research R2).
        if (transition.from != transition.to) inVehicleHint = false
        publish()
        if (isLifecycleChange && transition.from != transition.to) _transitions.emit(transition)
    }

    /** Re-requests the sampling profile if it changed, then publishes the current state. */
    private fun publish() {
        val profile = profileFor(snapshot.lifecycle)
        if (profile != requestedProfile) {
            requestedProfile = profile
            adapters.location.setProfile(profile)
        }
        _state.value = EngineState(snapshot.lifecycle, snapshot.parkedLocation, profile, latestFix)
    }

    /**
     * The GUIDANCE profile applies only while PARKED and the guidance screen is visible. An IN_VEHICLE activity hint
     * raises IDLE_WATCH to the DRIVING rate so drive-away is detected sooner; it never changes the lifecycle and
     * never pauses sampling (research R2).
     */
    private fun profileFor(lifecycle: LifecycleState): SamplingProfile {
        val base = when (lifecycle) {
            LifecycleState.PARKING -> SamplingProfile.PARKING
            LifecycleState.DRIVING -> SamplingProfile.DRIVING
            LifecycleState.PARKED -> if (guidanceVisible) SamplingProfile.GUIDANCE else SamplingProfile.IDLE_WATCH
            LifecycleState.FINDING -> SamplingProfile.IDLE_WATCH
        }
        return if (base == SamplingProfile.IDLE_WATCH && inVehicleHint) SamplingProfile.DRIVING else base
    }
}
