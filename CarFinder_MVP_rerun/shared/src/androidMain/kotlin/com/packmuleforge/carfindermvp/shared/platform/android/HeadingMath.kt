package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.SensorManager
import android.view.Surface
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath

/**
 * Converts a rotation vector into the true-north heading of the top of the screen (research R3).
 *
 * @requirement FR-021
 */
object HeadingMath {
    fun azimuth(rotationVector: FloatArray, displayRotation: Int, declinationDegrees: Float): Double {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, rotationVector)
        // Remap so the azimuth is measured for the screen's up direction, not the device's natural orientation.
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapped = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val magneticDegrees = Math.toDegrees(orientation[0].toDouble())
        return GeoMath.normalizeDegrees(magneticDegrees + declinationDegrees)
    }
}
