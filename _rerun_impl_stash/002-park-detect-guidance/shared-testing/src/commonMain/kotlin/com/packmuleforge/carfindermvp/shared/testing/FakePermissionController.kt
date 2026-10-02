package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.platform.PermissionController
import com.packmuleforge.carfindermvp.shared.platform.RequestMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scripted permission state. [grantOnRequest] lists the capabilities a request will grant; [modes] can model
 * iOS-style [RequestMode.IMPLICIT_ON_FIRST_USE].
 */
class FakePermissionController(
    initial: Map<Capability, CapabilityStatus> =
        Capability.entries.associateWith { CapabilityStatus.NOT_DETERMINED },
    val modes: MutableMap<Capability, RequestMode> =
        Capability.entries.associateWith { RequestMode.EXPLICIT }.toMutableMap(),
    var grantOnRequest: Set<Capability> = Capability.entries.toSet(),
) : PermissionController {
    private val flow = MutableStateFlow(initial)
    override val status: StateFlow<Map<Capability, CapabilityStatus>> = flow

    val requests = mutableListOf<Set<Capability>>()

    fun set(capability: Capability, value: CapabilityStatus) {
        flow.value = flow.value + (capability to value)
    }

    fun grantAll() {
        flow.value = Capability.entries.associateWith { CapabilityStatus.GRANTED }
    }

    override fun requestMode(capability: Capability): RequestMode = modes.getValue(capability)

    override suspend fun request(capabilities: Set<Capability>): Map<Capability, CapabilityStatus> {
        requests += capabilities
        for (capability in capabilities) {
            if (modes.getValue(capability) != RequestMode.EXPLICIT) continue
            if (flow.value[capability] == CapabilityStatus.GRANTED) continue
            set(capability, if (capability in grantOnRequest) CapabilityStatus.GRANTED else CapabilityStatus.DENIED)
        }
        return flow.value.filterKeys { it in capabilities }
    }
}
