package com.packmuleforge.carfindermvp.shared.platform.android

import android.view.Surface
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rotation vector → true-north heading of the top of the screen.
 * @requirement QR-004
 */
@RunWith(RobolectricTestRunner::class)
class HeadingMathTest {

    /**
     * Rotation vector for a phone lying flat, screen up, whose top edge points at [azimuthDegrees]: a rotation
     * about the world z axis by -azimuth (azimuth is measured clockwise from north seen from above).
     */
    private fun flatFacing(azimuthDegrees: Double): FloatArray {
        val half = -azimuthDegrees * PI / 180 / 2
        return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    private fun angularDifference(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180) 360 - d else d
    }

    private val rotations = mapOf(
        Surface.ROTATION_0 to 0.0,
        Surface.ROTATION_90 to 90.0,
        Surface.ROTATION_180 to 180.0,
        Surface.ROTATION_270 to 270.0,
    )

    /** @requirement FR-021 */
    @Test
    fun portrait_headingIsTheDirectionTheTopOfTheDeviceFaces() {
        for (azimuth in listOf(0.0, 90.0, 180.0, 270.0)) {
            val heading = HeadingMath.azimuth(flatFacing(azimuth), Surface.ROTATION_0, declinationDegrees = 0f)
            assertTrue(angularDifference(heading, azimuth) < 0.5, "facing $azimuth gave $heading")
        }
    }

    /**
     * With the display rotated, the heading is the direction the top of the *screen* faces. For ROTATION_90 the
     * device is turned counter-clockwise, so the screen top is the device's right edge: azimuth + 90.
     * @requirement FR-021
     */
    @Test
    fun rotatedDisplay_headingFollowsTheScreenTop() {
        for (azimuth in listOf(0.0, 90.0, 180.0, 270.0)) {
            for ((rotation, offset) in rotations) {
                val heading = HeadingMath.azimuth(flatFacing(azimuth), rotation, declinationDegrees = 0f)
                val expected = (azimuth + offset) % 360.0
                assertTrue(angularDifference(heading, expected) < 0.5, "facing $azimuth, rotation $rotation gave $heading")
            }
        }
    }

    /** @requirement FR-021 */
    @Test
    fun declination_isAdded_andResultIsNormalized() {
        val declination = 17f
        val heading = HeadingMath.azimuth(flatFacing(350.0), Surface.ROTATION_0, declination)
        assertTrue(angularDifference(heading, 7.0) < 0.5, "gave $heading")
        assertTrue(heading >= 0.0 && heading < 360.0)
    }
}
