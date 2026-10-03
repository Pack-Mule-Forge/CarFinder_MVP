package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.SensorManager
import android.view.Surface
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath

/**
 * Pure heading math: remap the rotation matrix for the display rotation, take the azimuth, add declination.
 *
 * @requirement FR-033
 */
object HeadingMath {

    /** The `remapCoordinateSystem` axes for each display rotation, as the Android documentation gives them. */
    fun remapAxes(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    /** The azimuth of the screen's up direction, clockwise from magnetic north, in [0, 360). */
    fun magneticAzimuthDegrees(rotationVector: FloatArray, displayRotation: Int): Double {
        val rotation = FloatArray(MATRIX_SIZE)
        SensorManager.getRotationMatrixFromVector(rotation, rotationVector)
        val (x, y) = remapAxes(displayRotation)
        val remapped = FloatArray(MATRIX_SIZE)
        SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        return GeoMath.normalizeDegrees(GeoMath.toDegrees(orientation[0].toDouble()))
    }

    /** Magnetic azimuth corrected to true north, in [0, 360). */
    fun trueHeadingDegrees(magneticAzimuthDegrees: Double, declinationDegrees: Double): Double =
        GeoMath.normalizeDegrees(magneticAzimuthDegrees + declinationDegrees)

    private const val MATRIX_SIZE = 9
}
