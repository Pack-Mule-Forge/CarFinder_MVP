package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.ActivitySignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeActivitySignalSource : ActivitySignalSource {
    private val state = MutableStateFlow(false)
    override val isInVehicle: StateFlow<Boolean> = state.asStateFlow()

    var isStarted = false
        private set

    fun set(inVehicle: Boolean) {
        state.value = inVehicle
    }

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }
}
