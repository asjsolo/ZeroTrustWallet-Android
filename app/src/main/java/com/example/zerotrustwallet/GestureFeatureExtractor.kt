package com.example.zerotrustwallet

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

class GestureFeatureExtractor {

    // Data structures holding spatial feature vectors for the CNN model
    private val spatialVectors = mutableListOf<GestureVector>()

    data class GestureVector(
        val velocityX: Float,
        val velocityY: Float,
        val totalDistance: Float,
        val durationMs: Long
    )

    // Records a completed drag or swipe event from Jetpack Compose pointer events
    fun recordGesture(
        dragAmount: Offset,
        durationMs: Long
    ) {
        if (durationMs <= 0) return

        val distanceX = dragAmount.x
        val distanceY = dragAmount.y

        // Calculate total spatial distance: sqrt(dx^2 + dy^2)
        val totalDistance = sqrt((distanceX * distanceX) + (distanceY * distanceY))

        // Calculate velocity (pixels per millisecond)
        val velocityX = distanceX / durationMs
        val velocityY = distanceY / durationMs

        val vector = GestureVector(
            velocityX = velocityX,
            velocityY = velocityY,
            totalDistance = totalDistance,
            durationMs = durationMs
        )

        spatialVectors.add(vector)
    }

    // Exports spatial feature matrices to feed the CNN model
    fun exportCNNFeatures(): List<Map<String, Any>> {
        return spatialVectors.map { vector ->
            mapOf(
                "vx" to vector.velocityX,
                "vy" to vector.velocityY,
                "distance" to vector.totalDistance,
                "duration" to vector.durationMs
            )
        }
    }

    fun wipeSession() {
        spatialVectors.clear()
    }
}