package com.example.zerotrustwallet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class IMUSensorManager(context: Context) : SensorEventListener {

    // Hook into Android's native hardware sensors
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // A reactive data stream that our Fusion Engine can continuously read
    private val _imuScore = MutableStateFlow(100f)
    val imuScore: StateFlow<Float> = _imuScore

    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    // This triggers dozens of times per second whenever the phone moves
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Calculate the total acceleration vector (Standard gravity is ~9.8 m/s^2)
                val totalAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // Calculate deviation from standard resting/holding state
                val deviation = abs(totalAcceleration - 9.8f)

                // Penalize the score based on how erratic the movement is
                var currentScore = 100f - (deviation * 12f) // Multiplier controls strictness

                // Clamp the score between 0 and 100
                if (currentScore > 100f) currentScore = 100f
                if (currentScore < 0f) currentScore = 0f

                // Push the new score to the live stream
                _imuScore.value = currentScore
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required for this prototype implementation
    }
}