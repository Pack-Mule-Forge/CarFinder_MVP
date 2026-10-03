package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.guidance.ConeGeometry
import com.packmuleforge.carfindermvp.shared.platform.Capability

/**
 * The UI's only input: exactly one view, chosen by FR-042.
 *
 * @requirement FR-042, QR-013
 */
sealed interface HomeScreenState {
    /** The FR-056 confirmation that a required permission is needed, with "Close" and "Allow". */
    data class PermissionRequired(val capability: Capability) : HomeScreenState

    /** "Car Finder is closing." */
    data object Closing : HomeScreenState

    /** "Location unavailable" */
    data object Unavailable : HomeScreenState

    /** "Driving" */
    data object Driving : HomeScreenState

    /** "Sensing you will be parking soon" */
    data object Parking : HomeScreenState

    data class Guidance(val cone: ConeGeometry, val distanceText: String) : HomeScreenState

    /** "You have arrived", with the "Do you see your car?" prompt while [isPromptVisible]. */
    data class Arrived(val isPromptVisible: Boolean) : HomeScreenState
}
