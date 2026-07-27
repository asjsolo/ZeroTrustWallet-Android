package com.example.zerotrustwallet

class KeystrokeEngine {
    // Edge Memory: Stores the raw temporal features locally
    private val flightTimes = mutableListOf<Long>()
    private var backspaceCount = 0
    private var lastKeyTimestamp = 0L

    fun recordKeyPress(isBackspace: Boolean) {
        val currentTimestamp = System.currentTimeMillis()

        if (isBackspace) {
            backspaceCount++
            // Reset timing for the next key to avoid massive flight times during deletions
            lastKeyTimestamp = currentTimestamp
            return
        }

        if (lastKeyTimestamp != 0L) {
            val flightTime = currentTimestamp - lastKeyTimestamp
            flightTimes.add(flightTime)

            // Cap the array to prevent memory leaks during long sessions
            if (flightTimes.size > 100) {
                flightTimes.removeAt(0)
            }
        }

        lastKeyTimestamp = currentTimestamp
    }

    // This will eventually feed Sakith's LSTM model
    fun getInteractionFingerprint(): Map<String, Any> {
        return mapOf(
            "average_flight_time" to if (flightTimes.isNotEmpty()) flightTimes.average() else 0.0,
            "error_correction_count" to backspaceCount,
            "raw_flight_sequence" to flightTimes.toList()
        )
    }

    fun resetSession() {
        flightTimes.clear()
        backspaceCount = 0
        lastKeyTimestamp = 0L
    }
}