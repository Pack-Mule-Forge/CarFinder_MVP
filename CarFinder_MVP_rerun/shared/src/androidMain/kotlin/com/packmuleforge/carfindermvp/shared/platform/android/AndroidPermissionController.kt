package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionController
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime permissions on Android, one platform prompt per [request].
 *
 * Application-scoped: it holds the pending request itself and re-registers its launcher under one fixed key on
 * every [attach], so a result for a prompt opened before the Activity was re-created (rotation) reaches the new
 * registration and resumes the waiting call. [detach] alone never ends a pending request.
 *
 * The platform reports only granted or not, so a "requested" mark per capability, kept in a private preferences
 * file, tells `DENIED` from `NOT_REQUESTED`. Location is requested as fine and coarse together, as Android 12+
 * requires for its precise/approximate choice, and counts as granted only with fine location (FR-056).
 *
 * @requirement FR-047, FR-048, FR-049, FR-056
 */
class AndroidPermissionController(context: Context) : PermissionController {
    private val appContext = context.applicationContext
    private val marks = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val status: StateFlow<PermissionState> = state.asStateFlow()

    @Volatile
    private var launcher: ActivityResultLauncher<Array<String>>? = null

    @Volatile
    private var pending: CompletableDeferred<Unit>? = null

    fun attach(registry: ActivityResultRegistry) {
        launcher = registry.register(RESULT_KEY, ActivityResultContracts.RequestMultiplePermissions()) {
            pending?.complete(Unit)
        }
    }

    fun detach() {
        launcher = null
    }

    override suspend fun request(capability: Capability): Boolean {
        if (!needsRuntimeGrant(capability)) {
            refresh()
            return true
        }
        val current = launcher
        if (current == null) {
            // No Activity to show a prompt from; report the current grant without throwing.
            refresh()
            return state.value.isGranted(capability)
        }
        marks.edit().putBoolean(capability.name, true).apply()
        val answer = CompletableDeferred<Unit>()
        pending = answer
        try {
            current.launch(platformPermissions(capability))
            answer.await()
        } finally {
            if (pending === answer) pending = null
        }
        refresh()
        return state.value.isGranted(capability)
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read() = PermissionState(
        Capability.entries.associateWith { capability ->
            when {
                isGrantedNow(capability) -> PermissionStatus.GRANTED
                marks.getBoolean(capability.name, false) -> PermissionStatus.DENIED
                else -> PermissionStatus.NOT_REQUESTED
            }
        },
    )

    private fun isGrantedNow(capability: Capability): Boolean =
        !needsRuntimeGrant(capability) || platformPermissions(capability).first().let(::isGranted)

    private fun isGranted(permission: String) =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun needsRuntimeGrant(capability: Capability): Boolean = when (capability) {
        Capability.FINE_LOCATION -> true
        Capability.BACKGROUND_LOCATION, Capability.ACTIVITY_RECOGNITION -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        Capability.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /** The platform permissions for one prompt; the first is the one that decides the grant. */
    private fun platformPermissions(capability: Capability): Array<String> = when (capability) {
        Capability.FINE_LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        Capability.BACKGROUND_LOCATION -> arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        Capability.ACTIVITY_RECOGNITION -> arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        Capability.NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val PREFERENCES = "car_finder_permission_marks"
        const val RESULT_KEY = "car_finder_permission_request"
    }
}
