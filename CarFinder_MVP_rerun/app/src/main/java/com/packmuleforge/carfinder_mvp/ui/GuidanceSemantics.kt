package com.packmuleforge.carfinder_mvp.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

/**
 * Custom semantics properties for UI testing of the guidance display.
 * These allow tests to read the rendered cone half-angle and other properties
 * without needing to reverse-engineer them from the Canvas drawing.
 */

val ConeHalfAngleProperty = SemanticsPropertyKey<Float>("coneHalfAngle")

fun androidx.compose.ui.Modifier.guidanceConeSemantic(coneHalfAngleRadians: Float): androidx.compose.ui.Modifier {
    return this.semantics {
        set(ConeHalfAngleProperty, coneHalfAngleRadians)
    }.semantics {
        testTag = "guidance_cone"
    }
}

// Test tags for other elements
const val GUIDANCE_PERSON_ICON_TAG = "guidance_person_icon"
const val GUIDANCE_CAR_ICON_TAG = "guidance_car_icon"
const val GUIDANCE_DISTANCE_TEXT_TAG = "guidance_distance_text"
