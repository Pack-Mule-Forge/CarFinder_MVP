package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * The complete, displayable description of the current moment — computed in `:shared`, consumed
 * by Compose as a pure input. A sealed hierarchy makes the four-way selection total by
 * construction, so FR-030's "no undefined display condition can arise" is guaranteed by the type
 * system rather than by a defensive branch.
 *
 * @requirement FR-018 System MUST evaluate the four outcomes in strict priority order
 * @requirement FR-019 System MUST show "Driving - Waiting to Park" when state is DRIVING
 * @requirement FR-020 System MUST show "Parked location unavailable." when state is FINDING (or
 *   PARKED without available guidance, FR-030)
 * @requirement FR-021 System MUST show "Sensing you will be Parking Soon." when state is PARKING
 * @requirement FR-022 System MUST show directional guidance when state is PARKED and location exists
 * @requirement FR-030 Any undefined state falls back to FINDING
 */
@Requirement("FR-018", "FR-019", "FR-020", "FR-021", "FR-022", "FR-030", "FR-042")
sealed interface GuidanceViewState {
    /**
     * System is DRIVING (possibly with no location ever stored). Message: "Driving - Waiting to
     * Park" (FR-019)
     */
    data object Driving : GuidanceViewState

    /**
     * System is FINDING (no location has been determined), or PARKED with a stored location but
     * guidance unavailable (FR-030). Message: "Parked location unavailable." (FR-020) — wording
     * that is true in both cases. Also the fallback for any undefined state.
     */
    data object NoParkedLocation : GuidanceViewState

    /**
     * System is PARKING (detecting convergence). Message: "Sensing you will be Parking Soon."
     * (FR-021)
     */
    data object ParkingSoon : GuidanceViewState

    /**
     * System is PARKED with a stored location. Display the directional guidance cone and distance
     * text (FR-022). Guidance rendering uses all fields; the display updates as position and
     * heading change.
     */
    @Requirement("FR-023", "FR-024", "FR-025", "FR-026", "FR-027", "FR-028", "FR-029", "FR-031")
    data class Guidance(
        /** Distance to the Parked Location in meters (FR-028) */
        val distanceMeters: Double,

        /** Distance formatted as feet or miles per FR-029 (at or below threshold → feet) */
        val formattedDistance: String,

        /**
         * Display bearing = (360 - deviceHeadingTrueNorth + bearingToCar) mod 360, so the cone
         * tracks the vehicle as the device turns (FR-024)
         */
        val displayBearingDegrees: Double,

        /**
         * Cone half-angle = atan(uncertaintyRadius / distance) in radians. At zero distance
         * returns π/2 (90°) to indicate arrival (FR-023)
         */
        val coneHalfAngleRadians: Double,

        /** True if cone half-angle >= ARRIVAL_CONE_HALF_ANGLE (FR-031) */
        val hasArrived: Boolean
    ) : GuidanceViewState
}
