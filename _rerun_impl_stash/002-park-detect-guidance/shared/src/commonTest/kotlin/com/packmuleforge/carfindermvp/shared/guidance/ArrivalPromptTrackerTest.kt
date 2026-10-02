package com.packmuleforge.carfindermvp.shared.guidance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "Do you see your car?" prompt appears once per arrival and re-arms only after leaving the arrival zone.
 * @requirement QR-001
 */
class ArrivalPromptTrackerTest {

    private val armed = ArrivalPromptTracker.initial()

    /** @requirement FR-028 */
    @Test
    fun armed_becomesPrompting_onArrival() {
        val t = armed.onArrivedChanged(true)
        assertEquals(ArrivalPromptTracker.Phase.PROMPTING, t.phase)
        assertTrue(t.isPromptVisible)
    }

    /** @requirement FR-029 */
    @Test
    fun answering_dismissesThePrompt() {
        val t = armed.onArrivedChanged(true).onAnswered()
        assertEquals(ArrivalPromptTracker.Phase.DISMISSED, t.phase)
        assertFalse(t.isPromptVisible)
    }

    /** @requirement FR-029 */
    @Test
    fun dismissed_whileStillArrived_doesNotPromptAgain() {
        val t = armed.onArrivedChanged(true).onAnswered().onArrivedChanged(true).onArrivedChanged(true)
        assertFalse(t.isPromptVisible)
    }

    /** @requirement FR-028 */
    @Test
    fun dismissed_rearmsOnlyAfterLeavingArrival_thenPromptsOnNextArrival() {
        val left = armed.onArrivedChanged(true).onAnswered().onArrivedChanged(false)
        assertEquals(ArrivalPromptTracker.Phase.ARMED, left.phase)
        assertTrue(left.onArrivedChanged(true).isPromptVisible)
    }

    /** @requirement FR-029 */
    @Test
    fun answeringWhenNotPrompting_changesNothing() {
        assertEquals(armed, armed.onAnswered())
    }
}
