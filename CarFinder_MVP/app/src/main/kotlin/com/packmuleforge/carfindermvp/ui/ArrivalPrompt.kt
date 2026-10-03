package com.packmuleforge.carfindermvp.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.packmuleforge.carfindermvp.R

/**
 * "You have arrived", with the "Do you see your car?" dialog while [isPromptVisible]. Yes and No both only
 * dismiss the prompt.
 *
 * @requirement FR-038, FR-039, QR-012
 */
@Composable
fun ArrivedDisplay(isPromptVisible: Boolean, onAnswered: () -> Unit) {
    StatusMessage(stringResource(R.string.arrived_message), tag = UiTags.ARRIVAL_MESSAGE)
    if (isPromptVisible) {
        AlertDialog(
            modifier = Modifier.testTag(UiTags.ARRIVAL_PROMPT),
            // Only an answer dismisses the prompt.
            onDismissRequest = {},
            text = { Text(stringResource(R.string.arrival_question)) },
            confirmButton = {
                TextButton(onClick = onAnswered, modifier = Modifier.testTag(UiTags.ARRIVAL_YES)) {
                    Text(stringResource(R.string.answer_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onAnswered, modifier = Modifier.testTag(UiTags.ARRIVAL_NO)) {
                    Text(stringResource(R.string.answer_no))
                }
            },
        )
    }
}
