package com.packmuleforge.carfinder_mvp.permission

import com.packmuleforge.carfinder.shared.platform.Capability
import com.packmuleforge.carfinder.shared.platform.PermissionController
import com.packmuleforge.carfinder.shared.platform.PermissionResult
import com.packmuleforge.carfinder.shared.platform.PermissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * T108 test double for PermissionController, local to :app's test source set.
 *
 * shared/src/commonTest/.../fake/FakePermissionController.kt already exists and does the same
 * job, but :app only depends on :shared's main output (`implementation(project(":shared"))`), not
 * its commonTest sources — there is no Gradle wiring (e.g. a testFixtures configuration) that
 * exposes commonTest to :app's test compilation, and adding one would be a build.gradle.kts
 * change unrelated to the Activity Result API/permission-dialog work Rule 2 scopes Gradle changes
 * to. This is a minimal, :app-local equivalent scoped to exactly what
 * [PermissionFlowCoordinator] needs: a scripted result per capability and a call log for
 * asserting request order and no-repeat-after-decline.
 */
class FakePermissionController : PermissionController {
    private val scripted = mutableMapOf<Capability, PermissionResult>()
    private val statusMap = mutableMapOf<Capability, MutableStateFlow<PermissionStatus>>()

    /** Every capability passed to [request], in call order — includes repeats if any occurred. */
    val requestLog = mutableListOf<Capability>()

    fun script(capability: Capability, result: PermissionResult) {
        scripted[capability] = result
    }

    override suspend fun request(capability: Capability): PermissionResult {
        requestLog += capability
        val result = scripted[capability] ?: PermissionResult.DENIED
        statusMap.getOrPut(capability) { MutableStateFlow(PermissionStatus.DENIED) }.value =
            when (result) {
                PermissionResult.GRANTED -> PermissionStatus.GRANTED
                PermissionResult.DENIED -> PermissionStatus.DENIED
                PermissionResult.PERMANENTLY_DENIED -> PermissionStatus.PERMANENTLY_DENIED
            }
        return result
    }

    override fun status(capability: Capability): Flow<PermissionStatus> =
        statusMap.getOrPut(capability) { MutableStateFlow(PermissionStatus.DENIED) }
}
