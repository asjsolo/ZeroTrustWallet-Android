package com.example.zerotrustwallet.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
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
    val gestureBaseline: List<Map<String, Any>>,
    val imuBaseline: Map<String, Any>
)

// --- 2. API INTERFACE ---
interface ZeroTrustApi {
    @POST("api/auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): AuthResponse

    // ADD THIS NEW LINE:
    @POST("api/auth/login")
    suspend fun loginUser(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/enroll")
    suspend fun enrollBiometrics(@Body request: EnrollmentRequest): AuthResponse
}
// --- 3. RETROFIT CLIENT BUILDER ---
object ApiClient {
    // Your new global internet address!
    private const val BASE_URL = " https://wrench-silicon-slider.ngrok-free.dev /"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofitService: ZeroTrustApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZeroTrustApi::class.java)
    }
}