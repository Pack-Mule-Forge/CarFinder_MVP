package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-024", "FR-035", "FR-037")
class DisplayBearingTest {
    @Test
    fun displayBearingFormula() {
        // display_bearing = (360 - device_heading + bearing_to_car) mod 360
        val deviceHeading = 45.0      // NE
        val bearingToCar = 135.0      // SE
        val expected = (360.0 - 45.0 + 135.0) % 360.0  // 450 % 360 = 90.0 (E)

        val displayBearing = ConeGeometry.displayBearing(deviceHeading, bearingToCar)
        assertTrue(abs(displayBearing - expected) < 0.1, "Display bearing formula incorrect")
    }

    @Test
    fun displayBearingNorthPointing() {
        // Device heading N (0°), car is N (bearing 0°) → display should be N (0°)
        val deviceHeading = 0.0
        val bearingToCar = 0.0
        val expected = 0.0

        val displayBearing = ConeGeometry.displayBearing(deviceHeading, bearingToCar)
        assertTrue(abs(displayBearing - expected) < 0.1)
    }

    @Test
    fun displayBearingEastPointing() {
        // Device heading E (90°), car is E (bearing 90°) → car is straight ahead, display 0°
        val deviceHeading = 90.0
        val bearingToCar = 90.0
        val expected = 0.0

        val displayBearing = ConeGeometry.displayBearing(deviceHeading, bearingToCar)
        assertTrue(abs(displayBearing - expected) < 0.1)
    }

    @Test
    fun displayBearingWithNegativeIntermediate() {
        // Formula can produce negative intermediates; ensure normalization to [0, 360)
        val deviceHeading = 350.0
        val bearingToCar = 10.0
        // (360 - 350 + 10) % 360 = 20

        val displayBearing = ConeGeometry.displayBearing(deviceHeading, bearingToCar)
        assertTrue(displayBearing >= 0.0 && displayBearing < 360.0, "Display bearing must be in [0, 360)")
    }

    @Test
    fun displayBearingAlwaysInRange() {
        // Test multiple random combinations to ensure normalization
        for (heading in arrayOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.9)) {
            for (bearing in arrayOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.9)) {
                val displayBearing = ConeGeometry.displayBearing(heading, bearing)
                assertTrue(
                    displayBearing >= 0.0 && displayBearing < 360.0,
                    "Display bearing must be in [0, 360) for heading=$heading, bearing=$bearing"
                )
            }
        }
    }
}
