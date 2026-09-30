package com.packmuleforge.carfindermvp.shared.guidance

/**
 * Shows "Do you see your car?" once per arrival: ARMED → PROMPTING on arrival, PROMPTING → DISMISSED on an
 * answer, and DISMISSED → ARMED only after the half-angle drops back below the arrival angle. It holds no
 * reference to the engine or the store, so answering can never change the lifecycle or the Parked Location.
 * Immutable: every transition returns a new tracker.
 *
 * @requirement FR-028, FR-029
 */
data class ArrivalPromptTracker(val phase: Phase) {

    enum class Phase { ARMED, PROMPTING, DISMISSED }

    val isPromptVisible: Boolean get() = phase == Phase.PROMPTING

    fun onArrivedChanged(isArrived: Boolean): ArrivalPromptTracker = when {
        isArrived && phase == Phase.ARMED -> copy(phase = Phase.PROMPTING)
        !isArrived && phase != Phase.ARMED -> copy(phase = Phase.ARMED)
        else -> this
    }

    fun onAnswered(): ArrivalPromptTracker = if (phase == Phase.PROMPTING) copy(phase = Phase.DISMISSED) else this

    companion object {
        fun initial() = ArrivalPromptTracker(Phase.ARMED)
    }
}
