package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.ActivitySignalSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class FakeActivitySignalSource : ActivitySignalSource {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val inVehicleEntered: SharedFlow<Unit> = flow

    var isStarted = false
        private set

    suspend fun emitInVehicle() = flow.emit(Unit)

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }
}
