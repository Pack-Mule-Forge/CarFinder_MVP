package com.packmuleforge.carfinder_mvp.permission

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.platform.Capability
import com.packmuleforge.carfinder.shared.platform.PermissionController
import com.packmuleforge.carfinder.shared.platform.PermissionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sequences the FR-045 permission requests through the shared [PermissionController] abstraction:
 * precise location, then background location, then activity recognition, then notifications.
 * Tracks which capabilities were declined this app session so none is re-requested (FR-045), and
 * exposes [locationGranted] so MainActivity/GuidanceViewModel can react without polling.
 *
 * [onLocationGranted] is invoked exactly once, the moment location transitions to granted, so the
 * caller can start the background service automatically (FR-046) with no app restart required.
 */
@Requirement("FR-045", "FR-046")
class PermissionFlowCoordinator(
    private val permissionController: PermissionController,
    private val onLocationGranted: () -> Unit
) {
    private val declinedThisSession = mutableSetOf<Capability>()

    private val _locationGranted = MutableStateFlow(false)
    val locationGranted: StateFlow<Boolean> = _locationGranted.asStateFlow()

    // FR-045: this order is the requirement itself, not a caller choice.
    private val requestOrder = listOf(
        Capability.LOCATION,
        Capability.BACKGROUND_LOCATION,
        Capability.ACTIVITY_RECOGNITION,
        Capability.NOTIFICATIONS
    )

    /**
     * Run one pass of the sequence, skipping any capability already declined this session.
     * Safe to call repeatedly (e.g. on every launch while permissions remain outstanding).
     */
    suspend fun runFlow() {
        for (capability in requestOrder) {
            if (capability in declinedThisSession) continue

            val granted = permissionController.request(capability) == PermissionResult.GRANTED
            if (!granted) {
                declinedThisSession += capability
            }

            if (capability == Capability.LOCATION) {
                if (granted) {
                    if (!_locationGranted.value) {
                        _locationGranted.value = true
                        onLocationGranted()
                    }
                } else {
                    _locationGranted.value = false
                    // FR-046: nothing downstream of a denied LOCATION is meaningful (no service,
                    // no guidance); stop this pass here rather than asking for the rest.
                    return
                }
            }
        }
    }
}
