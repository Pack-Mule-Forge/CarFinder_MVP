package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.domain.TuningConstants
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.guidance.Point
import kotlin.math.min

private val IconSize = 40.dp

/**
 * The guidance cone, person and car icons, and the distance text. A pure function of [state]: all geometry comes
 * from the shared module in normalized units, and the only arithmetic here is scaling by the minimum display
 * dimension and translating to the screen center (Constitution IV). No centerline is drawn (FR-023).
 *
 * @requirement FR-020, FR-021, FR-022, FR-023, FR-024, FR-025, FR-027, FR-028, QR-008, QR-009
 */
@Composable
fun GuidanceDisplay(state: HomeScreenState.Guidance, onArrivalAnswered: (Boolean) -> Unit) {
    if (state.isArrived) {
        ArrivalView(isPromptVisible = state.isArrivalPromptVisible, onArrivalAnswered = onArrivalAnswered)
    } else {
        ConeView(state)
    }
}

/**
 * Replaces the cone, both icons and the distance text once the uncertainty reaches the remaining distance.
 * The Yes/No answer only dismisses the prompt (FR-029).
 *
 * @requirement FR-028, FR-029
 */
@Composable
private fun ArrivalView(isPromptVisible: Boolean, onArrivalAnswered: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.arrival_message),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(TestTags.ARRIVAL_MESSAGE),
        )
        if (isPromptVisible) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.arrival_prompt),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(TestTags.ARRIVAL_PROMPT),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onArrivalAnswered(true) }, modifier = Modifier.testTag(TestTags.ARRIVAL_YES)) {
                    Text(stringResource(R.string.arrival_yes))
                }
                OutlinedButton(onClick = { onArrivalAnswered(false) }, modifier = Modifier.testTag(TestTags.ARRIVAL_NO)) {
                    Text(stringResource(R.string.arrival_no))
                }
            }
        }
    }
}

@Composable
private fun ConeView(state: HomeScreenState.Guidance) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val minDim: Dp = min(maxWidth.value, maxHeight.value).dp
        val cone = state.cone
        val coneColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

        Box(Modifier.size(minDim), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(minDim)
                    .testTag(TestTags.GUIDANCE_CONE)
                    .semantics {
                        coneHalfAngleDegrees = cone.halfAngleDegrees
                        coneDisplayBearingDegrees = cone.displayBearingDegrees
                        coneDrawSize = minDim
                    },
            ) {
                val scale = size.minDimension
                val radius = (TuningConstants.CONE_LENGTH_FRACTION * scale).toFloat()
                val pivot = center + cone.apex.toOffset(scale)
                drawArc(
                    color = coneColor,
                    // Shared angles are clockwise from screen-up; Canvas angles are clockwise from 3 o'clock.
                    startAngle = (cone.sweepStartDegrees - 90.0).toFloat(),
                    sweepAngle = cone.sweepDegrees.toFloat(),
                    useCenter = true,
                    topLeft = Offset(pivot.x - radius, pivot.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            AnchoredIcon(R.drawable.ic_person, R.string.guidance_you, TestTags.PERSON_ICON, cone.apex, minDim)
            AnchoredIcon(R.drawable.ic_car, R.string.guidance_your_car, TestTags.CAR_ICON, cone.carAnchor, minDim)
        }

        Text(
            text = state.distance.text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(TestTags.DISTANCE_TEXT),
        )
    }
}

@Composable
private fun AnchoredIcon(iconRes: Int, descriptionRes: Int, tag: String, anchor: Point, minDim: Dp) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = stringResource(descriptionRes),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .offset(x = minDim * anchor.x.toFloat(), y = -minDim * anchor.y.toFloat())
            .size(IconSize)
            .testTag(tag),
    )
}

/** Normalized point (+y up) to a pixel offset from the canvas center (+y down). */
private fun Point.toOffset(scale: Float) = Offset((x * scale).toFloat(), (-y * scale).toFloat())
