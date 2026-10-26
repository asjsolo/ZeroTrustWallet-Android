package com.example.zerotrustwallet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class IMUFeatureExtractor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelerationReadings = mutableListOf<FloatArray>()
    private val gyroscopeReadings = mutableListOf<FloatArray>()

    private var latestAccel = floatArrayOf(0f, 0f, 0f)
    private var latestGyro = floatArrayOf(0f, 0f, 0f)

    var onMotionBatchReady: ((Map<String, Any>) -> Unit)? = null

    private var lastCaptureTime: Long = 0
    private val CAPTURE_INTERVAL_MS = 250L

    private var batchCounter = 0
    private val BATCH_THRESHOLD = 20

    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                latestAccel = it.values.clone()
            } else if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                latestGyro = it.values.clone()
            }
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCaptureTime > CAPTURE_INTERVAL_MS) {
            lastCaptureTime = currentTime

            // Calculate 3D Rotational Magnitude
            val gyroMag = sqrt(
                (latestGyro[0] * latestGyro[0] +
                        latestGyro[1] * latestGyro[1] +
                        latestGyro[2] * latestGyro[2]).toDouble()
            ).toFloat()

            // DEADZONE FILTER: If rotation is basically zero, phone is resting on a flat surface.
            if (gyroMag < 0.02f) {
                return // Exit early. Do not record data or increment batch.
            }

            // Phone is moving/in-hand. Record the data.
            accelerationReadings.add(latestAccel.clone())
            gyroscopeReadings.add(latestGyro.clone())
            Log.d("IMUEngine", "Motion Captured -> Gyro Mag: $gyroMag")

            batchCounter++
            if (batchCounter >= BATCH_THRESHOLD) {
                val features = exportMotionFeatures()
                Log.d("IMUEngine", "Batch Ready: ${features["totalDataPoints"]} data points")
                onMotionBatchReady?.invoke(features)
                batchCounter = 0

                if (accelerationReadings.size >= BATCH_THRESHOLD) {
                    val halfSizeAccel = accelerationReadings.size / 2
                    accelerationReadings.subList(0, halfSizeAccel).clear()

                    val halfSizeGyro = gyroscopeReadings.size / 2
                    if (halfSizeGyro > 0) {
                        gyroscopeReadings.subList(0, halfSizeGyro).clear()
                    }
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