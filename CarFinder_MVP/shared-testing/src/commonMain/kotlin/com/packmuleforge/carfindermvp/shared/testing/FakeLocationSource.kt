package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.platform.LocationSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeLocationSource : LocationSource {
    private val flow = MutableSharedFlow<LocationReading>(extraBufferCapacity = BUFFER)
    override val readings: SharedFlow<LocationReading> = flow.asSharedFlow()

    /** Every interval passed to [setIntervalMillis], in order. */
    val intervalHistory = mutableListOf<Long>()
    var isStarted = false
        private set
    var startCount = 0
        private set

    val hasSubscriber: Boolean get() = flow.subscriptionCount.value > 0

    suspend fun emit(reading: LocationReading) = flow.emit(reading)

    override fun setIntervalMillis(intervalMillis: Long) {
        intervalHistory += intervalMillis
    }

    override fun start() {
        isStarted = true
        startCount++
    }

    override fun stop() {
        isStarted = false
    }

    private companion object {
        const val BUFFER = 256
    }
}
