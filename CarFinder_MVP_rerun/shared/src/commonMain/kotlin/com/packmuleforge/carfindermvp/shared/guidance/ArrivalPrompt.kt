package com.packmuleforge.carfindermvp.shared.guidance

/**
 * The "Do you see your car?" prompt: armed, prompting, or dismissed. It re-arms only when a guidance state below
 * the arrival half-angle is computed, so rotation and unavailable spells never bring it back. It has no effect on
 * the lifecycle or the Parked Location.
 *
 * @requirement FR-039
 */
data class ArrivalPrompt(val phase: Phase = Phase.ARMED) {

    enum class Phase { ARMED, PROMPTING, DISMISSED }

    val isPromptVisible: Boolean get() = phase == Phase.PROMPTING

    /** Called for every computed guidance state; unavailable spells compute none and leave the prompt alone. */
    fun onGuidance(isArrived: Boolean): ArrivalPrompt = when {
        isArrived && phase == Phase.ARMED -> ArrivalPrompt(Phase.PROMPTING)
        !isArrived && phase != Phase.ARMED -> ArrivalPrompt(Phase.ARMED)
        else -> this
    }

    /** Yes and No both only dismiss the prompt. */
    fun onAnswer(): ArrivalPrompt = if (phase == Phase.PROMPTING) ArrivalPrompt(Phase.DISMISSED) else this
}
