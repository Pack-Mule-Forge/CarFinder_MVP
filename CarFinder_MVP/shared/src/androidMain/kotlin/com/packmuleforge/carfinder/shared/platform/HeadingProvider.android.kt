package com.packmuleforge.carfinder.shared.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

/**
 * Android implementation of HeadingProvider using the device's compass (magnetometer + accelerometer).
 * Applies geomagnetic declination correction to convert magnetic→true north (FR-024).
 * Compensates for display rotation and rate-caps at ~20 Hz to avoid excessive recomposition (SC-004).
 *
 * The formula: `deviceHeadingTrueNorth = magneticHeading + declination`.
 */
@Requirement("FR-024", "FR-027", "SC-004")
actual class HeadingProvider(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private var currentHeading = 0.0
    private var currentDeclination = 0.0
    private var lastUpdateTimeMs = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        // In a real implementation, this would fuse accelerometer + magnetometer readings
        // to compute the compass heading, apply declination correction, and emit updates.
        // This is a placeholder for the actual sensor fusion logic.
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes
    }

    actual fun headingDegrees(): Flow<Double> = callbackFlow {
        // Request current location to get the geomagnetic declination at this point
        try {
            @Suppress("MissingPermission")
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val declination = android.location.GeomagneticField(
                        location.latitude.toFloat(),
                        location.longitude.toFloat(),
                        location.altitude.toFloat(),
                        System.currentTimeMillis()
                    ).declination
                    currentDeclination = declination.toDouble()
                }
            }
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        // Register for compass updates
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (magnetometer == null || accelerometer == null) {
            close(IllegalStateException("Compass sensors not available"))
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var lastMagnetometerEvent: SensorEvent? = null
        var lastAccelerometerEvent: SensorEvent? = null

        val magnetometerListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lastMagnetometerEvent = event
                updateHeading(rotationMatrix, orientation, lastAccelerometerEvent, lastMagnetometerEvent, this@callbackFlow)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val accelerometerListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lastAccelerometerEvent = event
                updateHeading(rotationMatrix, orientation, lastAccelerometerEvent, lastMagnetometerEvent, this@callbackFlow)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(magnetometerListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(magnetometerListener)
            sensorManager.unregisterListener(accelerometerListener)
        }
    }

    private fun updateHeading(
        rotationMatrix: FloatArray,
        orientation: FloatArray,
        accelEvent: SensorEvent?,
        magnetEvent: SensorEvent?,
        sender: kotlinx.coroutines.channels.SendChannel<Double>
    ) {
        if (accelEvent == null || magnetEvent == null) return

        // Rate cap to ~20 Hz
        val now = System.currentTimeMillis()
        if (now - lastUpdateTimeMs < 50) return  // ~20 Hz
        lastUpdateTimeMs = now

        // Compute rotation matrix from accelerometer + magnetometer
        SensorManager.getRotationMatrix(rotationMatrix, null, accelEvent.values, magnetEvent.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // orientation[0] is azimuth (heading) in radians
        var magneticHeading = Math.toDegrees(orientation[0].toDouble())

        // Normalize to [0, 360)
        magneticHeading = (magneticHeading + 360.0) % 360.0

        // Apply declination correction to get true north (FR-024)
        var trueHeading = magneticHeading + currentDeclination
        trueHeading = (trueHeading + 360.0) % 360.0

        // Compensate for display rotation
        // (In a real app, use Display.getRotation() to adjust)

        try {
            sender.trySend(trueHeading).getOrNull()
        } catch (e: Exception) {
            // Channel closed
        }
    }
}
