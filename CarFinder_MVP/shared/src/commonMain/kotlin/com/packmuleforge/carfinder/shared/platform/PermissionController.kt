package com.packmuleforge.carfinder.shared.platform

import kotlinx.coroutines.flow.Flow

/**
 * Platform capabilities that require runtime permission requests. Each capability may map to one
 * or more platform-specific permissions (e.g., LOCATION on Android requires both fine and coarse
 * location permissions).
 */
enum class Capability {
    LOCATION,
    BACKGROUND_LOCATION,
    ACTIVITY_RECOGNITION
}

/**
 * Result of a permission request. GRANTED means the capability is now available. DENIED means the
 * user declined and can be asked again. PERMANENTLY_DENIED means the user has disabled the
 * permission in system settings and must be directed there manually.
 */
enum class PermissionResult {
    GRANTED, DENIED, PERMANENTLY_DENIED
}

/**
 * Current status of a capability. GRANTED means the capability is available. DENIED means it was
 * denied or has not been requested. PERMANENTLY_DENIED means the user has disabled it in system
 * settings. NOT_APPLICABLE means the platform does not recognize or support this capability.
 */
enum class PermissionStatus {
    GRANTED, DENIED, PERMANENTLY_DENIED, NOT_APPLICABLE
}

/**
 * Platform abstraction for runtime permission handling. This normalizes each platform's permission
 * flow into a single suspend function per platform, without forcing false symmetry where platforms
 * genuinely differ.
 *
 * Example: iOS motion APIs require no explicit permission request, so that capability may return
 * NOT_APPLICABLE or observable status rather than being forced into a request/response shape.
 */
expect class PermissionController {
    /**
     * Request a permission/capability. Suspends until the user responds or the platform handles
     * the request. Returns the result of the request.
     */
    suspend fun request(capability: Capability): PermissionResult

    /**
     * Observe the current status of a capability. Emits immediately on subscription with the
     * current status, then emits again whenever the status changes (e.g., the user changes the
     * setting in system preferences).
     */
    fun status(capability: Capability): Flow<PermissionStatus>
}
