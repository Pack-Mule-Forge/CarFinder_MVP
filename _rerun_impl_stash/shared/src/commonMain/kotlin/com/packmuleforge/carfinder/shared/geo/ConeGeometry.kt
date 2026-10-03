package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.constants.ParkingConstants
import kotlin.math.PI
import kotlin.math.atan

/**
 * Guidance display cone geometry: half-angle and bearing calculations.
 * All computations are pure; no I/O or state (FR-042).
 */
@Requirement("FR-023", "FR-024", "FR-031", "FR-042")
object ConeGeometry {
    /**
     * Compute cone half-angle from uncertainty and distance.
     * Formula: atan(uncertainty / distance).
     * At zero distance, returns π/2 (90°) to indicate arrival without NaN (FR-023).
     *
     * @param uncertaintyMeters The uncertainty radius in meters
     * @param distanceMeters The distance to the parked location in meters
     * @return Half-angle in radians
     */
    fun halfAngleRadians(uncertaintyMeters: Double, distanceMeters: Double): Double {
        return if (distanceMeters == 0.0) {
            PI / 2.0  // 90 degrees: arrival
        } else {
            atan(uncertaintyMeters / distanceMeters)
        }
    }

    /**
     * Compute the display bearing for centering the cone.
     * Formula: (360 - deviceHeadingTrueNorth + bearingToCar) mod 360
     * Result is always in [0, 360) so the cone tracks the vehicle as the device turns (FR-024).
     *
     * @param deviceHeadingTrueNorth Device heading in true north degrees [0, 360)
     * @param bearingToCar Bearing to the vehicle in true north degrees [0, 360)
     * @return Display bearing in degrees [0, 360)
     */
    fun displayBearing(deviceHeadingTrueNorth: Double, bearingToCar: Double): Double {
        var bearing = (360.0 - deviceHeadingTrueNorth + bearingToCar) % 360.0
        if (bearing < 0.0) {
            bearing += 360.0
        }
        return bearing
    }

    /**
     * Check if the cone half-angle indicates arrival.
     * True when half-angle >= ARRIVAL_CONE_HALF_ANGLE (FR-031).
     *
     * @param halfAngleRadians The cone half-angle in radians
     * @return True if arrival condition is met
     */
    fun hasArrived(halfAngleRadians: Double): Boolean {
        return halfAngleRadians >= ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS
    }
}
