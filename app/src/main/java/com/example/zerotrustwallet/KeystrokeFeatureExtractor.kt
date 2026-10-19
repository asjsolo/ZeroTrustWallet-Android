package com.example.zerotrustwallet

import android.util.Log

class KeystrokeFeatureExtractor {
    private val flightTimes = mutableListOf<Long>()
    private val dwellTimes = mutableListOf<Long>()
    private val errorSignature = mutableListOf<Int>()

    private var lastKeyUpTime: Long = 0
    private val keyPressMap = mutableMapOf<String, Long>()

    // For the 15-key batch continuous evaluation
    var onBatchReady: ((Map<String, Any>) -> Unit)? = null
    private var currentBatchCount = 0

    fun recordKeyDown(key: String) {
        val currentTime = System.currentTimeMillis()
        keyPressMap[key] = currentTime

        // Calculate FLIGHT TIME (Time from releasing previous key to pressing this one)
        if (lastKeyUpTime > 0) {
            val flightTime = currentTime - lastKeyUpTime
            flightTimes.add(flightTime)

            // --- LIVE TELEMETRY LOG ---
            Log.d("KeystrokeEngine", "[$key] Flight Time: ${flightTime}ms")
        }

        // Calculate ERROR SIGNATURE
        if (key == "DEL") {
            errorSignature.add(1)
            Log.d("KeystrokeEngine", "⚠️ Error Correction Detected (DEL)")
        } else {
            errorSignature.add(0)
        }
    }

    fun recordKeyUp(key: String) {
        val currentTime = System.currentTimeMillis()
        lastKeyUpTime = currentTime

        // Calculate DWELL TIME (Time the finger spent holding down this specific key)
        val keyDownTime = keyPressMap.remove(key)
        if (keyDownTime != null) {
            val dwellTime = currentTime - keyDownTime
            dwellTimes.add(dwellTime)

            // --- LIVE TELEMETRY LOG ---
            Log.d("KeystrokeEngine", "[$key] Dwell Time: ${dwellTime}ms")
        }

        // Trigger continuous evaluation batch
        currentBatchCount++
        if (currentBatchCount >= 15) {
            val batchData = exportLSTMFeatures()
            onBatchReady?.invoke(batchData)
            currentBatchCount = 0 // Reset for the next rolling batch
        }
    }

    fun exportLSTMFeatures(): Map<String, Any> {
        return mapOf(
            "flightTimes" to flightTimes.toList(),
            "dwellTimes" to dwellTimes.toList(),
            "errorSignature" to errorSignature.toList(),
            "sequenceLength" to flightTimes.size
        )
    }

    fun wipeSession() {
        flightTimes.clear()
        dwellTimes.clear()
        errorSignature.clear()
        lastKeyUpTime = 0
        keyPressMap.clear()
        currentBatchCount = 0
        Log.d("KeystrokeEngine", "Secure Session Wiped.")
    }
}