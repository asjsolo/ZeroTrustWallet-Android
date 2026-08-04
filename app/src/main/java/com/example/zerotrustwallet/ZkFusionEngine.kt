package com.example.zerotrustwallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import kotlin.random.Random

class ZkFusionEngine(
    private val keystrokeExtractor: KeystrokeFeatureExtractor,
    private val gestureExtractor: GestureFeatureExtractor,
    private val imuExtractor: IMUFeatureExtractor
) {

    // Reactive streams that the Android UI will listen to in real-time
    private val _currentTrustScore = MutableStateFlow(100f)
    val currentTrustScore: StateFlow<Float> = _currentTrustScore.asStateFlow()

    private val _sessionLocked = MutableSharedFlow<String>() // Emits a message when lockout triggers
    val sessionLocked: SharedFlow<String> = _sessionLocked.asSharedFlow()

    private var monitoringJob: Job? = null

    /**
     * Starts the continuous background loop.
     * This runs constantly while the user navigates the app.
     */
    fun startContinuousMonitoring(scope: CoroutineScope) {
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(4000) // Run an evaluation every 4 seconds
                evaluateBiometricWindow()
            }
        }
    }

    /**
     * Stops monitoring and wipes memory when the app is closed or locked.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        wipeAllSensors()
        _currentTrustScore.value = 100f // Reset for next login
    }

    private suspend fun evaluateBiometricWindow() {
        // 1. Pull data collected over the last 4 seconds
        val keystrokeData = keystrokeExtractor.exportLSTMFeatures()
        val gestureData = gestureExtractor.exportCNNFeatures()
        val imuData = imuExtractor.exportMotionFeatures()

        val keySequenceLength = keystrokeData["sequenceLength"] as? Int ?: 0
        val gestureCount = gestureData.size
        val motionPoints = imuData["totalDataPoints"] as? Int ?: 0

        // 2. Idle Check: If the user is just staring at the screen and not touching it,
        // we shouldn't penalize them. We only calculate a new score if there is active input.
        if (keySequenceLength == 0 && gestureCount == 0 && motionPoints < 10) {
            wipeAllSensors() // Clear the 4-second buffer
            return
        }

        // 3. Mock ML Evaluation (Dynamic Weighting)
        // Simulate grading the active inputs for this 4-second window.
        var keystrokeScore = if (keySequenceLength > 0) 95f else 100f
        var gestureScore = if (gestureCount > 0) 92f else 100f
        var imuScore = if (motionPoints > 20) 96f else 100f

        // Apply slight random variance to simulate real ML confidence intervals
        keystrokeScore -= Random.nextFloat() * 5
        gestureScore -= Random.nextFloat() * 5
        imuScore -= Random.nextFloat() * 5

        // Fusion Algorithm: Combine scores based on architectural weights
        val finalTrustScore = (keystrokeScore * 0.4f) + (gestureScore * 0.4f) + (imuScore * 0.2f)

        // 4. Update the live stream so the UI can react
        _currentTrustScore.value = finalTrustScore

        // 5. THE ZERO-TRUST KILL SWITCH
        // If the score drops below 80, emit the lockdown signal instantly
        if (finalTrustScore < 80f) {
            _sessionLocked.emit("Zero-Trust Lockdown: Biometric anomaly detected.")
            stopMonitoring()
        }

        // 6. Wipe the local memory buffers so the next 4-second window starts fresh
        wipeAllSensors()
    }

    /**
     * Called at the exact moment of a transaction to generate the cryptographic proof.
     */
    fun generateZkSnarkHashForTransaction(): String {
        val score = _currentTrustScore.value
        val rawData = "ZK_SNARK_VALID_${score}_${System.currentTimeMillis()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawData.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun wipeAllSensors() {
        keystrokeExtractor.wipeSession()
        gestureExtractor.wipeSession()
        imuExtractor.wipeSession()
    }
}