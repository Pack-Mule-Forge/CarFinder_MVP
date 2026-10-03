package com.packmuleforge.carfinder.shared.platform

/**
 * T110 construction fix (CR-5/CR-8 follow-up). AndroidPermissionController is constructed with
 * only an Application Context (see CarFinderApplication), which can check permission status but
 * cannot show a permission dialog — that requires a live Activity. Previously `request()` was a
 * no-op that just re-read `status()`.
 *
 * This interface is the seam: an Activity implements it over its own
 * `androidx.activity.result.ActivityResultLauncher` and assigns itself to
 * [AndroidPermissionController.requester]. When no requester is attached (no Activity available,
 * e.g. calls from the service), `request()` falls back to a status-only read and never throws —
 * preserving the original "safe to call with no Activity" contract from
 * contracts/platform-adapters.md.
 */
interface PermissionRequester {
    /** Launch the system permission dialog for [permission] and suspend for the user's answer. */
    suspend fun requestPermission(permission: String): Boolean

    /** Mirrors Activity/Fragment `shouldShowRequestPermissionRationale`. */
    fun shouldShowRationale(permission: String): Boolean
}
