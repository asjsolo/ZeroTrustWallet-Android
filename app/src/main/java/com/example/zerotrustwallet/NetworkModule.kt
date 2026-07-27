package com.example.zerotrustwallet

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// 1. The Data Payload Structure (What we will continuously send to Node.js)
data class BiometricPayload(
    val userId: String,
    val keystrokeScore: Float,
    val gestureScore: Float,
    val imuScore: Float,
    val timestamp: Long
)

// The response we expect back from your fusion engine
data class AuthResponse(
    val isTrusted: Boolean,
    val message: String
)

// 2. The API Interface (Mapping to your Node.js endpoints)
interface ZeroTrustApi {
    @POST("/api/evaluate-trust") // We will wire this specific route up in Node.js later
    suspend fun evaluateBiometrics(@Body payload: BiometricPayload): AuthResponse
}

// 3. The Retrofit Builder
object RetrofitClient {
    // IMPORTANT: Because you are running on a physical phone, "localhost" won't work.
    // You must change this to your laptop's actual IPv4 address on your Wi-Fi network.
    // Example: "http://192.168.1.15:3000"
    private const val BASE_URL = "http://192.168.X.X:3000"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Prints network traffic to Logcat for easy debugging
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: ZeroTrustApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZeroTrustApi::class.java)
    }
}