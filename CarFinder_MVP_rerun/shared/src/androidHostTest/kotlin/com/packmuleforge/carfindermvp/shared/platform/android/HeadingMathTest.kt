package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.SensorManager
import android.view.Surface
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
class HeadingMathTest {

    private val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)

    /**
     * The rotation vector of a phone lying flat whose natural top edge points [topAzimuthDegrees] clockwise from north.
     */
    private fun flatPhone(topAzimuthDegrees: Double): FloatArray {
        val theta = -topAzimuthDegrees * PI / 180
        return floatArrayOf(0f, 0f, sin(theta / 2).toFloat(), cos(theta / 2).toFloat())
    }

    /** Clockwise screen rotation, in degrees, for a display rotation constant. */
    private fun degrees(rotation: Int) = rotation * 90.0

    private fun angularDifference(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return minOf(d, 360 - d)
    }

    /** @requirement FR-033 */
    @Test
    fun theRemapAxesAreTheDocumentedOnesForEachRotation() {
        assertEquals(SensorManager.AXIS_X to SensorManager.AXIS_Y, HeadingMath.remapAxes(Surface.ROTATION_0))
        assertEquals(SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X, HeadingMath.remapAxes(Surface.ROTATION_90))
        assertEquals(SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y, HeadingMath.remapAxes(Surface.ROTATION_180))
        assertEquals(SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X, HeadingMath.remapAxes(Surface.ROTATION_270))
    }

    /** @requirement FR-033 */
    @Test
    fun theScreenTopPointingAtAKnownDirectionGivesThatHeadingInEveryRotation() {
        for (target in listOf(0.0, 30.0, 135.0, 260.0)) for (rotation in rotations) {
            // Turning the screen by the display rotation turns the phone's natural top edge the other way.
            val phone = flatPhone(target - degrees(rotation))
            val heading = HeadingMath.magneticAzimuthDegrees(phone, rotation)
            assertTrue(angularDifference(target, heading) < 0.5, "target $target rotation $rotation gave $heading")
        }
    }

    /** @requirement FR-033 */
    @Test
    fun withoutTheRemapTheHeadingDiffersInTheThreeOtherRotations() {
        for (rotation in rotations - Surface.ROTATION_0) {
            val phone = flatPhone(30.0 - degrees(rotation))
            val remapped = HeadingMath.magneticAzimuthDegrees(phone, rotation)
            val unremapped = HeadingMath.magneticAzimuthDegrees(phone, Surface.ROTATION_0)
            assertTrue(angularDifference(remapped, unremapped) > 45, "rotation $rotation")
        }
    }

    /** @requirement FR-033 */
    @Test
    fun declinationIsAddedAndTheResultWrapsIntoZeroToThreeSixty() {
        assertEquals(15.0, HeadingMath.trueHeadingDegrees(magneticAzimuthDegrees = 3.0, declinationDegrees = 12.0), 1e-9)
        assertEquals(7.0, HeadingMath.trueHeadingDegrees(magneticAzimuthDegrees = 352.0, declinationDegrees = 15.0), 1e-9)
        assertEquals(351.0, HeadingMath.trueHeadingDegrees(magneticAzimuthDegrees = 6.0, declinationDegrees = -15.0), 1e-9)
        assertEquals(0.0, HeadingMath.trueHeadingDegrees(magneticAzimuthDegrees = -15.0, declinationDegrees = 15.0), 1e-9)
        for (azimuth in listOf(-180.0, -1.0, 0.0, 179.9, 359.9)) for (declination in listOf(-30.0, 0.0, 30.0)) {
            val heading = HeadingMath.trueHeadingDegrees(azimuth, declination)
            assertTrue(heading >= 0.0 && heading < 360.0, "$azimuth + $declination = $heading")
        }
    }
}
