package com.packmuleforge.carfinder_mvp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.sp
import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Simple centered text status message for non-guidance states.
 * Renders the exact message provided with testTag="status_message" for testing (FR-019, FR-020, FR-021).
 */
@Requirement("FR-019", "FR-020", "FR-021")
@Composable
fun StatusMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = 18.sp,
            modifier = Modifier.semantics { testTag = "status_message" }
        )
    }
}
