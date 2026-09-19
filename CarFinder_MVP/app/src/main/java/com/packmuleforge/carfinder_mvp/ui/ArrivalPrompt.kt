package com.packmuleforge.carfinder_mvp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Arrival confirmation prompt. Shown when the cone half-angle reaches the arrival threshold,
 * replacing the guidance cone. User can answer "Yes" or "No"; neither affects the parking
 * state or location (FR-033).
 */
@Requirement("FR-031", "FR-032", "FR-033")
@Composable
fun ArrivalPrompt(
    onAnswered: (yesClicked: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // "You have arrived" message
            Text(
                text = "You have arrived",
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 24.dp),
                semantics { testTag = "arrival_message" }
            )

            // "Do you see your car?" question
            Text(
                text = "Do you see your car?",
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 32.dp),
                semantics { testTag = "arrival_prompt" }
            )

            // Yes/No buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Button(
                    onClick = { onAnswered(true) },
                    modifier = Modifier.weight(1f),
                    semantics { testTag = "arrival_answer_yes" }
                ) {
                    Text("Yes")
                }

                Button(
                    onClick = { onAnswered(false) },
                    modifier = Modifier.weight(1f),
                    semantics { testTag = "arrival_answer_no" }
                ) {
                    Text("No")
                }
            }
        }
    }
}
