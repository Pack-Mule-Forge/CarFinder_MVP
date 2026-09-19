package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@Requirement("FR-027")
class AngleUtilsTest {
    @Test
    fun shortestPathFrom359To1() {
        // From 359° to 1° should take the 2° clockwise route, not the 358° counter-clockwise
        val start = 359.0
        val end = 1.0
        val t = 0.5  // halfway

        val interpolated = AngleUtils.shortestPathInterpolate(start, end, t)

        // At halfway, should be at 0° (between 359 and 1)
        assertTrue(
            abs(interpolated - 0.0) < 2.0 || abs(interpolated - 360.0) < 2.0,
            "Shortest path from 359 to 1 should cross 0/360"
        )
    }

    @Test
    fun shortestPathNormalCases() {
        val start = 0.0
        val end = 90.0
        val t = 0.5

        val interpolated = AngleUtils.shortestPathInterpolate(start, end, t)
        assertTrue(abs(interpolated - 45.0) < 0.1, "Interpolation at t=0.5 should be halfway")
    }

    @Test
    fun interpolationBoundsAtT0() {
        val interpolated = AngleUtils.shortestPathInterpolate(10.0, 20.0, 0.0)
        assertTrue(abs(interpolated - 10.0) < 0.1, "At t=0, should return start angle")
    }

    @Test
    fun interpolationBoundsAtT1() {
        val interpolated = AngleUtils.shortestPathInterpolate(10.0, 20.0, 1.0)
        assertTrue(abs(interpolated - 20.0) < 0.1, "At t=1, should return end angle")
    }

    @Test
    fun resultAlwaysInRange() {
        for (start in arrayOf(0.0, 90.0, 180.0, 270.0, 359.0)) {
            for (end in arrayOf(0.0, 90.0, 180.0, 270.0, 359.0)) {
                for (t in arrayOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                    val result = AngleUtils.shortestPathInterpolate(start, end, t)
                    assertTrue(
                        result >= 0.0 && result <= 360.0,
                        "Interpolated angle must be in [0, 360]"
                    )
                }
            }
        }
    }
}
