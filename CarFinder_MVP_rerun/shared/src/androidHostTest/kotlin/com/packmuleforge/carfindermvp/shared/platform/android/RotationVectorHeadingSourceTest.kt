package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import com.packmuleforge.carfindermvp.shared.testing.FakeMonotonicClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
class RotationVectorHeadingSourceTest {

    private class FakeSensorPort(var hasSensor: Boolean = true, var declination: Double = 0.0) : SensorPort {
        val registeredTypes = mutableListOf<Int>()
        var listener: SensorPort.Listener? = null
        var unregisterCount = 0

        override fun register(sensorType: Int, listener: SensorPort.Listener): Boolean {
            if (!hasSensor) return false
            registeredTypes += sensorType
            this.listener = listener
            return true
        }

        override fun unregister() {
            unregisterCount++
            listener = null
        }

        override fun displayRotation(): Int = Surface.ROTATION_0

        override fun declinationDegrees(latitude: Double, longitude: Double, epochMillis: Long) = declination
    }

    private val port = FakeSensorPort()
    private val clock = FakeMonotonicClock(startMillis = 5_555L)
    private val source = RotationVectorHeadingSource(port, clock, latestPosition = { Readings.readingAt() })

    private fun facing(degrees: Double): FloatArray {
        val theta = -degrees * PI / 180
        return floatArrayOf(0f, 0f, sin(theta / 2).toFloat(), cos(theta / 2).toFloat())
    }

    /** @requirement FR-034 */
    @Test
    fun itRegistersTheRotationVectorSensorAndNoOther() {
        source.start()
        assertEquals(listOf(Sensor.TYPE_ROTATION_VECTOR), port.registeredTypes)
    }

    /** @requirement FR-033, FR-034 */
    @Test
    fun aReliableEventEmitsATrueNorthHeadingWithReceiptTime() {
        port.declination = 12.0
        source.start()
        port.listener!!.onRotationVector(facing(30.0), SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        val heading = assertNotNull(source.heading.value)
        assertEquals(42.0, heading.trueHeadingDegrees, 0.5)
        assertEquals(5_555L, heading.receivedElapsedMillis)
    }

    /** @requirement FR-041 */
    @Test
    fun anUnreliableEventEmitsNullAndTheNextReliableEventEmitsAgain() {
        source.start()
        port.listener!!.onRotationVector(facing(30.0), SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
        assertNotNull(source.heading.value)
        port.listener!!.onRotationVector(facing(31.0), SensorManager.SENSOR_STATUS_UNRELIABLE)
        assertNull(source.heading.value)
        port.listener!!.onRotationVector(facing(32.0), SensorManager.SENSOR_STATUS_ACCURACY_LOW)
        assertEquals(32.0, assertNotNull(source.heading.value).trueHeadingDegrees, 0.5)
    }

    /** @requirement FR-041 */
    @Test
    fun withNoSuchSensorTheHeadingIsNull() {
        port.hasSensor = false
        source.start()
        assertNull(source.heading.value)
        assertTrue(port.registeredTypes.isEmpty())
    }

    /** @requirement FR-034 */
    @Test
    fun itRegistersOnStartAndUnregistersOnStop() {
        assertTrue(port.registeredTypes.isEmpty())
        source.start()
        assertEquals(1, port.registeredTypes.size)
        source.stop()
        assertEquals(1, port.unregisterCount)
        assertNull(port.listener)
    }
}
