package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.guidance.ArrivalPrompt
import com.packmuleforge.carfindermvp.shared.guidance.DefaultViewSelector
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.guidance.ViewKind
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.DenialConfirmation
import com.packmuleforge.carfindermvp.shared.platform.PermissionSequenceResult
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.platform.requestPermissionsInOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Turns engine state, heading, permissions and the passage of time into the one [HomeScreenState] the UI draws.
 * While the screen is visible a tick re-checks fix and heading age, so staleness shows without a new event.
 *
 * @requirement FR-040, FR-041, FR-042, FR-044, FR-049, QR-013
 */
class HomeScreenPresenter(
    private val engine: ParkingEngine,
    private val adapters: PlatformAdapters,
    private val scope: CoroutineScope,
) {
    private val denial = MutableStateFlow<DenialConfirmation?>(null)
    private val arrivalPrompt = MutableStateFlow(ArrivalPrompt())
    private val tick = MutableStateFlow(0L)
    private var tickJob: Job? = null
    private var isVisible = false

    val state: StateFlow<HomeScreenState> =
        combine(
            combine(engine.state, adapters.heading.heading, adapters.permissions.status, ::Triple),
            combine(denial, arrivalPrompt, tick, ::Triple),
        ) { (engineState, heading, permissions), (pendingDenial, _, _) ->
            compute(engineState, heading, permissions, pendingDenial)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            compute(engine.state.value, adapters.heading.heading.value, adapters.permissions.status.value, null),
        )

    /** Yes and No both only dismiss the prompt; the lifecycle and the Parked Location are untouched (FR-039). */
    fun onArrivalAnswered() {
        arrivalPrompt.update { it.onAnswer() }
    }

    /** Starts or stops the heading source and the staleness tick, and tells the engine (FR-027 row 3). */
    fun onGuidanceVisible(visible: Boolean) {
        if (visible == isVisible) return
        isVisible = visible
        if (visible) {
            adapters.heading.start()
            tickJob = scope.launch {
                while (true) {
                    delay(CarFinderConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS)
                    tick.value++
                }
            }
        } else {
            tickJob?.cancel()
            tickJob = null
            adapters.heading.stop()
        }
        engine.setGuidanceVisible(visible)
    }

    private val closePending = MutableStateFlow(false)

    /**
     * Raised [CarFinderConstants.SHUTDOWN_NOTICE_DURATION_MILLIS] after the user confirmed a denial, and held until
     * [onClosed]. It is state rather than an event, so an Activity that was stopped when it rose still sees it on
     * its next start (FR-056).
     */
    val isClosePending: StateFlow<Boolean> = closePending

    @Volatile
    private var sequenceJob: Job? = null

    @Volatile
    private var answer: CompletableDeferred<Boolean>? = null

    /**
     * Runs the FR-048 request sequence with the FR-056 confirmation. Does nothing while a sequence is running or a
     * close is pending.
     *
     * @requirement FR-048, FR-056
     */
    fun runPermissionSequence() {
        if (sequenceJob?.isActive == true || closePending.value) return
        sequenceJob = scope.launch {
            try {
                val result = requestPermissionsInOrder(adapters.permissions, ::awaitDenialAnswer)
                if (result is PermissionSequenceResult.CloseConfirmed) {
                    delay(CarFinderConstants.SHUTDOWN_NOTICE_DURATION_MILLIS)
                    closePending.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A platform failure ends this sequence; the next start asks again (FR-049: no crash).
                denial.value = null
            }
        }
    }

    /** "Close": the user accepts the denial; the app shows the closing message and then closes (FR-056 a). */
    fun onDenialConfirmed() {
        answer?.complete(true)
    }

    /** "Allow", back or a tap outside: the same permission is requested again (FR-056 b). */
    fun onDenialDismissed() {
        answer?.complete(false)
    }

    /** The Activity has closed. Nothing about the denial may carry over to the next launch (FR-056). */
    fun onClosed() = clearDenialState()

    /** The screen was finished by other means while a sequence was running. */
    fun cancelPermissionSequence() = clearDenialState()

    private fun clearDenialState() {
        sequenceJob?.cancel()
        sequenceJob = null
        answer = null
        denial.value = null
        closePending.value = false
    }

    private suspend fun awaitDenialAnswer(capability: Capability): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred
        denial.value = DenialConfirmation(capability, isClosing = false)
        val confirmed = try {
            deferred.await()
        } finally {
            if (answer === deferred) answer = null
        }
        denial.value = if (confirmed) DenialConfirmation(capability, isClosing = true) else null
        return confirmed
    }

    private fun compute(
        engineState: EngineState,
        heading: HeadingReading?,
        permissions: PermissionState,
        pendingDenial: DenialConfirmation?,
    ): HomeScreenState {
        val now = adapters.monotonicClock.elapsedRealtimeMillis()
        val kind = DefaultViewSelector.select(
            denial = pendingDenial,
            permissions = permissions,
            lifecycle = engineState.lifecycle,
            parked = engineState.parkedLocation,
            fix = engineState.latestFix,
            heading = heading,
            nowElapsedMillis = now,
        )
        return when (kind) {
            is ViewKind.PermissionRequired -> HomeScreenState.PermissionRequired(kind.capability)
            ViewKind.Closing -> HomeScreenState.Closing
            ViewKind.Unavailable -> HomeScreenState.Unavailable
            ViewKind.Driving -> HomeScreenState.Driving
            ViewKind.Parking -> HomeScreenState.Parking
            ViewKind.Guidance -> {
                val guidance = GuidanceCalculator.compute(
                    checkNotNull(engineState.parkedLocation),
                    checkNotNull(engineState.latestFix),
                    checkNotNull(heading),
                )
                val prompt = arrivalPrompt.updateAndGet { it.onGuidance(guidance.isArrived) }
                if (guidance.isArrived) {
                    HomeScreenState.Arrived(isPromptVisible = prompt.isPromptVisible)
                } else {
                    HomeScreenState.Guidance(guidance.cone, guidance.distanceText)
                }
            }
        }
    }
}
