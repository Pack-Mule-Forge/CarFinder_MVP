package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.NormalizedPoint
import kotlin.math.roundToInt

/**
 * The guidance cone, the person and car icons at its two ends, and the distance in the middle. Geometry arrives
 * normalized from shared code; this only scales by the minimum display dimension and translates to the center.
 *
 * @requirement FR-031, FR-035, FR-036, FR-037, FR-046, QR-012
 */
@Composable
fun GuidanceDisplay(state: HomeScreenState.Guidance, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag(UiTags.GUIDANCE_DISPLAY)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val scale = minOf(width, height)
        val center = Offset(width / 2, height / 2)
        val cone = state.cone

        // One scale and one translation; the y axis points up in normalized space.
        fun toScreen(point: NormalizedPoint) =
            Offset(center.x + (point.x * scale).toFloat(), center.y - (point.y * scale).toFloat())

        val apex = toScreen(cone.apex)
        val car = toScreen(cone.carAnchor)
        val radius = (cone.lengthFraction * scale).toFloat()
        val coneColor = MaterialTheme.colorScheme.primary.copy(alpha = CONE_ALPHA)

        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(UiTags.GUIDANCE_CONE)
                .semantics {
                    halfAngleDegrees = cone.halfAngleDegrees
                    displayBearingDegrees = cone.displayBearingDegrees
                    apexAnchor = apex
                    carAnchor = car
                    isCenterlineDrawn = false
                },
        ) {
            // Compose arcs start at 3 o'clock; bearings start at 12 o'clock.
            drawArc(
                color = coneColor,
                startAngle = (cone.sweepStartDegrees - QUARTER_TURN_DEGREES).toFloat(),
                sweepAngle = cone.sweepDegrees.toFloat(),
                useCenter = true,
                topLeft = Offset(apex.x - radius, apex.y - radius),
                size = Size(radius * 2, radius * 2),
            )
        }

        val iconSizePx = with(LocalDensity.current) { ICON_SIZE.toPx() }
        AnchoredIcon(R.drawable.ic_person, R.string.person_icon_description, apex, iconSizePx, UiTags.PERSON_ICON)
        AnchoredIcon(R.drawable.ic_car, R.string.car_icon_description, car, iconSizePx, UiTags.CAR_ICON)

        Text(
            text = state.distanceText,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center).testTag(UiTags.DISTANCE_TEXT),
        )
    }
}

@Composable
private fun AnchoredIcon(drawable: Int, description: Int, anchor: Offset, sizePx: Float, tag: String) {
    Box(
        Modifier
            .offset { IntOffset((anchor.x - sizePx / 2).roundToInt(), (anchor.y - sizePx / 2).roundToInt()) }
            .size(ICON_SIZE)
            .testTag(tag),
    ) {
        Image(painterResource(drawable), contentDescription = stringResource(description), modifier = Modifier.fillMaxSize())
    }
}

private val ICON_SIZE = 40.dp
private const val CONE_ALPHA = 0.35f
private const val QUARTER_TURN_DEGREES = 90.0
