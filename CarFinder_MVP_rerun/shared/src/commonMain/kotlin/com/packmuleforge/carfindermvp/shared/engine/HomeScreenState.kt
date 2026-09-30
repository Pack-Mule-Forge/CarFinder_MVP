package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometry
import com.packmuleforge.carfindermvp.shared.guidance.DistanceDisplay

/**
 * The only input to the home screen. Every value is immutable, so the UI is a pure function of it.
 *
 * @requirement FR-016, QR-009
 */
sealed interface HomeScreenState {
    /** "Driving - Waiting to Park." */
    data object Driving : HomeScreenState

    /** "Parked location unavailable." — no location held, or guidance cannot be computed honestly right now. */
    data object Unavailable : HomeScreenState

    /** "Sensing you will be Parking Soon." */
    data object Parking : HomeScreenState

    data class Guidance(
        val cone: ConeGeometry,
        val distance: DistanceDisplay,
        val isArrived: Boolean,
        val isArrivalPromptVisible: Boolean,
    ) : HomeScreenState
}
