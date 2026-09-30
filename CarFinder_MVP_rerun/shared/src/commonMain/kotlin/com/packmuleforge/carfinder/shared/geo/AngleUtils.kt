package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement

/**
 * Angular interpolation utilities for smooth cone rotation as the device turns.
 * Handles the shortest path around the 0/360 boundary.
 */
@Requirement("FR-027")
object AngleUtils {
    /**
     * Interpolate between two angles along the shortest path.
     * Example: from 359° to 1° takes the 2° clockwise route (through 0°),
     * not the 358° counter-clockwise route.
     *
     * @param startAngle Start angle in degrees [0, 360)
     * @param endAngle End angle in degrees [0, 360)
     * @param t Interpolation factor [0, 1]
     * @return Interpolated angle in degrees [0, 360)
     */
    fun shortestPathInterpolate(startAngle: Double, endAngle: Double, t: Double): Double {
        var delta = endAngle - startAngle

        // If the difference is more than 180, go the other way
        if (delta > 180.0) {
            delta -= 360.0
        } else if (delta < -180.0) {
            delta += 360.0
        }

        val interpolated = startAngle + (delta * t)
        return ((interpolated % 360.0) + 360.0) % 360.0  // Normalize to [0, 360)
    }
}
