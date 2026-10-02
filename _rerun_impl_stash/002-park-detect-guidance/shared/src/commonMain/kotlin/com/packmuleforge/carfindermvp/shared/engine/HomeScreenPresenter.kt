package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import com.packmuleforge.carfindermvp.shared.guidance.ArrivalPromptTracker
import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometryCalculator
import com.packmuleforge.carfindermvp.shared.guidance.DefaultViewSelector
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceAvailability
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.guidance.ViewKind
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource
import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * Computes the single home-screen state from the engine, the compass and the clock. All view selection and
 * guidance geometry happen here, never in the UI (QR-009). A ticker re-checks fix and heading currency, so a feed
 * that silently stops is caught without a new event (FR-034, SC-010). The ticker only runs while the UI collects.
 *
 * @requirement FR-016, FR-017, FR-027, FR-028, FR-029, FR-031, FR-034, QR-009
 */
class HomeScreenPresenter(
    engine: ParkingEngine,
    private val heading: HeadingSource,
    private val clock: MonotonicClock,
    scope: CoroutineScope,
) {
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TuningConstants.AVAILABILITY_RECHECK_INTERVAL_MILLIS)
        }
    }

    private val engineState = engine.state.onEach { state -> state.latestFix?.let(heading::updateDeclinationFrom) }

    private val answers = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    private sealed interface Event {
        data class Computed(val state: HomeScreenState) : Event
        data object Answered : Event
    }

    private data class Accumulator(val computed: HomeScreenState, val tracker: ArrivalPromptTracker)

    private val computed =
        combine(engineState, heading.headings, ticker) { engineState, latestHeading, _ -> compute(engineState, latestHeading) }
            .distinctUntilChanged()

    // The arrival prompt is one sequential fold over guidance updates and answers, so an answer from the UI thread
    // never races a guidance update. Losing guidance (no fix or heading) leaves the tracker unchanged: only a
    // half-angle that drops below the arrival angle re-arms the prompt (spec Edge Cases).
    val state: StateFlow<HomeScreenState> =
        merge(computed.map { Event.Computed(it) }, answers.map { Event.Answered })
            .scan(Accumulator(HomeScreenState.Unavailable, ArrivalPromptTracker.initial())) { acc, event ->
                when (event) {
                    is Event.Computed -> {
                        val guidance = event.state as? HomeScreenState.Guidance
                        val tracker = if (guidance != null) acc.tracker.onArrivedChanged(guidance.isArrived) else acc.tracker
                        Accumulator(event.state, tracker)
                    }
                    Event.Answered -> acc.copy(tracker = acc.tracker.onAnswered())
                }
            }
            .map { acc ->
                val guidance = acc.computed as? HomeScreenState.Guidance
                guidance?.copy(isArrivalPromptVisible = acc.tracker.isPromptVisible) ?: acc.computed
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), HomeScreenState.Unavailable)

    /**
     * Yes or No only dismisses the prompt. It never touches the engine or the store, so the state stays PARKED and
     * the Parked Location is kept.
     *
     * @requirement FR-029
     */
    fun onArrivalAnswered(@Suppress("UNUSED_PARAMETER") sawCar: Boolean) {
        answers.tryEmit(Unit)
    }

    private fun compute(engineState: EngineState, latestHeading: HeadingReading?): HomeScreenState {
        val parked = engineState.parkedLocation
        val fix = engineState.latestFix
        val available = GuidanceAvailability.isAvailable(
            engineState.lifecycle, parked, fix, latestHeading, clock.elapsedRealtimeMillis(),
        )
        return when (DefaultViewSelector.select(engineState.lifecycle, available)) {
            ViewKind.DRIVING -> HomeScreenState.Driving
            ViewKind.UNAVAILABLE -> HomeScreenState.Unavailable
            ViewKind.PARKING -> HomeScreenState.Parking
            ViewKind.GUIDANCE -> {
                val guidance = GuidanceCalculator.compute(parked!!, fix!!, latestHeading!!)
                HomeScreenState.Guidance(
                    cone = ConeGeometryCalculator.compute(guidance.displayBearingDegrees, guidance.coneHalfAngleDegrees),
                    distance = guidance.distanceDisplay,
                    isArrived = guidance.isArrived,
                    isArrivalPromptVisible = false, // set from the prompt tracker in [state]
                )
            }
        }
    }
}
