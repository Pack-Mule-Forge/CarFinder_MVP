package com.packmuleforge.carfinder.shared.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android implementation of PermissionController. Handles the API-version divergence:
 * - API 24-28: BACKGROUND_LOCATION is implicitly granted if FINE_LOCATION is granted
 * - API 30+: BACKGROUND_LOCATION is a separate request, must be asked after foreground is granted
 * - API 29+: ACTIVITY_RECOGNITION is a runtime permission; below that, NOT_APPLICABLE
 * - API 33+: NOTIFICATIONS (POST_NOTIFICATIONS) is a runtime permission; below that, NOT_APPLICABLE
 *
 * `request()` is safe to call with no [PermissionRequester] attached — e.g. from the service,
 * which has no Activity — and degrades to a status-only read, never throwing (T110).
 */
@Requirement("FR-014", "FR-045", "FR-046", "SC-010")
class AndroidPermissionController(private val context: Context) : PermissionController {
    private val statusMap: MutableMap<Capability, MutableStateFlow<PermissionStatus>> = mutableMapOf(
        Capability.LOCATION to MutableStateFlow(PermissionStatus.DENIED),
        Capability.BACKGROUND_LOCATION to MutableStateFlow(PermissionStatus.DENIED),
        Capability.ACTIVITY_RECOGNITION to MutableStateFlow(PermissionStatus.DENIED),
        Capability.NOTIFICATIONS to MutableStateFlow(PermissionStatus.DENIED)
    )

    /**
     * T110 construction fix: settable by MainActivity once it has a live ActivityResultLauncher.
     * Null when no Activity is available; see [PermissionRequester].
     */
    var requester: PermissionRequester? = null

    init {
        updateStatus()
    }

    override suspend fun request(capability: Capability): PermissionResult {
        updateStatus()

        if (statusMap[capability]?.value == PermissionStatus.GRANTED) {
            return PermissionResult.GRANTED
        }

        // Android contract (contracts/platform-adapters.md): background location must not be
        // requested until foreground location is already granted.
        if (capability == Capability.BACKGROUND_LOCATION &&
            statusMap[Capability.LOCATION]?.value != PermissionStatus.GRANTED
        ) {
            return PermissionResult.DENIED
        }

        val permissionNames = androidPermissionsFor(capability)
        val activeRequester = requester

        if (activeRequester == null || permissionNames.isEmpty()) {
            // No Activity to prompt with (T110's no-op fallback), or this capability requires no
            // runtime permission on the current API level (implicit grant or NOT_APPLICABLE).
            updateStatus()
            return statusMap[capability]?.value.toResult()
        }

        var allGranted = true
        for (permissionName in permissionNames) {
            if (!activeRequester.requestPermission(permissionName)) {
                allGranted = false
            }
        }
        updateStatus()

        if (allGranted) return PermissionResult.GRANTED
        val canAskAgain = permissionNames.any { activeRequester.shouldShowRationale(it) }
        return if (canAskAgain) PermissionResult.DENIED else PermissionResult.PERMANENTLY_DENIED
    }

    override fun status(capability: Capability): Flow<PermissionStatus> {
        return statusMap.getOrPut(capability) { MutableStateFlow(PermissionStatus.NOT_APPLICABLE) }
    }

    private fun androidPermissionsFor(capability: Capability): List<String> = when (capability) {
        Capability.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        Capability.BACKGROUND_LOCATION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                emptyList() // API 24-28: implicit grant, nothing to request
            }

        Capability.ACTIVITY_RECOGNITION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                emptyList() // API 24-28: NOT_APPLICABLE, no permission exists
            }

        Capability.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList() // API < 33: NOT_APPLICABLE, notifications are granted by default
            }
    }

    private fun PermissionStatus?.toResult(): PermissionResult = when (this) {
        PermissionStatus.GRANTED -> PermissionResult.GRANTED
        PermissionStatus.DENIED -> PermissionResult.DENIED
        PermissionStatus.PERMANENTLY_DENIED -> PermissionResult.PERMANENTLY_DENIED
        PermissionStatus.NOT_APPLICABLE, null -> PermissionResult.DENIED
    }

    private fun updateStatus() {
        val locationStatus = checkStatus(Manifest.permission.ACCESS_FINE_LOCATION)
        statusMap[Capability.LOCATION]?.value = locationStatus

        val bgLocationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            checkStatus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // API 24-28: implicit grant if foreground is granted
            if (locationStatus == PermissionStatus.GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        statusMap[Capability.BACKGROUND_LOCATION]?.value = bgLocationStatus

        val activityStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkStatus(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            PermissionStatus.NOT_APPLICABLE // API 24-28: no permission required
        }
        statusMap[Capability.ACTIVITY_RECOGNITION]?.value = activityStatus

        val notificationsStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkStatus(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionStatus.NOT_APPLICABLE // API < 33: granted by default, no permission gate
        }
        statusMap[Capability.NOTIFICATIONS]?.value = notificationsStatus
    }

    private fun checkStatus(permission: String): PermissionStatus =
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }

    fun onPermissionsChanged() {
        updateStatus()
    }
}
