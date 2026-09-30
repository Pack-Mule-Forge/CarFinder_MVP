package com.packmuleforge.carfindermvp.shared.guidance

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor

enum class DistanceUnit { FEET, MILES }

/** Distance for display: the value in [unit] and its formatted text, e.g. "412 ft" or "0.37 mi". */
data class DistanceDisplay(val value: Double, val unit: DistanceUnit, val text: String)

/** Everything the guidance display needs, derived (never persisted). */
data class GuidanceState(
    val uncertaintyMeters: Double,
    val distanceMeters: Double,
    val bearingToCarDegrees: Double,
    val displayBearingDegrees: Double,
    val coneHalfAngleDegrees: Double,
    val isArrived: Boolean,
    val distanceDisplay: DistanceDisplay,
)

/**
 * The guidance math. Formatting uses integer arithmetic only, so it is portable to non-JVM targets (research R9).
 *
 * @requirement FR-019, FR-020, FR-021, FR-025, FR-026, FR-028
 */
object GuidanceCalculator {

    fun uncertaintyMeters(parked: ParkedLocation, fix: LocationReading): Double = Uncertainty.radiusMeters(parked, fix)

    /** `atan(uncertainty / distance)`, written as atan2 so a zero distance gives 90° instead of failing. */
    fun coneHalfAngleDegrees(uncertaintyMeters: Double, distanceMeters: Double): Double =
        atan2(uncertaintyMeters, distanceMeters) * 180.0 / PI

    /** `(360 − device_heading + bearing_to_car) mod 360`: where the car is, measured from the top of the screen. */
    fun displayBearingDegrees(deviceHeading: Double, bearingToCar: Double): Double =
        GeoMath.normalizeDegrees(360.0 - deviceHeading + bearingToCar)

    /**
     * The cone can no longer honestly point anywhere once its half-angle reaches the arrival angle, which is the
     * same as uncertainty >= distance.
     *
     * @requirement FR-028
     */
    fun isArrived(halfAngleDegrees: Double): Boolean = halfAngleDegrees >= CarFinderConstants.ARRIVAL_HALF_ANGLE_DEGREES

    /** Feet at or below the unit threshold, miles above it. */
    fun distanceDisplay(distanceMeters: Double): DistanceDisplay {
        val feet = distanceMeters * CarFinderConstants.METERS_TO_FEET
        return if (feet <= CarFinderConstants.DISTANCE_UNIT_THRESHOLD_FEET) {
            val whole = roundHalfUp(feet)
            DistanceDisplay(whole.toDouble(), DistanceUnit.FEET, "$whole ft")
        } else {
            val hundredths = roundHalfUp(feet / CarFinderConstants.FEET_PER_MILE * 100)
            val text = "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} mi"
            DistanceDisplay(hundredths / 100.0, DistanceUnit.MILES, text)
        }
    }

    /** Combines one parked location, one current fix (with accuracy) and one heading into guidance. */
    fun compute(parked: ParkedLocation, fix: LocationReading, heading: HeadingReading): GuidanceState {
        val distance = GeoMath.distanceMeters(fix.latitude, fix.longitude, parked.latitude, parked.longitude)
        val bearing = GeoMath.initialBearingDegrees(fix.latitude, fix.longitude, parked.latitude, parked.longitude)
        val uncertainty = uncertaintyMeters(parked, fix)
        val halfAngle = coneHalfAngleDegrees(uncertainty, distance)
        return GuidanceState(
            uncertaintyMeters = uncertainty,
            distanceMeters = distance,
            bearingToCarDegrees = bearing,
            displayBearingDegrees = displayBearingDegrees(heading.trueHeadingDegrees, bearing),
            coneHalfAngleDegrees = halfAngle,
            isArrived = isArrived(halfAngle),
            distanceDisplay = distanceDisplay(distance),
        )
    }

    private fun roundHalfUp(value: Double): Long = floor(value + 0.5).toLong()
}
