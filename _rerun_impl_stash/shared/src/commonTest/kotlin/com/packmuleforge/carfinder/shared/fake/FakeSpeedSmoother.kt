package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.state.SpeedSmoother

/**
 * Test fake for SpeedSmoother. In tests, we can pass through unsmoothed speeds or use
 * the actual SpeedSmoother; this is just a placeholder for consistency.
 */
class FakeSpeedSmoother : SpeedSmoother() {
    // Uses the real implementation; no override needed
}
