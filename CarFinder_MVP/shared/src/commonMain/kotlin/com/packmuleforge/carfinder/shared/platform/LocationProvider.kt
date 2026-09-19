package com.packmuleforge.carfinder.shared.platform

import com.packmuleforge.carfinder.shared.model.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * Tuning levels for location requests. The adapter uses these to select accuracy and update
 * frequency for the Fused Location Provider (FR-005, research.md R-03).
 *
 * - DRIVING: balanced accuracy and frequency (~15–30 s interval, balanced accuracy)
 * - PARKING: high accuracy, elevated frequency (5 s interval per PARKING_SAMPLE_INTERVAL)
 * - PARKED: low frequency, balanced accuracy (~30–60 s interval)
 */
enum class LocationRequestTier {
    DRIVING, PARKING, PARKED
}

/**
 * Platform abstraction for location samples. Yields a continuous Flow of LocationSample objects
 * as the device's position changes. Updates cease when the consumer unsubscribes.
 */
expect class LocationProvider {
    fun samples(request: LocationRequestTier): Flow<LocationSample>
}
