package com.packmuleforge.carfindermvp.shared.platform.android

import android.util.Log
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticLog

/**
 * Writes the event type and how many times it has occurred. Never coordinates.
 *
 * @requirement FR-020
 */
class AndroidDiagnosticLog : DiagnosticLog {
    private val counts = mutableMapOf<String, Int>()

    @Synchronized
    override fun record(event: DiagnosticEvent) {
        val name = event::class.simpleName ?: "DiagnosticEvent"
        val count = (counts[name] ?: 0) + 1
        counts[name] = count
        Log.w(TAG, "$name count=$count")
    }

    private companion object {
        const val TAG = "CarFinder"
    }
}
