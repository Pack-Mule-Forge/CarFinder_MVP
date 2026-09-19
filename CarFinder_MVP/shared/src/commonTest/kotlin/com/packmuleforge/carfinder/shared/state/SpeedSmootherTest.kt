package com.packmuleforge.carfinder.shared.state

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.test.Test
import kotlin.test.assertEquals

@Requirement("FR-002", "FR-003", "FR-004", "FR-037", "SC-009")
class SpeedSmootherTest {
    @Test
    fun suppressesSingleSpuriousSpike() {
        val smoother = SpeedSmoother()

        // Sequence: normal, spike, normal
        val normal1 = ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - 1.0  // 4 m/s
        val spike = ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS + 10.0   // 35 m/s (spurious)
        val normal2 = ParkingConstants.PARKING_SPEED_THRESHOLD_MPS - 1.0  // 4 m/s

        val smoothed1 = smoother.smooth(normal1)
        val smoothed2 = smoother.smooth(spike)
        val smoothed3 = smoother.smooth(normal2)

        // After three readings, we have: [4, 35, 4]
        // Median is 4, so spike should be suppressed
        assertEquals(normal1, smoothed1, "First reading should pass through")
        // Second reading: median of [4, 35] → cannot compute yet (need 3)
        // Third reading: median of [4, 35, 4] → 4
        assertEquals(normal2, smoothed3, "Spike should be suppressed by median filter")
    }

    @Test
    fun allowsGenuineSustainedCrossing() {
        val smoother = SpeedSmoother()

        val highSpeed1 = ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS + 5.0
        val highSpeed2 = ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS + 5.0
        val highSpeed3 = ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS + 5.0

        smoother.smooth(highSpeed1)
        smoother.smooth(highSpeed2)
        val smoothed3 = smoother.smooth(highSpeed3)

        // Genuine high speeds: [30, 30, 30] → median is 30
        assertEquals(highSpeed3, smoothed3, "Genuine sustained crossing should be allowed")
    }

    @Test
    fun outputMedianOfThreeReadings() {
        val smoother = SpeedSmoother()

        val speeds = listOf(10.0, 5.0, 15.0, 8.0, 12.0)
        val results = mutableListOf<Double>()

        for (speed in speeds) {
            results.add(smoother.smooth(speed))
        }

        // Window: [10, 5, 15] → median = 10
        // Window: [5, 15, 8] → median = 8
        // Window: [15, 8, 12] → median = 12

        assertEquals(10.0, results[2], "First median result should be 10")
        assertEquals(8.0, results[3], "Second median result should be 8")
        assertEquals(12.0, results[4], "Third median result should be 12")
    }
}
