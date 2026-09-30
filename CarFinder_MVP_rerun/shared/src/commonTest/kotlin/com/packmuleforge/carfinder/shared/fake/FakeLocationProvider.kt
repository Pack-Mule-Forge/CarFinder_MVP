package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.platform.LocationProvider
import com.packmuleforge.carfinder.shared.platform.LocationRequestTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test fake for LocationProvider. Yields samples from a pre-scripted list in sequence.
 * Allows deterministic replay of recorded drive/park sequences.
 */
class FakeLocationProvider(
    private val samples: List<LocationSample> = emptyList()
) : LocationProvider {
    override fun samples(request: LocationRequestTier): Flow<LocationSample> = flow {
        for (sample in samples) {
            emit(sample)
        }
    }
}
