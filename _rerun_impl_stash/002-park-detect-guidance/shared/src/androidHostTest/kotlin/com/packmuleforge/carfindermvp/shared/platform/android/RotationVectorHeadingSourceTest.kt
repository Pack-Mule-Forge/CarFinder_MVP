package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.testing.FakeMonotonicClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSensorPort(private val available: Set<Int>) : SensorPort {
    var registeredType: Int? = null
        private set
    private var onValues: ((FloatArray) -> Unit)? = null
    private var onAccuracy: ((Int) -> Unit)? = null

    override fun hasSensor(type: Int) = type in available

    override fun register(type: Int, onValues: (FloatArray) -> Unit, onAccuracy: (Int) -> Unit) {
        registeredType = type
        this.onValues = onValues
        this.onAccuracy = onAccuracy
    }

    override fun unregister() {
        registeredType = null
        onValues = null
        onAccuracy = null
    }

    fun send(values: FloatArray) = onValues?.invoke(values)

    fun accuracy(value: Int) = onAccuracy?.invoke(value)
}

/**
 * Compass availability rules for FR-031, through a fake sensor port.
 * @requirement QR-004
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RotationVectorHeadingSourceTest {

    private val clock = FakeMonotonicClock(now = 5_000_000L)

    private fun flatFacing(azimuthDegrees: Double): FloatArray {
        val half = -azimuthDegrees * PI / 180 / 2
        return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    private fun TestScope.source(port: FakeSensorPort) = RotationVectorHeadingSource(
        port = port,
        displayRotation = { Surface.ROTATION_0 },
        clock = clock,
        scope = backgroundScope + UnconfinedTestDispatcher(testScheduler),
    )

    /** @requirement FR-031 */
    @Test
    fun prefersRotationVector_fallsBackToGeomagnetic() = runTest {
        val both = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR))
        source(both).start()
        assertEquals(Sensor.TYPE_ROTATION_VECTOR, both.registeredType)

        val geomagneticOnly = FakeSensorPort(setOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR))
        source(geomagneticOnly).start()
        assertEquals(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, geomagneticOnly.registeredType)
    }

    /** @requirement FR-031 */
    @Test
    fun noHeadingSensor_emitsNull() = runTest {
        val port = FakeSensorPort(emptySet())
        val source = source(port).apply { start() }
        assertNull(port.registeredType)
        assertNull(source.headings.value)
    }

    /** @requirement FR-031 */
    @Test
    fun sensorEvent_emitsHeading_stampedWithMonotonicTime() = runTest {
        val port = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR))
        val source = source(port).apply { start() }
        port.send(flatFacing(90.0))
        val heading = assertNotNull(source.headings.value)
        assertEquals(90.0, heading.trueHeadingDegrees, 0.5)
        assertEquals(clock.now, heading.elapsedRealtimeMillis)
    }

    /** @requirement FR-031 */
    @Test
    fun unreliableAccuracy_emitsNull() = runTest {
        val port = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR))
        val source = source(port).apply { start() }
        port.send(flatFacing(0.0))
        port.accuracy(SensorManager.SENSOR_STATUS_UNRELIABLE)
        assertNull(source.headings.value)
        port.send(flatFacing(0.0))
        assertNull(source.headings.value, "no heading until accuracy recovers")
        port.accuracy(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        port.send(flatFacing(0.0))
        assertNotNull(source.headings.value)
    }

    /** @requirement FR-030, FR-031 */
    @Test
    fun noEventForStalenessTimeout_emitsNull() = runTest {
        val port = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR))
        val source = source(port).apply { start() }
        port.send(flatFacing(0.0))
        advanceTimeBy(CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS - 1)
        assertNotNull(source.headings.value)
        advanceTimeBy(2)
        assertNull(source.headings.value)
    }

    /** @requirement FR-031 */
    @Test
    fun startAndStop_registerAndUnregister() = runTest {
        val port = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR))
        val source = source(port)
        source.start()
        assertTrue(port.registeredType != null)
        source.stop()
        assertFalse(port.registeredType != null)
        assertNull(source.headings.value)
    }

    /** @requirement FR-021 */
    @Test
    fun declinationFromFix_changesTheHeading() = runTest {
        val port = FakeSensorPort(setOf(Sensor.TYPE_ROTATION_VECTOR))
        val source = source(port).apply { start() }
        port.send(flatFacing(0.0))
        val magnetic = assertNotNull(source.headings.value).trueHeadingDegrees

        // Seattle has an easterly declination of roughly 15 degrees.
        source.updateDeclinationFrom(Readings.readingAt(lat = 47.6, lon = -122.3, epochMillis = 1_790_000_000_000L))
        port.send(flatFacing(0.0))
        val trueHeading = assertNotNull(source.headings.value).trueHeadingDegrees
        assertNotEquals(magnetic, trueHeading)
        assertTrue(trueHeading in 8.0..22.0, "true heading was $trueHeading")
    }
}
