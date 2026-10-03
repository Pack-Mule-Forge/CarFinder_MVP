package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import kotlin.math.atan2
import kotlin.math.roundToLong

/**
 * Everything guidance shows, derived and never stored.
 *
 * @requirement FR-030, FR-031, FR-032, FR-037, FR-038
 */
data class GuidanceState(
    val uncertaintyMeters: Double,
    val distanceMeters: Double,
    val bearingToCarDegrees: Double,
    val displayBearingDegrees: Double,
    val coneHalfAngleDegrees: Double,
    val isArrived: Boolean,
    val distanceText: String,
    val cone: ConeGeometry,
)

/** @requirement FR-030, FR-031, FR-032, FR-037, FR-038 */
object GuidanceCalculator {

    /**
     * Absorbs floating-point rounding at exact boundaries ("at the threshold" and "reaches 45°"), so a value that
     * is mathematically equal to the boundary is treated as equal. Not a tunable value.
     */
    private val BOUNDARY_TOLERANCE: Double = 1e-9

    /** @requirement FR-030 */
    fun uncertaintyMeters(parked: ParkedLocation, fix: LocationReading): Double =
        parked.accuracyMeters + checkNotNull(fix.accuracyMeters) { "a fix without accuracy is never used for guidance" }

    /** `atan(uncertainty / distance)` in degrees; 90 at distance zero (FR-031). */
    fun coneHalfAngleDegrees(uncertaintyMeters: Double, distanceMeters: Double): Double =
        GeoMath.toDegrees(atan2(uncertaintyMeters, distanceMeters))

    /** `(360 − device_heading + bearing_to_car) mod 360` (FR-032). */
    fun displayBearingDegrees(deviceHeading: Double, bearingToCar: Double): Double =
        GeoMath.normalizeDegrees(FULL_TURN_DEGREES - deviceHeading + bearingToCar)

    /** @requirement FR-038 */
    fun isArrived(halfAngleDegrees: Double): Boolean =
        halfAngleDegrees >= CarFinderConstants.ARRIVAL_CONE_HALF_ANGLE_DEGREES - BOUNDARY_TOLERANCE

    /** Whole feet at or below the distance-unit threshold, otherwise miles to two decimals (FR-037). */
    fun distanceText(distanceMeters: Double): String {
        val feet = distanceMeters * CarFinderConstants.FEET_PER_METER
        val threshold = CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET
        if (feet <= threshold * (1 + BOUNDARY_TOLERANCE)) return "${feet.roundToLong()} ft"
        val hundredths = (feet / CarFinderConstants.FEET_PER_MILE * HUNDRED).roundToLong()
        val fraction = (hundredths % HUNDRED.toLong()).toString().padStart(2, '0')
        return "${hundredths / HUNDRED.toLong()}.$fraction mi"
    }

    fun compute(parked: ParkedLocation, fix: LocationReading, heading: HeadingReading): GuidanceState {
        val uncertainty = uncertaintyMeters(parked, fix)
        val distance = GeoMath.distanceMeters(fix.latitude, fix.longitude, parked.latitude, parked.longitude)
        val bearingToCar = GeoMath.initialBearingDegrees(fix.latitude, fix.longitude, parked.latitude, parked.longitude)
        val displayBearing = displayBearingDegrees(heading.trueHeadingDegrees, bearingToCar)
        val halfAngle = coneHalfAngleDegrees(uncertainty, distance)
        return GuidanceState(
            uncertaintyMeters = uncertainty,
            distanceMeters = distance,
            bearingToCarDegrees = bearingToCar,
            displayBearingDegrees = displayBearing,
            coneHalfAngleDegrees = halfAngle,
            isArrived = isArrived(halfAngle),
            distanceText = distanceText(distance),
            cone = ConeGeometryCalculator.compute(displayBearing, halfAngle),
        )
    }

    private val FULL_TURN_DEGREES: Double = 360.0
    private val HUNDRED: Double = 100.0
}
