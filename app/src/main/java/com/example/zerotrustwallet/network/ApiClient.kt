package com.example.zerotrustwallet.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// --- 1. DATA CLASSES (Matching your Node.js backend) ---
data class RegisterRequest(
    val username: String,
    val email: String,
    val pin: String,
    val zk_public_key: String
)

data class LoginRequest(
    val email: String,
    val pin: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val message: String,
    val user: UserData?
)

data class UserData(
    val id: String, // Changed from Int to String to handle UUIDs!
    val username: String,
    val email: String,
    val zk_public_key: String,
    val accountBalance: Double
)

data class EnrollmentRequest(
    val userId: String,
    val keystrokeBaseline: Map<String, Any>,
    val gestureBaseline: Map<String, Any>,
    val imuBaseline: Map<String, Any>
)

// --- 2. API INTERFACE ---
interface ApiService {
    @POST("api/auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun loginUser(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/enroll")
    suspend fun enrollBiometrics(@Body request: EnrollmentRequest): AuthResponse

    @POST("api/auth/verify-keystroke")
    suspend fun verifyKeystrokes(@Body request: VerifyKeystrokeRequest): VerifyKeystrokeResponse

    @POST("api/auth/verify-gesture")
    suspend fun verifyGestureBatch(@Body payload: Map<String, Any>): Response<Map<String, Any>>

    // RESTORE THE MISSING IMU ROUTE HERE:
    @POST("api/auth/verify-imu")
    suspend fun verifyImuBatch(@Body payload: Map<String, Any>): Response<Map<String, Any>>
}
// --- 3. RETROFIT CLIENT BUILDER ---
object ApiClient {
    // Use your laptop's IPv4 address here (e.g., http://192.168.1.100:5000/)
    private const val BASE_URL = "https://wrench-silicon-slider.ngrok-free.dev/"

    // Increase timeouts to 60 seconds for massive biometric payloads
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val retrofitService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

data class VerifyKeystrokeRequest(
    val userId: String,
    val batchData: Map<String, @JvmSuppressWildcards Any>
)

data class VerifyKeystrokeResponse(
    val verified: Boolean,
    val score: Float,
    val timestamp: String
)