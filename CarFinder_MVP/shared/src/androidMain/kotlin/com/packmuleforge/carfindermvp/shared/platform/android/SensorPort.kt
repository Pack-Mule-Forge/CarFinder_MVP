package com.packmuleforge.carfindermvp.shared.platform.android

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface

/** A thin seam over the sensor framework so host tests drive [RotationVectorHeadingSource] with a test double. */
interface SensorPort {
    interface Listener {
        fun onRotationVector(values: FloatArray, accuracy: Int)
        fun onAccuracyChanged(accuracy: Int) = Unit
    }

    /** Registers [listener] for [sensorType]; `false` when the device has no such sensor. */
    fun register(sensorType: Int, listener: Listener): Boolean
    fun unregister()

    /** The current display rotation, one of the `Surface.ROTATION_*` constants. */
    fun displayRotation(): Int

    /** Magnetic declination east of true north at this place and time. */
    fun declinationDegrees(latitude: Double, longitude: Double, epochMillis: Long): Double
}

internal class AndroidSensorPort(context: Context) : SensorPort {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var registered: SensorEventListener? = null

    override fun register(sensorType: Int, listener: SensorPort.Listener): Boolean {
        val sensor = sensorManager?.getDefaultSensor(sensorType) ?: return false
        val platformListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = listener.onRotationVector(event.values.clone(), event.accuracy)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = listener.onAccuracyChanged(accuracy)
        }
        registered = platformListener
        return sensorManager.registerListener(platformListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun unregister() {
        registered?.let { sensorManager?.unregisterListener(it) }
        registered = null
    }

    override fun displayRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    override fun declinationDegrees(latitude: Double, longitude: Double, epochMillis: Long): Double =
        GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, epochMillis).declination.toDouble()
}
