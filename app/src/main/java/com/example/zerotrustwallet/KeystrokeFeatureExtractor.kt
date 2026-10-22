package com.example.zerotrustwallet

import android.util.Log

class KeystrokeFeatureExtractor {
    private val flightTimes = mutableListOf<Long>()
    private val dwellTimes = mutableListOf<Long>()
    private val errorSignature = mutableListOf<Int>()


    private var lastKeyUpTime: Long = 0
    private val keyPressMap = mutableMapOf<String, Long>()


    // Callback triggered every 15 keystrokes for continuous rolling verification
    var onBatchReady: ((Map<String, Any>) -> Unit)? = null
    private var currentBatchCount = 0


    fun recordKeyDown(key: String) {
        val currentTime = System.currentTimeMillis()
        keyPressMap[key] = currentTime


        // Calculate FLIGHT TIME (Latency between releasing previous key and pressing current key)
        if (lastKeyUpTime > 0) {
            val flightTime = currentTime - lastKeyUpTime
            flightTimes.add(flightTime)
            Log.d("KeystrokeEngine", "[$key] Flight Time: ${flightTime}ms")
        }


        // Capture Backspace Error Correction Signature
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


        // Calculate DWELL TIME (Duration the key remains pressed down)
        val keyDownTime = keyPressMap.remove(key)
        if (keyDownTime != null) {
            val dwellTime = currentTime - keyDownTime
            dwellTimes.add(dwellTime)
            Log.d("KeystrokeEngine", "[$key] Dwell Time: ${dwellTime}ms")
        }


        // Trigger 15-key rolling window evaluation batch
        currentBatchCount++
        if (currentBatchCount >= 15) {
            val batchData = exportKeystrokeFeatures()
            onBatchReady?.invoke(batchData)
            currentBatchCount = 0 // Reset counter for next rolling window
        }
    }


    fun exportKeystrokeFeatures(): Map<String, Any> {
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
        Log.d("KeystrokeEngine", "Secure Keystroke Session Wiped.")
    }
}
