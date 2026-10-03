package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.guidance.DefaultViewSelector
import com.packmuleforge.carfindermvp.shared.guidance.GuidanceCalculator
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.guidance.ViewKind
import com.packmuleforge.carfindermvp.shared.platform.DenialConfirmation
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Turns engine state, heading, permissions and the passage of time into the one [HomeScreenState] the UI draws.
 * While the screen is visible a tick re-checks fix and heading age, so staleness shows without a new event.
 *
 * @requirement FR-040, FR-041, FR-042, FR-044, QR-013
 */
class HomeScreenPresenter(
    private val engine: ParkingEngine,
    private val adapters: PlatformAdapters,
    private val scope: CoroutineScope,
) {
    private val denial = MutableStateFlow<DenialConfirmation?>(null)
    private val tick = MutableStateFlow(0L)
    private var tickJob: Job? = null
    private var isVisible = false

    val state: StateFlow<HomeScreenState> =
        combine(engine.state, adapters.heading.heading, adapters.permissions.status, denial, tick) {
                engineState, heading, permissions, pendingDenial, _ ->
            compute(engineState, heading, permissions, pendingDenial)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            compute(engine.state.value, adapters.heading.heading.value, adapters.permissions.status.value, null),
        )

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
                HomeScreenState.Guidance(guidance.cone, guidance.distanceText)
            }
        }
    }
}
