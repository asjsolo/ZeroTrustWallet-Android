package com.example.zerotrustwallet

import android.util.Log
import com.example.zerotrustwallet.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
//This data class packages the three independent biometric scores,
//the fused average, and a boolean isAuthorized flag.
//It serves as a static snapshot to populate the UI matrix when the user attempts a financial transfer.
data class TrustMatrix(
    val keystrokeScore: Float,
    val gestureScore: Float,
    val imuScore: Float,
    val finalScore: Float,
    val isAuthorized: Boolean //pass fail and 80%threshold
)

class ZkFusionEngine(
    private val keystrokeExtractor: KeystrokeFeatureExtractor,
    private val gestureExtractor: GestureFeatureExtractor,
    private val imuExtractor: IMUFeatureExtractor
) {
    var activeUserId: String? = null //A nullable string that stores the database ID of the currently authenticated user.

    // State Machine holding the live backend scores continuously and the private set is to ensure only zk engine itself
    //can upfate them preventing unauthorized tampering
    var liveKeystrokeScore: Float = 100f
        private set
    var liveGestureScore: Float = 100f// 100%trust score at login the engine assumes 100 percent reduces only when the
        private set                    // backend send the score mathematially
    var liveImuScore: Float = 100f
        private set

    private val lockdownThreshold = 80f // Fused threshold for continuous authentication

    // Global callback to instantly log the user out if scores drop
    var onLockdownTriggered: (() -> Unit)? = null
    //The onLockdownTriggered variable is a functional UI callback.
    // It allows the background engine to instantly command the MainActivity to destroy the session and return to the login screen.


    //The init block acts as the hardware event listener.
    // It wires the engine directly to the three feature extractors.
    // Whenever an extractor hits its data threshold (e.g., 20 IMU readings),
    // it fires its onBatchReady lambda, immediately routing the raw telemetry into the engine.
    init {
        keystrokeExtractor.onBatchReady = { batchData -> processLiveKeystrokeBatch(batchData) }
        gestureExtractor.onGestureBatchReady = { batchData -> processLiveGestureBatch(batchData) }
        imuExtractor.onMotionBatchReady = { batchData -> processLiveIMUBatch(batchData) }
    }

    //Transmission of raw keystoke data to the backend and update trust state
    private fun processLiveKeystrokeBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId ?: return//ensure user is actively logged in
        CoroutineScope(Dispatchers.IO).launch {//force network request onto a background i/o thread
                                                        //enusring android ui never sttuers or freezes while waiting for server
            try {
                //
                val request = com.example.zerotrustwallet.network.VerifyKeystrokeRequest(currentUserId, batchData)
                //
                val response = ApiClient.retrofitService.verifyKeystrokes(request)
                liveKeystrokeScore = response.score
                Log.d("ZkFusionEngine", "Continuous Keystroke Score: $liveKeystrokeScore%")
                evaluateContinuousState()
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "Keystroke API Error: ${e.message}")
            }
        }
    }

    private fun processLiveGestureBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = mapOf("userId" to currentUserId, "gestureBatch" to batchData)
                val response = ApiClient.retrofitService.verifyGestureBatch(payload)
                if (response.isSuccessful) {
                    val score = (response.body()?.get("score") as? Number)?.toFloat() ?: 0f
                    liveGestureScore = score
                    Log.d("ZkFusionEngine", "Continuous Gesture Score: $liveGestureScore%")
                    evaluateContinuousState()
                }
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "Gesture API Error: ${e.message}")
            }
        }
    }

    private fun processLiveIMUBatch(batchData: Map<String, Any>) {
        val currentUserId = activeUserId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = mapOf("userId" to currentUserId, "imuBatch" to batchData)
                val response = ApiClient.retrofitService.verifyImuBatch(payload)
                if (response.isSuccessful) {
                    val score = (response.body()?.get("score") as? Number)?.toFloat() ?: 0f
                    liveImuScore = score
                    Log.d("ZkFusionEngine", "Continuous IMU Score: $liveImuScore%")
                    evaluateContinuousState()
                }
            } catch (e: Exception) {
                Log.e("ZkFusionEngine", "IMU API Error: ${e.message}")
            }
        }
    }

    // This runs every single time ANY sensor receives a new score from the backend
    private fun evaluateContinuousState() {
        // gets the most recent pecentage form the engones  and calucalte the mathematical average
        val fusedAverage = (liveKeystrokeScore + liveGestureScore + liveImuScore) / 3f
        Log.d("ZkFusionEngine", "Live Fused Average: $fusedAverage%")

        if (fusedAverage < lockdownThreshold) {
            Log.e("ZkFusionEngine", "ANOMALY DETECTED. Fused Score dropped to $fusedAverage%. Triggering Lockdown.")
            CoroutineScope(Dispatchers.Main).launch {//if dropped  below 80 switch to the main ui
                onLockdownTriggered?.invoke()
            }
        }
    }

    // Evaluates the current state for the Transaction UI when the user attempts a transfer
    fun evaluateTrustMatrix(): TrustMatrix {
        val finalScore = (liveKeystrokeScore + liveGestureScore + liveImuScore) / 3f
        val isAuthorized = finalScore >= lockdownThreshold
        return TrustMatrix(liveKeystrokeScore, liveGestureScore, liveImuScore, finalScore, isAuthorized)
    }

    // Creates the Zero-Knowledge cryptographic token only if the matrix is authorized
    fun generateCryptographicProof(transactionRef: String): String {
        val matrix = evaluateTrustMatrix()//pressing the transfer button will get the current trust score
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
        liveKeystrokeScore = 100f
        liveGestureScore = 100f
        liveImuScore = 100f
        activeUserId = null
    }
}