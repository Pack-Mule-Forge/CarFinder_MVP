package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import com.packmuleforge.carfindermvp.shared.platform.LocationSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class FakeLocationSource : LocationSource {
    private val flow = MutableSharedFlow<LocationReading>(extraBufferCapacity = 64)
    override val readings: SharedFlow<LocationReading> = flow

    /** Every profile passed to [setProfile], in order. */
    val profileHistory = mutableListOf<SamplingProfile>()
    var isStarted = false
        private set

    suspend fun emit(reading: LocationReading) = flow.emit(reading)

    override fun setProfile(profile: SamplingProfile) {
        profileHistory += profile
    }

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }
}
