package com.packmuleforge.carfinder.shared.view

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.geo.AngleUtils
import com.packmuleforge.carfinder.shared.geo.ConeGeometry
import com.packmuleforge.carfinder.shared.geo.DistanceFormatter
import com.packmuleforge.carfinder.shared.geo.Geodesy
import com.packmuleforge.carfinder.shared.geo.UncertaintyCalculator
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState

/**
 * Pure calculator for guidance view state. Computes which of the four views to show and
 * all the fields needed to render it. No I/O, no state, no clock (FR-042).
 *
 * All computation lives here; Composables are pure functions of this output.
 */
@Requirement("FR-018", "FR-019", "FR-020", "FR-021", "FR-022", "FR-030", "FR-042")
object GuidanceViewStateCalculator {
    fun calculate(
        parkingState: ParkingState,
        parkedLocation: ParkedLocation?,
        currentFix: GeoPoint?,
        deviceHeading: Double?,
        currentHeading: Double?
    ): GuidanceViewState {
        // FR-018: Strict priority order
        // 1. DRIVING → always "Driving - Waiting to Park"
        if (parkingState == ParkingState.DRIVING) {
            return GuidanceViewState.Driving
        }

        // 2. No location ever stored (FINDING) → "No parked Location yet."
        if (parkedLocation == null) {
            return GuidanceViewState.NoParkedLocation
        }

        // 3. PARKING → "Sensing you will be Parking Soon."
        if (parkingState == ParkingState.PARKING) {
            return GuidanceViewState.ParkingSoon
        }

        // 4. Otherwise (PARKED with location) → Guidance
        // But verify we have the required inputs
        if (parkingState == ParkingState.PARKED && currentFix != null && deviceHeading != null && currentHeading != null) {
            val distance = Geodesy.distanceMeters(parkedLocation.point, currentFix)
            val bearing = Geodesy.trueBearingDegrees(currentFix, parkedLocation.point)
            val uncertainty = UncertaintyCalculator.calculate(parkedLocation.point, currentFix)
            val halfAngle = ConeGeometry.halfAngleRadians(uncertainty, distance)
            val displayBearing = ConeGeometry.displayBearing(deviceHeading, bearing)
            val hasArrived = ConeGeometry.hasArrived(halfAngle)

            return GuidanceViewState.Guidance(
                distanceMeters = distance,
                formattedDistance = DistanceFormatter.format(distance),
                displayBearingDegrees = displayBearing,
                coneHalfAngleRadians = halfAngle,
                hasArrived = hasArrived
            )
        }

        // FR-030: Undefined state → fallback to FINDING
        return GuidanceViewState.NoParkedLocation
    }
}
