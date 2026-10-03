package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeHeadingSource : HeadingSource {
    private val state = MutableStateFlow<HeadingReading?>(null)
    override val heading: StateFlow<HeadingReading?> = state.asStateFlow()

    var isStarted = false
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set

    fun emit(reading: HeadingReading?) {
        state.value = reading
    }

    override fun start() {
        isStarted = true
        startCount++
    }

    override fun stop() {
        isStarted = false
        stopCount++
    }
}
