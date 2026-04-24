package com.example.aibudgettracker.network

import android.util.Log
import com.example.aibudgettracker.data.Transaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

// Shared Base URL
const val BASE_URL = "https://aibudgettrackerbackend-180398268036.europe-west2.run.app/"

// 1. API Interface
interface BudgetApi {
    @GET("/")
    suspend fun getStatus(): StatusResponse

    @POST("auth/login/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<AuthResponse>

    @POST("auth/setup-pin")
    suspend fun setupPin(@Body request: PinSetupRequest): Response<MessageResponse>

    @POST("auth/login/pin")
    suspend fun loginWithPin(
        @Header("Authorization") token: String,
        @Body request: PinLoginRequest
    ): Response<PinLoginResponse>

    @POST("analyze/transaction")
    suspend fun analyzeTransaction(@Body request: CategorisationRequest): Response<TransactionResponse>

    @Multipart
    @POST("analyze/upload-csv")
    suspend fun uploadCsv(
        @Part file: MultipartBody.Part,
        @Part("biz_categories") bizCategories: RequestBody,
        @Part("pers_categories") persCategories: RequestBody,
        @Part("business_name") businessName: RequestBody? = null,
        @Part("business_industry") businessIndustry: RequestBody? = null,
        @Part("business_description") businessDescription: RequestBody? = null
    ): Response<CsvUploadResponse>
}

// 2. Data Models
data class TransactionRequest(
    @SerializedName("merchant")
    val merchant: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("date")
    val date: String,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("category_name")
    val categoryName: String = "Uncategorized",
    @SerializedName("is_business")
    val isBusiness: Boolean = false,
    @SerializedName("original_category")
    val originalCategory: String? = null
)

data class CategorisationRequest(
    @SerializedName("transaction")
    val transaction: TransactionRequest,
    @SerializedName("categories")
    val categories: List<String>,
    @SerializedName("business_name")
    val businessName: String? = null,
    @SerializedName("business_industry")
    val businessIndustry: String? = null,
    @SerializedName("business_description")
    val businessDescription: String? = null
)

data class TransactionResponse(
    @SerializedName("transactions")
    val transactions: List<TransactionRequest>? = null,
    @SerializedName("merchant")
    val merchant: String? = null,
    @SerializedName("amount")
    val amount: Double? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("category_name")
    val categoryName: String? = null,
    @SerializedName("is_business")
    val isBusiness: Boolean? = null
)

data class StatusResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String
)

data class CsvUploadResponse(
    @SerializedName("transactions")
    val transactions: List<TransactionRequest>? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error")
    val error: String? = null // Some backends use 'error' instead of 'message'
)

// Auth Models
data class GoogleLoginRequest(
    @SerializedName("id_token")
    val idToken: String
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("user")
    val user: UserInfo?,
    @SerializedName("has_pin")
    val hasPin: Boolean = false,
    @SerializedName("session")
    val session: SessionInfo? = null
)

data class SessionInfo(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?
)

data class UserInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String
)

data class PinRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("pin")
    val pin: String
)

data class PinLoginRequest(
    @SerializedName("pin")
    val pin: String
)

data class PinLoginResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("user_id")
    val userId: String
)

data class PinSetupRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("pin")
    val pin: String
)

data class MessageResponse(
    @SerializedName("message")
    val message: String
)

// 3. Retrofit Instance
object NetworkClient {
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            authToken?.let {
                Log.d("NetworkClient", "Adding Auth Header: Bearer ${it.take(10)}...")
                requestBuilder.header("Authorization", "Bearer $it")
            } ?: Log.w("NetworkClient", "No Auth Token available for request: ${original.url}")
            
            val response = chain.proceed(requestBuilder.build())
            response
        }
        .build()

    val api: BudgetApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BudgetApi::class.java)
    }

    suspend fun safeAnalyzeTransaction(
        transaction: Transaction,
        availableCategories: List<String>,
        businessName: String? = null,
        businessIndustry: String? = null,
        businessDescription: String? = null
    ): AnalysisResult {
        return try {
            val txRequest = TransactionRequest(
                merchant = transaction.merchant,
                amount = transaction.amount.toDouble(),
                date = transaction.date,
                description = transaction.description,
                categoryName = transaction.categoryName.ifBlank { "Uncategorized" },
                isBusiness = transaction.isBusiness,
                originalCategory = transaction.originalCategory
            )

            val request = CategorisationRequest(
                transaction = txRequest,
                categories = availableCategories,
                businessName = businessName,
                businessIndustry = businessIndustry,
                businessDescription = businessDescription
            )
            
            val jsonRequest = gson.toJson(request)
            Log.d("AI_Network", "Request JSON: $jsonRequest")
            
            val response = api.analyzeTransaction(request)
            Log.d("AI_Network", "HTTP Status: ${response.code()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d("AI_Network", "Raw Response: ${gson.toJson(body)}")
                
                val resultList = when {
                    body == null -> null
                    !body.transactions.isNullOrEmpty() -> body.transactions
                    body.merchant != null -> listOf(
                        TransactionRequest(
                            merchant = body.merchant,
                            amount = body.amount ?: 0.0,
                            date = body.date ?: "",
                            description = body.description ?: "",
                            categoryName = body.categoryName ?: "General",
                            isBusiness = body.isBusiness ?: transaction.isBusiness
                        )
                    )
                    else -> null
                }

                if (resultList != null) {
                    AnalysisResult.Success(TransactionResponse(transactions = resultList))
                } else {
                    AnalysisResult.Error("Server response format not recognized or empty")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error details"
                Log.e("AI_Network", "Request Failed: ${response.code()} - $errorBody")
                AnalysisResult.Error("Server error (${response.code()}): $errorBody")
            }
        } catch (e: Exception) {
            Log.e("AI_Network", "Exception: ${e.localizedMessage}")
            AnalysisResult.Error("Network Error: ${e.localizedMessage}")
        }
    }

    suspend fun uploadCsv(
        file: MultipartBody.Part,
        bizCategories: RequestBody,
        persCategories: RequestBody,
        businessName: RequestBody? = null,
        businessIndustry: RequestBody? = null,
        businessDescription: RequestBody? = null
    ): CsvUploadResponse {
        return try {
            val response = api.uploadCsv(
                file, 
                bizCategories, 
                persCategories,
                businessName,
                businessIndustry,
                businessDescription
            )
            Log.d("AI_Network", "CSV Status: ${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                Log.d("AI_Network", "CSV Body: ${gson.toJson(body)}")
                body ?: CsvUploadResponse(message = "Server returned empty body (200 OK)")
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e("AI_Network", "CSV Error ($${response.code()}): $errorBody")
                CsvUploadResponse(message = "HTTP ${response.code()}: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("AI_Network", "CSV Exception: ${e.localizedMessage}")
            CsvUploadResponse(message = "Exception: ${e.localizedMessage}")
        }
    }
}

sealed class AnalysisResult {
    data class Success(val response: TransactionResponse) : AnalysisResult()
    data class Error(val message: String) : AnalysisResult()
}
