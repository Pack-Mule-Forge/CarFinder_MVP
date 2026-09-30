package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource
import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Compass heading from the rotation-vector sensor (falling back to the geomagnetic rotation vector), corrected to
 * true north and remapped to the display rotation. Emits null when the heading is unavailable: no sensor,
 * unreliable accuracy, or no event within the heading-staleness timeout (FR-031, research R3).
 *
 * @requirement FR-021, FR-031
 */
class RotationVectorHeadingSource(
    private val port: SensorPort,
    private val displayRotation: () -> Int,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
) : HeadingSource {

    private val _headings = MutableStateFlow<HeadingReading?>(null)
    override val headings: StateFlow<HeadingReading?> = _headings.asStateFlow()

    @Volatile
    private var declinationDegrees = 0f

    @Volatile
    private var isReliable = true
    private var watchdog: Job? = null

    override fun start() {
        val type = listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            .firstOrNull(port::hasSensor)
        if (type == null) {
            _headings.value = null
            return
        }
        isReliable = true
        port.register(type, ::onValues, ::onAccuracy)
    }

    override fun stop() {
        port.unregister()
        watchdog?.cancel()
        _headings.value = null
    }

    override fun updateDeclinationFrom(fix: LocationReading) {
        declinationDegrees = GeomagneticField(
            fix.latitude.toFloat(),
            fix.longitude.toFloat(),
            0f,
            fix.epochMillis,
        ).declination
    }

    private fun onValues(rotationVector: FloatArray) {
        if (!isReliable) return
        val heading = HeadingMath.azimuth(rotationVector, displayRotation(), declinationDegrees)
        _headings.value = HeadingReading(heading, clock.elapsedRealtimeMillis())
        armWatchdog()
    }

    private fun onAccuracy(accuracy: Int) {
        isReliable = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        if (!isReliable) _headings.value = null
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS)
            _headings.value = null
        }
    }
}
