package com.example.aibudgettracker.network.banking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.aibudgettracker.network.BASE_URL
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Query
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Interface for Bank-related API calls (TrueLayer integration).
 */
interface BankingApi {
    
    // Path matches the server's @app.post("/bank/exchange")
    // Leading slash removed to work correctly with BASE_URL ending in /
    @POST("bank/exchange")
    suspend fun exchangeToken(
        @Query("code") code: String
    ): TokenExchangeResponse
}

/**
 * Data model for the bank token exchange response.
 */
data class TokenExchangeResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Int? = null,
    val token_type: String? = null,
    val status: String? = null,
    val message: String? = null
)

/**
 * Retrofit client for the Banking API.
 */
object BankingNetworkClient {
    private val client = OkHttpClient()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: BankingApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BankingApi::class.java)
    }

    /**
     * Launches the bank linking flow in the browser.
     * This triggers the server's GET /bank/exchange endpoint.
     */
    /**
     * Launches the bank linking flow in the browser.
     * We pass the userId so the server knows which profile to link the bank to.
     */
    fun launchBankLink(context: Context, userId: String) {
        // Construct URL with the userId as a query parameter
        val cleanBaseUrl = BASE_URL.removeSuffix("/")

        // We add ?userId=... so your Python server can grab it
        val url = "$cleanBaseUrl/bank/exchange?userId=$userId"

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    suspend fun exchangeCode(code: String): Result<TokenExchangeResponse> {
        return try {
            val response = api.exchangeToken(code)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun syncTransactions(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // We call the 'sync' endpoint on your Python server
            val request = Request.Builder()
                .url("$BASE_URL/bank/sync?userId=$userId")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Later, we will parse the transactions here
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Server returned ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

