package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.Sensor
import android.hardware.SensorManager
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource
import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Heading from the gyroscope-smoothed rotation-vector sensor, remapped for the display rotation and corrected to
 * true north with the declination at the latest fix. `null` while the sensor is unreliable or absent. Staleness is
 * judged by the presenter from [HeadingReading.receivedElapsedMillis].
 *
 * @requirement FR-033, FR-034, FR-041
 */
class RotationVectorHeadingSource(
    private val port: SensorPort,
    private val clock: MonotonicClock,
    private val latestPosition: () -> LocationReading?,
    private val wallClock: WallClock = AndroidWallClock(),
) : HeadingSource {

    private val state = MutableStateFlow<HeadingReading?>(null)
    override val heading: StateFlow<HeadingReading?> = state.asStateFlow()

    private val listener = object : SensorPort.Listener {
        override fun onRotationVector(values: FloatArray, accuracy: Int) {
            state.value = if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) null else toHeading(values)
        }

        override fun onAccuracyChanged(accuracy: Int) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) state.value = null
        }
    }

    override fun start() {
        if (!port.register(Sensor.TYPE_ROTATION_VECTOR, listener)) state.value = null
    }

    override fun stop() {
        port.unregister()
        state.value = null
    }

    private fun toHeading(values: FloatArray): HeadingReading {
        val magnetic = HeadingMath.magneticAzimuthDegrees(values, port.displayRotation())
        // Until a fix is known the declination is unknown; the magnetic heading is used meanwhile.
        val declination = latestPosition()?.let {
            port.declinationDegrees(it.latitude, it.longitude, wallClock.epochMillis())
        } ?: 0.0
        return HeadingReading(HeadingMath.trueHeadingDegrees(magnetic, declination), clock.elapsedRealtimeMillis())
    }
}
