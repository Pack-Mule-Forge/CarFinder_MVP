package com.packmuleforge.carfindermvp.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.unit.Dp

/** Test tags for the home screen (contracts/guidance-ui.md). */
object TestTags {
    const val STATUS_MESSAGE = "status_message"
    const val GUIDANCE_CONE = "guidance_cone"
    const val PERSON_ICON = "person_icon"
    const val CAR_ICON = "car_icon"
    const val DISTANCE_TEXT = "distance_text"
    const val ARRIVAL_MESSAGE = "arrival_message"
    const val ARRIVAL_PROMPT = "arrival_prompt"
    const val ARRIVAL_YES = "arrival_yes"
    const val ARRIVAL_NO = "arrival_no"
}

/** What the cone rendered, exposed so tests verify geometry numerically instead of by screenshot. */
val ConeHalfAngleDegrees = SemanticsPropertyKey<Double>("ConeHalfAngleDegrees")
var SemanticsPropertyReceiver.coneHalfAngleDegrees by ConeHalfAngleDegrees

val ConeDisplayBearingDegrees = SemanticsPropertyKey<Double>("ConeDisplayBearingDegrees")
var SemanticsPropertyReceiver.coneDisplayBearingDegrees by ConeDisplayBearingDegrees

val ConeDrawSize = SemanticsPropertyKey<Dp>("ConeDrawSize")
var SemanticsPropertyReceiver.coneDrawSize by ConeDrawSize
