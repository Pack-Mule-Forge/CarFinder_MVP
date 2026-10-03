package com.packmuleforge.carfindermvp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState

/**
 * The one screen: a pure function of [state] (QR-013), built only from Compose (QR-012).
 *
 * @requirement FR-042, QR-012, QR-013
 */
@Composable
fun HomeScreen(
    state: HomeScreenState,
    onArrivalAnswered: () -> Unit,
    onDenialConfirmed: () -> Unit,
    onDenialDismissed: () -> Unit,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (state) {
                HomeScreenState.Unavailable -> StatusMessage(stringResource(R.string.location_unavailable))
                is HomeScreenState.Guidance -> GuidanceDisplay(state)
                is HomeScreenState.Arrived -> ArrivedDisplay(state.isPromptVisible, onArrivalAnswered)
                // The remaining states are drawn by the stories that introduce them.
                else -> Unit
            }
        }
    }
}
