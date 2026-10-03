package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.MachineEvent
import com.packmuleforge.carfindermvp.shared.domain.MachineSnapshot
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.domain.ParkingStateMachine
import com.packmuleforge.carfindermvp.shared.domain.SamplingPolicy
import com.packmuleforge.carfindermvp.shared.domain.Transition
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the engine publishes after every input.
 *
 * @requirement FR-018, FR-027
 */
data class EngineState(
    val lifecycle: LifecycleState = LifecycleState.FINDING,
    val parkedLocation: ParkedLocation? = null,
    val latestFix: LocationReading? = null,
    val samplingIntervalMillis: Long = CarFinderConstants.SAMPLING_INTERVAL_IDLE_MILLIS,
)

/**
 * The single-consumer actor that turns readings into lifecycle changes. Every change is persisted before it is
 * published, and the sampling interval is re-applied after every input. Only the foreground service calls [start];
 * the UI calls the read-only [restore].
 *
 * @requirement FR-003, FR-018, FR-020, FR-027, FR-029
 */
class ParkingEngine(private val adapters: PlatformAdapters, private val scope: CoroutineScope) {

    private val mutableState = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = mutableState.asStateFlow()

    private val mutableTransitions = MutableSharedFlow<Transition>(extraBufferCapacity = 64)

    /** Lifecycle changes only; a recovery correction is not emitted here. */
    val transitions: SharedFlow<Transition> = mutableTransitions.asSharedFlow()

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private var job: Job? = null
    private var windowTimer: Job? = null
    private var actorScope: CoroutineScope? = null
    private var snapshot = MachineSnapshot.INITIAL
    private var latestFix: LocationReading? = null
    private var isGuidanceVisible = false
    private var isInVehicle = false
    private var appliedIntervalMillis: Long? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Reads and publishes the stored state. Starts nothing, subscribes to nothing and writes nothing. */
    suspend fun restore() {
        if (isRunning) return
        val transition = ParkingStateMachine.reduce(MachineSnapshot.INITIAL, MachineEvent.Restored(adapters.store.read()))
        snapshot = transition.snapshot
        publish()
    }

    /** Restores, then runs the actor and sampling. A second call does nothing. */
    fun start() {
        if (isRunning) return
        job = scope.launch {
            actorScope = this
            restoreAndRepair()
            launch(start = CoroutineStart.UNDISPATCHED) {
                adapters.location.readings.collect { inputs.send(Input.Reading(it)) }
            }
            applySamplingInterval()
            adapters.location.start()
            for (input in inputs) handle(input)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        actorScope = null
        windowTimer = null
        appliedIntervalMillis = null
        adapters.location.stop()
    }

    fun setGuidanceVisible(visible: Boolean) {
        if (isRunning) inputs.trySend(Input.GuidanceVisibility(visible)) else isGuidanceVisible = visible
    }

    private suspend fun restoreAndRepair() {
        val transition = ParkingStateMachine.reduce(MachineSnapshot.INITIAL, MachineEvent.Restored(adapters.store.read()))
        if (transition.persist) {
            adapters.store.write(transition.snapshot.toRecord())
            adapters.log.record(DiagnosticEvent.RecordNormalized)
        }
        snapshot = transition.snapshot
        publish()
        scheduleWindowEnd()
    }

    private suspend fun handle(input: Input) {
        when (input) {
            is Input.Reading -> onReading(input.reading)
            is Input.GuidanceVisibility -> isGuidanceVisible = input.visible
            Input.RecoveryWindowElapsed -> {
                snapshot = ParkingStateMachine.reduce(
                    snapshot,
                    MachineEvent.RecoveryWindowElapsed(adapters.wallClock.epochMillis()),
                ).snapshot
            }
        }
        applySamplingInterval()
    }

    /**
     * Schedules one timer for the moment the recovery window closes, so the sampling interval changes then even if
     * no reading arrives (FR-024, FR-027). A correction keeps the declaration time, so the moment never moves.
     */
    private fun scheduleWindowEnd() {
        windowTimer?.cancel()
        windowTimer = null
        val parked = snapshot.parkedLocation ?: return
        val now = adapters.wallClock.epochMillis()
        if (!parked.isRecoveryOpen(now)) return
        windowTimer = actorScope?.launch {
            delay(parked.recoveryClosesAtEpochMillis - now)
            inputs.send(Input.RecoveryWindowElapsed)
        }
    }

    private suspend fun onReading(reading: LocationReading) {
        val transition = ParkingStateMachine.reduce(
            snapshot,
            MachineEvent.Reading(reading, adapters.wallClock.epochMillis()),
        )
        val locationChanged = transition.snapshot.parkedLocation != snapshot.parkedLocation
        if (transition.persist) adapters.store.write(transition.snapshot.toRecord())
        snapshot = transition.snapshot
        latestFix = reading
        publish()
        if (transition.snapshot.lifecycle != transition.from) mutableTransitions.tryEmit(transition)
        if (locationChanged) scheduleWindowEnd()
    }

    private fun applySamplingInterval() {
        val isRecoveryOpen = snapshot.parkedLocation?.isRecoveryOpen(adapters.wallClock.epochMillis()) == true
        val interval = SamplingPolicy.intervalFor(
            lifecycle = snapshot.lifecycle,
            isRecoveryOpen = isRecoveryOpen,
            isGuidanceVisible = isGuidanceVisible,
            isInVehicle = isInVehicle,
        )
        if (interval != appliedIntervalMillis) {
            appliedIntervalMillis = interval
            adapters.location.setIntervalMillis(interval)
            publish()
        }
    }

    private fun publish() {
        mutableState.value = EngineState(
            lifecycle = snapshot.lifecycle,
            parkedLocation = snapshot.parkedLocation,
            latestFix = latestFix,
            samplingIntervalMillis = appliedIntervalMillis ?: mutableState.value.samplingIntervalMillis,
        )
    }

    private sealed interface Input {
        data class Reading(val reading: LocationReading) : Input
        data class GuidanceVisibility(val visible: Boolean) : Input
        data object RecoveryWindowElapsed : Input
    }
}
