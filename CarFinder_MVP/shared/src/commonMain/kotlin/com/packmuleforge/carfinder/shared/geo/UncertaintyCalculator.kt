package com.packmuleforge.carfinder.shared.geo

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint

/**
 * Calculates the uncertainty radius as the sum of two accuracy values.
 * Never rounds, clamps, or substitutes the value (FR-016).
 */
@Requirement("FR-015", "FR-016")
object UncertaintyCalculator {
    /**
     * Calculate uncertainty radius from parked location and current fix.
     *
     * @param parkedLocationPoint The GeoPoint of the stored parked location
     * @param currentFixPoint The current device position
     * @return The sum of their accuracy radii in meters
     */
    fun calculate(parkedLocationPoint: GeoPoint, currentFixPoint: GeoPoint): Double {
        return parkedLocationPoint.accuracyRadiusMeters + currentFixPoint.accuracyRadiusMeters
    }
}
