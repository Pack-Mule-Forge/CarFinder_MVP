package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACTIVITY_RECOGNITION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.platform.PermissionController
import com.packmuleforge.carfindermvp.shared.platform.RequestMode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android's Activity-based runtime-permission flow, normalized into one suspend [request] (research R6).
 * Requests are sequenced: foreground location, notifications, activity recognition, then background location as
 * its own request (required on API 30+). A denied foreground location stops the sequence.
 *
 * @requirement FR-033, QR-010
 */
class AndroidPermissionController(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val isGranted: (String) -> Boolean = {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    },
) : PermissionController {

    private val denied = mutableSetOf<Capability>()
    private val _status = MutableStateFlow(computeStatus())
    override val status: StateFlow<Map<Capability, CapabilityStatus>> = _status.asStateFlow()

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CancellableContinuation<Map<String, Boolean>>? = null

    fun attach(activity: ComponentActivity) = attach(activity.activityResultRegistry)

    fun attach(registry: ActivityResultRegistry) {
        detach()
        launcher = registry.register(REGISTRY_KEY, ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.let { continuation ->
                pending = null
                continuation.resume(result)
            }
        }
    }

    fun detach() {
        launcher?.unregister()
        launcher = null
        pending?.cancel()
        pending = null
    }

    /** Re-reads the platform state, e.g. after the user changed a permission in system Settings. */
    fun refresh() {
        _status.value = computeStatus()
    }

    override fun requestMode(capability: Capability): RequestMode = when (capability) {
        Capability.LOCATION_FOREGROUND -> RequestMode.EXPLICIT
        Capability.NOTIFICATIONS -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) RequestMode.EXPLICIT else RequestMode.NOT_REQUIRED
        Capability.MOTION_ACTIVITY, Capability.LOCATION_BACKGROUND ->
            if (sdkInt >= Build.VERSION_CODES.Q) RequestMode.EXPLICIT else RequestMode.NOT_REQUIRED
    }

    override suspend fun request(capabilities: Set<Capability>): Map<Capability, CapabilityStatus> {
        for (capability in SEQUENCE.filter { it in capabilities }) {
            if (requestMode(capability) != RequestMode.EXPLICIT) continue
            if (permissionsFor(capability).all(isGranted)) continue
            val result = launch(permissionsFor(capability))
            val grantedNow = if (capability == Capability.LOCATION_FOREGROUND) result.values.any { it } else result.values.all { it }
            if (grantedNow) denied -= capability else denied += capability
            refresh()
            if (capability == Capability.LOCATION_FOREGROUND && !grantedNow) break
        }
        refresh()
        return _status.value.filterKeys { it in capabilities }
    }

    private suspend fun launch(permissions: List<String>): Map<String, Boolean> {
        val launcher = checkNotNull(launcher) { "attach() must be called before request()" }
        return suspendCancellableCoroutine { continuation ->
            pending = continuation
            launcher.launch(permissions.toTypedArray())
        }
    }

    private fun computeStatus(): Map<Capability, CapabilityStatus> = Capability.entries.associateWith { capability ->
        when {
            requestMode(capability) == RequestMode.NOT_REQUIRED -> CapabilityStatus.GRANTED
            capability == Capability.LOCATION_FOREGROUND && permissionsFor(capability).any(isGranted) ->
                CapabilityStatus.GRANTED
            capability != Capability.LOCATION_FOREGROUND && permissionsFor(capability).all(isGranted) ->
                CapabilityStatus.GRANTED
            capability in denied -> CapabilityStatus.DENIED
            else -> CapabilityStatus.NOT_DETERMINED
        }
    }

    private fun permissionsFor(capability: Capability): List<String> = when (capability) {
        Capability.LOCATION_FOREGROUND -> listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        Capability.NOTIFICATIONS -> listOf(POST_NOTIFICATIONS)
        Capability.MOTION_ACTIVITY -> listOf(ACTIVITY_RECOGNITION)
        Capability.LOCATION_BACKGROUND -> listOf(ACCESS_BACKGROUND_LOCATION)
    }

    private companion object {
        const val REGISTRY_KEY = "carfinder.permissions"

        val SEQUENCE = listOf(
            Capability.LOCATION_FOREGROUND,
            Capability.NOTIFICATIONS,
            Capability.MOTION_ACTIVITY,
            Capability.LOCATION_BACKGROUND,
        )
    }
}
