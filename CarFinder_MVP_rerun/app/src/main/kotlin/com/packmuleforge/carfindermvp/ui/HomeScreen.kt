package com.packmuleforge.carfindermvp.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState

/**
 * The app's only screen, shown without any user action. A pure function of [state]; view selection happened in
 * the shared presenter.
 *
 * @requirement FR-016, FR-017, FR-031, QR-008, QR-009
 */
@Composable
fun HomeScreen(state: HomeScreenState, onArrivalAnswered: (sawCar: Boolean) -> Unit) {
    Surface {
        when (state) {
            HomeScreenState.Driving -> StatusMessage(stringResource(R.string.status_driving))
            HomeScreenState.Unavailable -> StatusMessage(stringResource(R.string.status_unavailable))
            HomeScreenState.Parking -> StatusMessage(stringResource(R.string.status_parking))
            is HomeScreenState.Guidance -> GuidanceDisplay(state, onArrivalAnswered)
        }
    }
}
