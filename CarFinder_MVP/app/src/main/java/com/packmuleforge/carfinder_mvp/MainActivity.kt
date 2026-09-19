package com.packmuleforge.carfinder_mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder_mvp.ui.GuidanceDisplay
import com.packmuleforge.carfinder_mvp.ui.GuidanceViewModel
import com.packmuleforge.carfinder_mvp.ui.StatusMessage
import com.packmuleforge.carfinder_mvp.ui.theme.CarFinder_MVPTheme

/**
 * Main activity for Car Finder. Displays the default view showing parking state and guidance.
 * The view is read-only; it observes the state machine output and renders it (FR-014, FR-042).
 */
@Requirement("FR-017", "FR-018", "FR-041", "FR-042")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarFinder_MVPTheme {
                DefaultView(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun DefaultView(modifier: Modifier = Modifier) {
    val viewModel: GuidanceViewModel = viewModel()
    val viewState = viewModel.viewState.collectAsState()

    // Exhaustive when ensures all GuidanceViewState variants are handled (FR-018)
    when (val state = viewState.value) {
        is GuidanceViewState.Driving -> {
            StatusMessage(
                text = "Driving - Waiting to Park",
                modifier = modifier
            )
        }

        is GuidanceViewState.NoParkedLocation -> {
            StatusMessage(
                text = "No parked Location yet.",
                modifier = modifier
            )
        }

        is GuidanceViewState.ParkingSoon -> {
            StatusMessage(
                text = "Sensing you will be Parking Soon.",
                modifier = modifier
            )
        }

        is GuidanceViewState.Guidance -> {
            GuidanceDisplay(
                distanceMeters = androidx.compose.runtime.derivedStateOf { state.distanceMeters },
                formattedDistance = androidx.compose.runtime.derivedStateOf { state.formattedDistance },
                displayBearingDegrees = androidx.compose.runtime.derivedStateOf { state.displayBearingDegrees },
                coneHalfAngleRadians = androidx.compose.runtime.derivedStateOf { state.coneHalfAngleRadians },
                hasArrived = androidx.compose.runtime.derivedStateOf { state.hasArrived },
                onArrivalAnswered = { _ ->
                    // FR-033: Answering the prompt is local UI state only.
                    // The state machine and Parked Location are unchanged.
                    // In a real app, this callback would manage local UI state for dismissing the prompt.
                },
                modifier = modifier
            )
        }
    }
}