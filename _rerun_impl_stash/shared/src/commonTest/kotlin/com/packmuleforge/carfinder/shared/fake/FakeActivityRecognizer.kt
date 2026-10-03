package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.platform.ActivityRecognizer
import com.packmuleforge.carfinder.shared.platform.VehicleTransition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test fake for ActivityRecognizer. Yields transitions from a pre-scripted list in sequence.
 */
class FakeActivityRecognizer(
    private val transitions: List<VehicleTransition> = emptyList()
) : ActivityRecognizer {
    override fun inVehicleTransitions(): Flow<VehicleTransition> = flow {
        for (transition in transitions) {
            emit(transition)
        }
    }
}
