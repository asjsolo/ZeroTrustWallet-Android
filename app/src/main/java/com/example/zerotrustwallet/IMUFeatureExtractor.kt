package com.example.zerotrustwallet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class IMUFeatureExtractor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelerationReadings = mutableListOf<FloatArray>()
    private val gyroscopeReadings = mutableListOf<FloatArray>()

    // Continuous Verification Callback
    var onMotionBatchReady: ((Map<String, Any>) -> Unit)? = null
    private var batchCounter = 0
    private val BATCH_THRESHOLD = 50 // Trigger evaluation every 50 sensor ticks

    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        Log.d("IMUEngine", "Hardware Sensors Active. Monitoring physical tremors...")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        Log.d("IMUEngine", "Hardware Sensors Offline.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerationReadings.add(floatArrayOf(it.values[0], it.values[1], it.values[2]))
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeReadings.add(floatArrayOf(it.values[0], it.values[1], it.values[2]))
                }
            }

            // Trigger continuous background evaluation
            batchCounter++
            if (batchCounter >= BATCH_THRESHOLD) {
                onMotionBatchReady?.invoke(exportMotionFeatures())
                batchCounter = 0

                // Prevent memory leaks by dropping old telemetry
                if (accelerationReadings.size > 200) {
                    accelerationReadings.subList(0, 100).clear()
                    gyroscopeReadings.subList(0, 100).clear()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
        batchCounter = 0
    }
}