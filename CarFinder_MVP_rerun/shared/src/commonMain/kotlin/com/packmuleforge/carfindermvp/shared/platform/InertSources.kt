package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A heading source with no sensor: always `null`. Used until the real source exists. */
class InertHeadingSource : HeadingSource {
    override val heading: StateFlow<HeadingReading?> = MutableStateFlow<HeadingReading?>(null).asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}

/** An activity source with no signal: never in a vehicle. Used until the real source exists. */
class InertActivitySignalSource : ActivitySignalSource {
    override val isInVehicle: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}
