package com.example.zerotrustwallet

class KeystrokeFeatureExtractor {
    private var lastInputTime: Long = 0L
    private var previousTextLength: Int = 0

    // Arrays to hold the temporal features for the LSTM
    private val flightTimes = mutableListOf<Double>()
    private val errorCorrectionSignature = mutableListOf<Int>() // 1 for backspace, 0 for normal typing

    fun recordTextChange(currentText: String) {
        val currentTime = System.currentTimeMillis()
        val currentLength = currentText.length

        // Track the dynamic error correction signature
        if (currentLength < previousTextLength) {
            // A backspace/deletion occurred
            errorCorrectionSignature.add(1)
        } else if (currentLength > previousTextLength) {
            // Normal character added
            errorCorrectionSignature.add(0)

            // Calculate temporal flight time between key additions
            if (lastInputTime != 0L) {
                val flightTime = (currentTime - lastInputTime).toDouble()
                flightTimes.add(flightTime)
            }
        }

        previousTextLength = currentLength
        lastInputTime = currentTime
    }

    // Packages the extracted features to be fed into the LSTM
    fun exportLSTMFeatures(): Map<String, Any> {
        return mapOf(
            "temporalSequence" to flightTimes.toList(),
            "errorSignature" to errorCorrectionSignature.toList(),
            "sequenceLength" to flightTimes.size
        )
    }

    fun wipeSession() {
        flightTimes.clear()
        errorCorrectionSignature.clear()
        lastInputTime = 0L
        previousTextLength = 0
    }
}
