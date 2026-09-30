package com.packmuleforge.carfindermvp.shared.platform.android

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Thin seam over SensorManager so the heading source can be tested without hardware (Constitution III). */
interface SensorPort {
    fun hasSensor(type: Int): Boolean

    fun register(type: Int, onValues: (FloatArray) -> Unit, onAccuracy: (Int) -> Unit)

    fun unregister()
}

internal class AndroidSensorPort(private val sensorManager: SensorManager) : SensorPort {
    private var listener: SensorEventListener? = null

    override fun hasSensor(type: Int) = sensorManager.getDefaultSensor(type) != null

    override fun register(type: Int, onValues: (FloatArray) -> Unit, onAccuracy: (Int) -> Unit) {
        unregister()
        val sensor = sensorManager.getDefaultSensor(type) ?: return
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onValues(event.values.clone())
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = onAccuracy(accuracy)
        }
        listener = newListener
        sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun unregister() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}
