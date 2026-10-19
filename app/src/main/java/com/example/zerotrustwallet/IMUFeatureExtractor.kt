package com.example.zerotrustwallet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class IMUFeatureExtractor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Data structures to hold raw sensor matrices for the Activity Classifier
    private val accelerationReadings = mutableListOf<FloatArray>()
    private val gyroscopeReadings = mutableListOf<FloatArray>()

    /**
     * Starts monitoring the hardware sensors when the user is active in the app
     */
    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    /**
     * Stops monitoring to save battery when the app goes to the background
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    // Triggered automatically by Android hardware whenever the phone moves
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // Capture X, Y, Z acceleration vectors
                    accelerationReadings.add(floatArrayOf(it.values[0], it.values[1], it.values[2]))
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // Capture X, Y, Z rotational vectors
                    gyroscopeReadings.add(floatArrayOf(it.values[0], it.values[1], it.values[2]))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required for basic contextual feature extraction
    }

    /**
     * Exports the denoised spatial matrices to feed the Activity Recognition ML model
     */
    fun exportMotionFeatures(): Map<String, Any> {
        return mapOf(
            "accelerometerVectors" to accelerationReadings.toList(),
            "gyroscopeVectors" to gyroscopeReadings.toList(),
            "totalDataPoints" to accelerationReadings.size + gyroscopeReadings.size
        )
    }

    fun wipeSession() {
        accelerationReadings.clear()
        gyroscopeReadings.clear()
    }
}