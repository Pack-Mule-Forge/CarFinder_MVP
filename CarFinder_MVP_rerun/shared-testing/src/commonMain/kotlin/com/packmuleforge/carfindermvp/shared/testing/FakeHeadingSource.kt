package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeHeadingSource : HeadingSource {
    private val flow = MutableStateFlow<HeadingReading?>(null)
    override val headings: StateFlow<HeadingReading?> = flow

    var started = false
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set
    val declinationFixes = mutableListOf<LocationReading>()

    fun emit(heading: HeadingReading?) {
        flow.value = heading
    }

    override fun start() {
        started = true
        startCount++
    }

    override fun stop() {
        started = false
        stopCount++
    }

    override fun updateDeclinationFrom(fix: LocationReading) {
        declinationFixes += fix
    }
}
