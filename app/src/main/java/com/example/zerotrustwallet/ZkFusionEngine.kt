package com.example.zerotrustwallet

import android.util.Log

// Data class to feed the UI matrix
data class TrustMatrix(
    val keystrokeScore: Float,
    val gestureScore: Float,
    val imuScore: Float,
    val finalScore: Float,
    val isAuthorized: Boolean
)

class ZkFusionEngine(
    private val keystrokeExtractor: KeystrokeFeatureExtractor,
    private val gestureExtractor: GestureFeatureExtractor,
    private val imuExtractor: IMUFeatureExtractor
) {
    /**
     * Fuses the telemetry from all three microservices to calculate a unified Zero-Trust score.
     */
    fun evaluateTrustMatrix(): TrustMatrix {
        // 1. Pull feature matrices from the team's extractors
        val keystrokeFeatures = keystrokeExtractor.exportLSTMFeatures()
        val gestureFeatures = gestureExtractor.exportCNNFeatures()
        val imuFeatures = imuExtractor.exportMotionFeatures()

        // 2. Evaluate Keystroke Dynamics (Sakith's Engine)
        val keySequenceLength = keystrokeFeatures["sequenceLength"] as? Int ?: 0
        val keystrokeScore = if (keySequenceLength > 2) 92f else 75f

        // 3. Evaluate Spatial Gestures (Kalani's Engine)
        val gestureScore = if (gestureFeatures.isNotEmpty()) 88f else 70f

        // 4. Evaluate IMU Motion (Oshani's Engine)
        val imuDataPoints = imuFeatures["totalDataPoints"] as? Int ?: 0
        val imuScore = if (imuDataPoints > 10) 95f else 80f

        // 5. Final Fusion Calculation
        val finalScore = (keystrokeScore + gestureScore + imuScore) / 3f
        val isAuthorized = finalScore >= 80f

        Log.d("ZkFusionEngine", "Fused Score: $finalScore | Authorized: $isAuthorized")

        return TrustMatrix(keystrokeScore, gestureScore, imuScore, finalScore, isAuthorized)
    }

    /**
     * Simulates the generation of a Zero-Knowledge Proof for the backend postgres ledger.
     */
    fun generateCryptographicProof(transactionRef: String): String {
        val matrix = evaluateTrustMatrix()
        return if (matrix.isAuthorized) {
            val hash = (transactionRef + System.currentTimeMillis() + matrix.finalScore).hashCode()
            "zk_proof_valid_${Integer.toHexString(hash)}"
        } else {
            "zk_proof_rejected_anomaly_detected"
        }
    }

    fun wipeSecureSession() {
        keystrokeExtractor.wipeSession()
        gestureExtractor.wipeSession()
        imuExtractor.wipeSession()
    }
}