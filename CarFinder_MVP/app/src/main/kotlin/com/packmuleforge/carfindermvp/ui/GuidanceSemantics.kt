package com.packmuleforge.carfindermvp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Semantics properties that let tests assert what is drawn, not only internal state (QR-006). */
object GuidanceSemantics {
    val HalfAngleDegrees = SemanticsPropertyKey<Double>("HalfAngleDegrees")
    val DisplayBearingDegrees = SemanticsPropertyKey<Double>("DisplayBearingDegrees")
    val ApexAnchor = SemanticsPropertyKey<Offset>("ApexAnchor")
    val CarAnchor = SemanticsPropertyKey<Offset>("CarAnchor")
    val IsCenterlineDrawn = SemanticsPropertyKey<Boolean>("IsCenterlineDrawn")
}

var SemanticsPropertyReceiver.halfAngleDegrees by GuidanceSemantics.HalfAngleDegrees
var SemanticsPropertyReceiver.displayBearingDegrees by GuidanceSemantics.DisplayBearingDegrees
var SemanticsPropertyReceiver.apexAnchor by GuidanceSemantics.ApexAnchor
var SemanticsPropertyReceiver.carAnchor by GuidanceSemantics.CarAnchor
var SemanticsPropertyReceiver.isCenterlineDrawn by GuidanceSemantics.IsCenterlineDrawn

/** Node tags (contracts/guidance-ui.md). */
object UiTags {
    const val STATUS_MESSAGE = "status-message"
    const val GUIDANCE_DISPLAY = "guidance-display"
    const val GUIDANCE_CONE = "guidance-cone"
    const val PERSON_ICON = "person-icon"
    const val CAR_ICON = "car-icon"
    const val DISTANCE_TEXT = "distance-text"
    const val ARRIVAL_MESSAGE = "arrival-message"
    const val ARRIVAL_PROMPT = "arrival-prompt"
    const val ARRIVAL_YES = "arrival-yes"
    const val ARRIVAL_NO = "arrival-no"
    const val PERMISSION_REQUIRED = "permission-required"
    const val PERMISSION_REQUIRED_CLOSE = "permission-required-close"
    const val PERMISSION_REQUIRED_ALLOW = "permission-required-allow"
    const val CLOSING_MESSAGE = "closing-message"
}
