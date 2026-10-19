package com.example.zerotrustwallet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    // Required to receive the ID from MainActivity upon login
    var activeUserId: String? = null

    var liveSessionScore: Float = 100f
        private set

    private val lockdownThreshold = 75f

    init {
        keystrokeExtractor.onBatchReady = { batchData ->
            processLiveKeystrokeBatch(batchData)
        }
    }

    private fun processLiveKeystrokeBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId

        // FIX: Silently ignore typing if the user isn't logged in yet (like on the login/register screens)
        // Using isNullOrEmpty() catches both nulls and blank strings without throwing red errors.
        if (currentUserId.isNullOrEmpty()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ZkFusionEngine", "Intercepted 15 keys. Sending batch to Node.js ZK Engine...")

                // Make the actual network call to your backend
                val request = com.example.zerotrustwallet.network.VerifyKeystrokeRequest(
                    userId = currentUserId,
                    batchData = batchData
                )

                val response = com.example.zerotrustwallet.network.ApiClient.retrofitService.verifyKeystrokes(request)

                // Update the live score with the real calculation from your statistical engine
                liveSessionScore = response.score

                Log.d("ZkFusionEngine", "Live Trust Score updated to: $liveSessionScore%")

                if (liveSessionScore < lockdownThreshold) {
                    Log.e("ZkFusionEngine", "CRITICAL ANOMALY: Score dropped below $lockdownThreshold%.")
                }
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "Batch evaluation failed: ${e.message}")
            }
        }
    }

    fun evaluateTrustMatrix(): TrustMatrix {
        val keystrokeFeatures = keystrokeExtractor.exportLSTMFeatures()
        val gestureFeatures = gestureExtractor.exportCNNFeatures()
        val imuFeatures = imuExtractor.exportMotionFeatures()

        val keySequenceLength = keystrokeFeatures["sequenceLength"] as? Int ?: 0
        val keystrokeScore = if (keySequenceLength > 2) 92f else 75f

        val gestureScore = if (gestureFeatures.isNotEmpty()) 88f else 70f

        val imuDataPoints = imuFeatures["totalDataPoints"] as? Int ?: 0
        val imuScore = if (imuDataPoints > 10) 95f else 80f

        val finalScore = (keystrokeScore + gestureScore + imuScore) / 3f
        val isAuthorized = finalScore >= 80f

        Log.d("ZkFusionEngine", "Fused Score: $finalScore | Authorized: $isAuthorized")

        return TrustMatrix(keystrokeScore, gestureScore, imuScore, finalScore, isAuthorized)
    }

    /**
     * Simulates the generation of a Zero-Knowledge Proof for the backend Postgres ledger.
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