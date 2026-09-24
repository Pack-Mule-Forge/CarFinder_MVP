package com.packmuleforge.carfinder_mvp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Guidance display cone showing direction and distance to the parked vehicle.
 * Renders a cone (edges only, no centerline) that:
 * - Rotates to track the vehicle as the device turns (display_bearing)
 * - Widens based on uncertainty (cone half-angle)
 * - Uses minimum display dimension for consistent scaling across rotations
 *
 * When arrival is reached, replaces the cone with an arrival confirmation prompt (FR-031, FR-032).
 */
@Requirement("FR-023", "FR-025", "FR-026", "FR-027", "FR-028", "FR-031", "FR-032", "SC-004")
@Composable
fun GuidanceDisplay(
    distanceMeters: State<Double>,
    formattedDistance: State<String>,
    displayBearingDegrees: State<Double>,
    coneHalfAngleRadians: State<Double>,
    hasArrived: State<Boolean>,
    onArrivalAnswered: (yesClicked: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // FR-031, FR-032: Replace cone with arrival prompt when arrival condition is met
    if (hasArrived.value) {
        ArrivalPrompt(
            onAnswered = onArrivalAnswered,
            modifier = modifier
        )
        return
    }

    // Otherwise, show the cone (FR-027: geometry against min dimension)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDimension = min(size.width, size.height)
            val centerX = size.width / 2
            val centerY = size.height / 2
            val coneLength = minDimension * 0.4f  // Length of cone (40% of min dimension)

            // Cone half-angle in radians
            val halfAngleRad = coneHalfAngleRadians.value
            val bearingRad = Math.toRadians(displayBearingDegrees.value)

            // Draw the cone edges
            drawConeBoundaries(centerX, centerY, coneLength, bearingRad, halfAngleRad)

            // Draw person icon at cone apex
            drawPersonIcon(centerX, centerY)

            // Draw car icon at cone far end
            val carX = centerX + coneLength * sin(bearingRad).toFloat()
            val carY = centerY - coneLength * cos(bearingRad).toFloat()
            drawCarIcon(carX, carY)
        }

        // Distance text centered in the display
        Text(
            text = formattedDistance.value,
            style = TextStyle(fontSize = 24.sp, color = Color.Black),
            modifier = Modifier.align(Alignment.Center).semantics { testTag = "guidance_distance_text" }
        )
    }
}

private fun DrawScope.drawConeBoundaries(
    centerX: Float,
    centerY: Float,
    coneLength: Float,
    bearingRad: Double,
    halfAngleRad: Double
) {
    val color = Color.Blue
    val strokeWidth = 3f

    // Left edge of cone
    val leftAngle = bearingRad - halfAngleRad
    val leftEndX = centerX + coneLength * sin(leftAngle).toFloat()
    val leftEndY = centerY - coneLength * cos(leftAngle).toFloat()
    drawLine(color, Offset(centerX, centerY), Offset(leftEndX, leftEndY), strokeWidth)

    // Right edge of cone
    val rightAngle = bearingRad + halfAngleRad
    val rightEndX = centerX + coneLength * sin(rightAngle).toFloat()
    val rightEndY = centerY - coneLength * cos(rightAngle).toFloat()
    drawLine(color, Offset(centerX, centerY), Offset(rightEndX, rightEndY), strokeWidth)

    // Arc at the cone's far end (simplified: just connect the edges)
    // A real implementation would draw an arc, but for MVP, connecting the edges is sufficient
    drawLine(color, Offset(leftEndX, leftEndY), Offset(rightEndX, rightEndY), strokeWidth)
}

private fun DrawScope.drawPersonIcon(centerX: Float, centerY: Float) {
    val size = 15f
    drawCircle(Color.Green, size / 2, Offset(centerX, centerY))
}

private fun DrawScope.drawCarIcon(carX: Float, carY: Float) {
    val size = 15f
    drawCircle(Color.Red, size / 2, Offset(carX, carY))
}
