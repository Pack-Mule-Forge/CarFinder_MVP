package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.platform.Capability
import com.packmuleforge.carfinder.shared.platform.PermissionController
import com.packmuleforge.carfinder.shared.platform.PermissionResult
import com.packmuleforge.carfinder.shared.platform.PermissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test fake for PermissionController. Allows scripting permission request results and
 * permission status changes per capability.
 */
class FakePermissionController : PermissionController {
    private val statusMap: MutableMap<Capability, MutableStateFlow<PermissionStatus>> = mutableMapOf(
        Capability.LOCATION to MutableStateFlow(PermissionStatus.GRANTED),
        Capability.BACKGROUND_LOCATION to MutableStateFlow(PermissionStatus.GRANTED),
        Capability.ACTIVITY_RECOGNITION to MutableStateFlow(PermissionStatus.GRANTED),
        // T110/CR-5 deviation: default GRANTED, matching the other three, now that Capability has
        // a NOTIFICATIONS entry (FR-045).
        Capability.NOTIFICATIONS to MutableStateFlow(PermissionStatus.GRANTED)
    )

    override suspend fun request(capability: Capability): PermissionResult {
        val current = statusMap[capability]?.value ?: PermissionStatus.NOT_APPLICABLE
        return when (current) {
            PermissionStatus.GRANTED -> PermissionResult.GRANTED
            PermissionStatus.DENIED -> PermissionResult.DENIED
            PermissionStatus.PERMANENTLY_DENIED -> PermissionResult.PERMANENTLY_DENIED
            PermissionStatus.NOT_APPLICABLE -> PermissionResult.DENIED
        }
    }

    override fun status(capability: Capability): Flow<PermissionStatus> {
        return statusMap.getOrPut(capability) { MutableStateFlow(PermissionStatus.NOT_APPLICABLE) }
    }

    fun setStatus(capability: Capability, status: PermissionStatus) {
        statusMap.getOrPut(capability) { MutableStateFlow(status) }.value = status
    }
}
