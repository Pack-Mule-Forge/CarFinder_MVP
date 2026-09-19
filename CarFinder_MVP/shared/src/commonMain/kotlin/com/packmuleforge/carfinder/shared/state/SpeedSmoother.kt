package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Rolling 3-median filter for speed readings. Suppresses single spurious spikes (e.g., from GPS
 * jitter during stop-and-go traffic) without preventing genuine sustained speed changes needed
 * for state transitions (FR-002, FR-003, FR-004, FR-010, SC-009).
 *
 * Keeps a window of the last 3 readings and returns their median. The first two readings are
 * buffered until we have 3, then every subsequent reading slides the window.
 */
@Requirement("FR-002", "FR-003", "FR-004", "FR-010", "SC-009")
class SpeedSmoother {
    private val window = mutableListOf<Double>()
    private val windowSize = 3

    fun smooth(speedMetersPerSecond: Double): Double {
        window.add(speedMetersPerSecond)

        if (window.size > windowSize) {
            window.removeAt(0)
        }

        // If we don't have 3 readings yet, return the last reading as-is
        // (first two readings pass through to prime the buffer)
        if (window.size < windowSize) {
            return speedMetersPerSecond
        }

        // Return the median of the 3-reading window
        return window.sorted()[1]  // Middle element in sorted order
    }
}
