package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticLog

class RecordingDiagnosticLog : DiagnosticLog {
    val events = mutableListOf<DiagnosticEvent>()

    override fun record(event: DiagnosticEvent) {
        events += event
    }
}
