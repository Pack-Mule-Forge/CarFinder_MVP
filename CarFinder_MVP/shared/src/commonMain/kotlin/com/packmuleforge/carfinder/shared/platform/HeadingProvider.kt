package com.packmuleforge.carfinder.shared.platform

import kotlinx.coroutines.flow.Flow

/**
 * Platform abstraction for device heading. Yields a continuous Flow of heading values in degrees,
 * **true-north corrected** (0…360), updated as the device rotates. Updates cease when the consumer
 * unsubscribes.
 *
 * The heading is derived from the device's magnetic sensor and corrected to true north using the
 * geomagnetic field declination at the user's current location.
 */
expect class HeadingProvider {
    fun headingDegrees(): Flow<Double>
}
