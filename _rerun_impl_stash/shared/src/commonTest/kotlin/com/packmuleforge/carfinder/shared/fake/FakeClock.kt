package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.platform.Clock

/**
 * Test fake for Clock. Allows advancing time manually for deterministic testing.
 */
class FakeClock : Clock {
    var currentTimeMillis: Long = System.currentTimeMillis()
        private set

    override fun nowEpochMillis(): Long = currentTimeMillis

    fun advanceBy(deltaMs: Long) {
        currentTimeMillis += deltaMs
    }

    fun setTime(epochMillis: Long) {
        currentTimeMillis = epochMillis
    }
}
