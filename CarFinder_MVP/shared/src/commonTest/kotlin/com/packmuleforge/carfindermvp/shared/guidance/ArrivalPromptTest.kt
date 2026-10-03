package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ArrivalPromptTest {

    private val armed = ArrivalPrompt()

    /** @requirement FR-039 */
    @Test
    fun armedBecomesPromptingWhenArrivalBegins() {
        assertFalse(armed.isPromptVisible)
        assertEquals(ArrivalPrompt.Phase.ARMED, armed.onGuidance(isArrived = false).phase)
        val prompting = armed.onGuidance(isArrived = true)
        assertEquals(ArrivalPrompt.Phase.PROMPTING, prompting.phase)
        assertTrue(prompting.isPromptVisible)
    }

    /** @requirement FR-039 */
    @Test
    fun promptingBecomesDismissedOnAnAnswer() {
        val dismissed = armed.onGuidance(isArrived = true).onAnswer()
        assertEquals(ArrivalPrompt.Phase.DISMISSED, dismissed.phase)
        assertFalse(dismissed.isPromptVisible)
    }

    /** @requirement FR-039 */
    @Test
    fun dismissedStaysDismissedWhileArrivalHoldsAndAcrossSpellsWithNoGuidance() {
        var prompt = armed.onGuidance(isArrived = true).onAnswer()
        repeat(5) { prompt = prompt.onGuidance(isArrived = true) }
        // An unavailable spell produces no guidance state, so the tracker is simply not updated.
        prompt = prompt.onGuidance(isArrived = true)
        assertEquals(ArrivalPrompt.Phase.DISMISSED, prompt.phase)
    }

    /** @requirement FR-039 */
    @Test
    fun dismissedReArmsOnlyAfterANonArrivedGuidanceStateAndThenPromptsAgain() {
        val dismissed = armed.onGuidance(isArrived = true).onAnswer()
        val rearmed = dismissed.onGuidance(isArrived = false)
        assertEquals(ArrivalPrompt.Phase.ARMED, rearmed.phase)
        assertEquals(ArrivalPrompt.Phase.PROMPTING, rearmed.onGuidance(isArrived = true).phase)
    }

    /** @requirement FR-039 */
    @Test
    fun anAnswerWhenNotPromptingChangesNothing() {
        assertEquals(armed, armed.onAnswer())
    }
}
