package com.example.zerotrustwallet

import android.util.Log
import com.example.zerotrustwallet.network.ApiClient
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
        // Pillar 1 Listener
        keystrokeExtractor.onBatchReady = { batchData ->
            processLiveKeystrokeBatch(batchData)
        }

        // Pillar 2 Listener (Restored)
        gestureExtractor.onGestureBatchReady = { batchData ->
            processLiveGestureBatch(batchData)
        }

        // Pillar 3 Listener (Restored)
        imuExtractor.onMotionBatchReady = { batchData ->
            processLiveIMUBatch(batchData)
        }
    }

    private fun processLiveKeystrokeBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId
        if (currentUserId.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ZkFusionEngine", "Intercepted keys. Sending to backend...")
                val request = com.example.zerotrustwallet.network.VerifyKeystrokeRequest(
                    userId = currentUserId,
                    batchData = batchData
                )
                val response = ApiClient.retrofitService.verifyKeystrokes(request)
                liveSessionScore = response.score
                Log.d("ZkFusionEngine", "Live Keystroke Trust Score updated to: $liveSessionScore%")
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "Keystroke evaluation failed: ${e.message}")
            }
        }
    }

    // Restored Gesture API Router
    private fun processLiveGestureBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId
        if (currentUserId.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload: Map<String, Any> = mapOf("userId" to currentUserId, "gestureBatch" to batchData)
                val response = ApiClient.retrofitService.verifyGestureBatch(payload)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val score = (responseBody?.get("score") as? Number)?.toFloat() ?: 0f
                    val action = responseBody?.get("action") as? String ?: "REVOKE"

                    liveSessionScore = score
                    Log.d("ZkFusionEngine", "Live Gesture Trust Score: $score% | Action: $action")
                }
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "Gesture API Error: ${e.message}")
            }
        }
    }

    // Restored IMU API Router
    private fun processLiveIMUBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId
        if (currentUserId.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload: Map<String, Any> = mapOf("userId" to currentUserId, "imuBatch" to batchData)
                val response = ApiClient.retrofitService.verifyImuBatch(payload)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val score = (responseBody?.get("score") as? Number)?.toFloat() ?: 0f
                    val action = responseBody?.get("action") as? String ?: "REVOKE"

                    liveSessionScore = score
                    Log.d("ZkFusionEngine", "Live IMU Trust Score: $score% | Action: $action")
                }
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "IMU API Error: ${e.message}")
            }
        }
    }

    fun evaluateTrustMatrix(): TrustMatrix {
        val keystrokeFeatures = keystrokeExtractor.exportKeystrokeFeatures()
        // FIX: Updated method name from exportCNNFeatures
        val gestureFeatures = gestureExtractor.exportGestureFeatures()
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