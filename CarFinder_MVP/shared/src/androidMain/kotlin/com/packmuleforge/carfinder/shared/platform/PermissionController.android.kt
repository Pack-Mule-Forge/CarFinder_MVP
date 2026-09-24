package com.packmuleforge.carfinder.shared.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android implementation of PermissionController. Handles the API-version divergence:
 * - API 24-28: BACKGROUND_LOCATION is implicitly granted if FINE_LOCATION is granted
 * - API 30+: BACKGROUND_LOCATION is a separate request, must be asked after foreground is granted
 *
 * The request function is safe to call with no Activity available (returns Denied, never throws).
 */
@Requirement("FR-014", "SC-010")
class AndroidPermissionController(private val context: Context) : PermissionController {
    private val statusMap: MutableMap<Capability, MutableStateFlow<PermissionStatus>> = mutableMapOf(
        Capability.LOCATION to MutableStateFlow(PermissionStatus.DENIED),
        Capability.BACKGROUND_LOCATION to MutableStateFlow(PermissionStatus.DENIED),
        Capability.ACTIVITY_RECOGNITION to MutableStateFlow(PermissionStatus.DENIED)
    )

    init {
        // Initialize statuses based on current grants
        updateStatus()
    }

    override suspend fun request(capability: Capability): PermissionResult {
        // Since we don't have access to an Activity from the shared module,
        // this is a no-op. The service (T040) is responsible for requesting permissions
        // and calling this controller's status() to observe changes.
        val current = status(capability).let { flow ->
            var result = PermissionStatus.DENIED
            flow.collect { result = it }
            result
        }

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

    private fun updateStatus() {
        // Check LOCATION permission
        val locationStatus = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
        statusMap[Capability.LOCATION]?.value = locationStatus

        // Check BACKGROUND_LOCATION permission
        val bgLocationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: separate permission
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        } else {
            // API 24-28: implicit grant if foreground is granted
            if (locationStatus == PermissionStatus.GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        statusMap[Capability.BACKGROUND_LOCATION]?.value = bgLocationStatus

        // Check ACTIVITY_RECOGNITION permission
        val activityStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: requires permission
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        } else {
            // API 24-28: no permission required
            PermissionStatus.NOT_APPLICABLE
        }
        statusMap[Capability.ACTIVITY_RECOGNITION]?.value = activityStatus
    }

    fun onPermissionsChanged() {
        updateStatus()
    }
}
