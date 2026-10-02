package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation

/**
 * Combined position uncertainty: both accuracy radii summed, never dropped or approximated.
 *
 * @requirement FR-019
 */
object Uncertainty {
    fun radiusMeters(parked: ParkedLocation, fix: LocationReading): Double {
        val fixAccuracy = requireNotNull(fix.accuracyMeters) { "a fix without accuracy cannot be used for guidance" }
        return parked.accuracyMeters + fixAccuracy
    }
}
