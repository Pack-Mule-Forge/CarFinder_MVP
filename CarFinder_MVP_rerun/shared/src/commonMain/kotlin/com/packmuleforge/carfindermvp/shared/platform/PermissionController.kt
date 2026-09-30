package com.packmuleforge.carfindermvp.shared.platform

import kotlinx.coroutines.flow.StateFlow

enum class Capability { LOCATION_FOREGROUND, LOCATION_BACKGROUND, MOTION_ACTIVITY, NOTIFICATIONS }

/**
 * How a platform obtains a capability. iOS motion activity, for example, prompts implicitly on first use, so it
 * is not forced into an explicit request it does not have (Constitution V, research R6).
 */
enum class RequestMode { EXPLICIT, IMPLICIT_ON_FIRST_USE, NOT_REQUIRED }

enum class CapabilityStatus { GRANTED, DENIED, NOT_DETERMINED, RESTRICTED, UNAVAILABLE }

/**
 * Normalizes the platform's runtime-permission flow into one suspend function plus observable status.
 * [request] MUST prompt only for [RequestMode.EXPLICIT] capabilities that are not already granted, and [status]
 * MUST reflect the real platform state.
 *
 * @requirement QR-010
 */
interface PermissionController {
    val status: StateFlow<Map<Capability, CapabilityStatus>>

    fun requestMode(capability: Capability): RequestMode

    suspend fun request(capabilities: Set<Capability>): Map<Capability, CapabilityStatus>
}
