package com.packmuleforge.carfindermvp.shared.platform

/** How a permission sequence ended. */
sealed interface PermissionSequenceResult {
    data class Completed(val state: PermissionState) : PermissionSequenceResult

    /** The user confirmed closing after denying [capability]; no later capability was requested. */
    data class CloseConfirmed(val capability: Capability) : PermissionSequenceResult
}

/**
 * The one place the launch order and the FR-056 confirm-or-ask-again loop live; deliberately not part of
 * [PermissionController], which keeps its single suspend function.
 *
 * Required capabilities are requested whenever not granted, whatever earlier answers were; on a denial
 * [confirmDenial] decides between closing (`true`) and asking again (`false`). Background location and activity
 * recognition are requested only while never requested, and their denial moves on. Background location is skipped
 * while location is not granted.
 *
 * @requirement FR-048, FR-056
 */
suspend fun requestPermissionsInOrder(
    controller: PermissionController,
    confirmDenial: suspend (Capability) -> Boolean,
): PermissionSequenceResult {
    for (capability in Capability.entries) {
        val state = controller.status.value
        if (state.isGranted(capability)) continue
        if (capability == Capability.BACKGROUND_LOCATION && !state.isGranted(Capability.FINE_LOCATION)) continue
        if (capability in PermissionState.REQUIRED) {
            while (!controller.request(capability)) {
                if (confirmDenial(capability)) return PermissionSequenceResult.CloseConfirmed(capability)
            }
        } else if (state[capability] == PermissionStatus.NOT_REQUESTED) {
            controller.request(capability)
        }
    }
    return PermissionSequenceResult.Completed(controller.status.value)
}
