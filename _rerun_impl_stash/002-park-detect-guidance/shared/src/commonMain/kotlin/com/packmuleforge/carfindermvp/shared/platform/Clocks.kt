package com.packmuleforge.carfindermvp.shared.platform

/**
 * Monotonic time since boot, immune to wall-clock changes; used for fix and heading currency (research R9).
 *
 * @requirement QR-010
 */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

/**
 * Wall-clock time, used only for timestamps such as `ParkedLocation.capturedAtEpochMillis`.
 *
 * @requirement QR-010
 */
fun interface WallClock {
    fun epochMillis(): Long
}
