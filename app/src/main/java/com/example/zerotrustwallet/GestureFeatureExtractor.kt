package com.example.zerotrustwallet

import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Spatial Gesture Biometric Extractor (Component 2 - Kalani)
 * Captures swipe velocity, drag duration, and trajectory distance.
 * Batches telemetry every 5 swipes for continuous authentication.
 */
class GestureFeatureExtractor {
    private val swipeVelocities = mutableListOf<Float>()
    private val dragDurations = mutableListOf<Long>()
    private val trajectories = mutableListOf<Float>()

    private var startX: Float = 0f
    private var startY: Float = 0f
    private var startTime: Long = 0

    // Callback triggered every 5 gestures for continuous verification
    var onGestureBatchReady: ((Map<String, Any>) -> Unit)? = null
    private var swipeCount = 0
    private val BATCH_SIZE = 5

    fun recordTouchDown(x: Float, y: Float) {
        startX = x
        startY = y
        startTime = System.currentTimeMillis()
    }

    fun recordTouchUp(endX: Float, endY: Float) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        val distanceX = endX - startX
        val distanceY = endY - startY
        val distance = sqrt(distanceX.pow(2) + distanceY.pow(2))

        // REMOVED distance > 30f limit: Now captures BOTH Swipes and Taps
        if (duration > 20) {
            val velocity = if (duration > 0) distance / duration else 0f

            dragDurations.add(duration)
            trajectories.add(distance)
            swipeVelocities.add(velocity)

            if (distance < 10f) {
                Log.d("GestureEngine", "Tap Detected -> Duration: ${duration}ms")
            } else {
                Log.d("GestureEngine", "Swipe Detected -> Velocity: ${velocity}px/ms, Distance: ${distance}px")
            }

            swipeCount++
            if (swipeCount >= BATCH_SIZE) {
                val batchData = exportGestureFeatures()
                onGestureBatchReady?.invoke(batchData)
                swipeCount = 0
            }
        }
    }

    fun exportGestureFeatures(): Map<String, Any> {
        return mapOf(
            "velocities" to swipeVelocities.toList(),
            "durations" to dragDurations.toList(),
            "trajectories" to trajectories.toList(),
            "sequenceLength" to swipeVelocities.size
        )
    }

    fun wipeSession() {
        swipeVelocities.clear()
        dragDurations.clear()
        trajectories.clear()
        swipeCount = 0
        Log.d("GestureEngine", "Secure Gesture Session Wiped.")
    }
}