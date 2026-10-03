package com.packmuleforge.carfindermvp.shared.domain

import kotlinx.serialization.Serializable

/**
 * The single lifecycle state. FINDING means only "no Parked Location is held".
 *
 * @requirement FR-001
 */
@Serializable
enum class LifecycleState { FINDING, DRIVING, PARKING, PARKED }
