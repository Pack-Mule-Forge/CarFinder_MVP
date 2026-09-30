package com.packmuleforge.carfindermvp.shared.domain

import kotlinx.serialization.Serializable

/**
 * The parking lifecycle. Exactly one state is current at a time, and a fresh install starts in [FINDING].
 *
 * Invariant: `state == PARKED` ⇔ `parkedLocation != null` (FR-018).
 *
 * @requirement FR-001, FR-011
 */
@Serializable
enum class LifecycleState {
    /** No Parked Location is held. The initial state. */
    FINDING,
    DRIVING,
    PARKING,
    /** The only state in which a Parked Location is held. */
    PARKED,
}
