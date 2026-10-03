package com.packmuleforge.carfinder.shared.model

import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * The parking lifecycle state. FINDING is the default and initial state, meaning no Parked Location
 * has been determined (FR-009). PARKED is the state in which a location is held, and it covers
 * walking back to the vehicle.
 *
 * @requirement FR-001 System MUST maintain exactly one current parking state at all times
 * @requirement FR-009 System MUST be in FINDING whenever no Parked Location has been determined
 */
@Requirement("FR-001", "FR-009")
enum class ParkingState {
    /** User is driving or stopped momentarily during a drive */
    DRIVING,

    /** User has slowed to parking speed; system is sampling to detect convergence */
    PARKING,

    /** Convergence achieved; a Parked Location exists and is being stored */
    PARKED,

    /**
     * No Parked Location has been determined, either because the app was just installed, because
     * no parking cycle has completed, or because position signal is unavailable. This is the
     * default and initial state.
     */
    FINDING
}
