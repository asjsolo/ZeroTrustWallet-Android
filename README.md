***

### Frontend Repository (`README.md`)

```markdown
# ZeroTrustWallet - Android Edge Gatekeeper (Frontend)

## 📖 Description
The ZeroTrustWallet Android app is a localized Edge Gatekeeper designed to provide continuous, invisible security for digital financial transactions. Built natively in Kotlin, the application passively monitors hardware telemetry—including typing cadence, swipe patterns, and device hand-tremors—and routes this data to the backend for real-time scoring. The app houses the Zero-Knowledge (ZK) Fusion Engine, a continuous state machine that aggregates trust scores and instantly triggers a localized session wipe if an anomaly is detected. When transferring funds, the app generates a cryptographically salted token to prove the user's identity without attaching raw biometric arrays to the financial payload.

## ⚙️ Core Components
* **Hardware Extractors:** Background listeners capturing raw telemetry in buffered batches to prevent battery drain.
* **Continuous ZK Fusion Engine:** An asynchronous state machine that maintains the live trust matrix and executes automated lockdowns if the fused average drops below 80%.
* **Salted Hashing Generator:** Utilizes Kotlin's native 32-bit `.hashCode()` combined with a millisecond temporal salt to generate unique, unforgeable transaction proofs.

## 🚀 How to Run

**Prerequisites:**
* Android Studio (Latest Stable Release)
* An Android physical device running API 24 or higher (Recommended over emulators for accurate IMU and touch sensor data).

**Setup Steps:**
1. Clone the repository:
   ```bash
   git clone [https://github.com/asjsolo/ZeroTrustWallet-Android.git](https://github.com/asjsolo/ZeroTrustWallet-Android.git)

2.Open the project in Android Studio and allow Gradle to sync the dependencies.

3.Open ApiClient.kt (or your designated network config file) and replace the BASE_URL with your local IP address or hosted backend URL:
const val BASE_URL = "http://<YOUR_BACKEND_IP>:3000/"

4.Connect your physical Android device via USB debugging (or use SCRCPY) and click Run (Shift + F10) in Android Studio.

Further Improvements
Trusted Execution Environment (TEE): Move the Salted Hash generation logic directly into the Android Keystore system or a local Zero-Knowledge processing enclave to completely isolate the cryptographic signing from the main application thread.

Offline Biometric Fallback: Implement a localized, lightweight version of the statistical models to allow the fusion engine to maintain baseline security even when the device loses cellular or Wi-Fi connection.

Background Service Integration: Migrate the sensor batching logic into a persistent Android WorkManager or Foreground Service, ensuring the biometric state machine continues evaluating even when the wallet is minimized.

👥 Contributors
Asjath Ahmed - Systems Architect & ZK Fusion Engine

Mohamed Sakith - Keystroke Extractor Module

Kalani Rathnayaka - Gesture Extractor Module

Oshani Rangika - IMU Extractor Module
