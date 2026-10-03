package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionController
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports the platform's current grants and never prompts. Replaced by the real controller in US7.
 */
internal class StubPermissionController(private val context: Context) : PermissionController {
    private val state = MutableStateFlow(read())
    override val status: StateFlow<PermissionState> = state.asStateFlow()

    override suspend fun request(capability: Capability): Boolean {
        refresh()
        return state.value.isGranted(capability)
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read() = PermissionState(
        Capability.entries.associateWith { if (isGranted(it)) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED },
    )

    private fun isGranted(capability: Capability): Boolean = when (capability) {
        Capability.FINE_LOCATION -> granted(Manifest.permission.ACCESS_FINE_LOCATION)
        Capability.BACKGROUND_LOCATION ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        Capability.ACTIVITY_RECOGNITION ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACTIVITY_RECOGNITION)
        Capability.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
