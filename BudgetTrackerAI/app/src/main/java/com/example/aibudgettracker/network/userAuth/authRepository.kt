package com.example.aibudgettracker.network.userAuth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.aibudgettracker.network.GoogleLoginRequest
import com.example.aibudgettracker.network.NetworkClient
import com.example.aibudgettracker.network.PinLoginRequest
import com.example.aibudgettracker.network.PinRequest
import com.example.aibudgettracker.network.PinSetupRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.status.SessionStatus
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import android.content.SharedPreferences
import android.util.Log

val supabase = createSupabaseClient(
    supabaseUrl = "https://vakufwbazkajnhetftxh.supabase.co",
    supabaseKey = "sb_publishable_QKFvO8jTjK0BvJtof-Y6HQ_dgR1KKM-"
) {
    install(Auth)
    install(Postgrest)
}

suspend fun launchGoogleSignIn(context: Context): Result<String> {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId("180398268036-ahu9k205e1uu1d2u59fi4ao9fi7833i2.apps.googleusercontent.com")
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential

        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            Log.d("AuthRepo", "Google ID Token obtained (length: ${idToken.length})")
            
            // Decoded details for debugging
            Log.d("AuthRepo", "Token Email: ${googleIdTokenCredential.id}")
            Log.d("AuthRepo", "Token Display Name: ${googleIdTokenCredential.displayName}")
            
            Result.success(idToken)
        } else {
            Result.failure(Exception("Unexpected credential type: ${credential.type}"))
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        Log.e("AuthRepo", "Google Sign-In Error Code: ${e.type}", e)
        Result.failure(Exception(e.message ?: "Google Sign-In failed"))
    } catch (e: Exception) {
        Log.e("AuthRepo", "Unexpected Error", e)
        Result.failure(e)
    }
}

/**
 * 1. Secure Nonce Helpers
 */
object NonceUtils {
    fun generateRawNonce(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return (1..32).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    fun String.toSha256(): String {
        val bytes = this.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}

/**
 * 2. Secure Sign-In Function
 * @param idToken The token received from Google
 */
suspend fun onGoogleSignIn(idToken: String) {
    try {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
        println("Fintech-level secure login successful!")
    } catch (e: Exception) {
        println("Auth failed: ${e.localizedMessage}")
    }
}

class AuthRepository(private val supabase: SupabaseClient, private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)

    init {
        // Restore token on startup
        val savedToken = prefs.getString("access_token", null)
        Log.d("AuthRepo", "Restoring token from prefs: ${savedToken?.take(10)}...")
        if (savedToken != null) {
            NetworkClient.setAuthToken(savedToken)
        }
    }

    // Expose the session status so the UI can react (e.g., hiding/showing the Sign In button)
    val sessionStatus: Flow<SessionStatus> = supabase.auth.sessionStatus

    fun hasBackendSession(): Boolean = prefs.getString("access_token", null) != null

    suspend fun loginToBackend(idToken: String): Result<Boolean> {
        Log.d("AuthRepo", "Attempting backend login with ID Token...")
        return try {
            val response = NetworkClient.api.loginWithGoogle(GoogleLoginRequest(idToken))
            Log.d("AuthRepo", "Backend login response code: ${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                val actualToken = body?.accessToken ?: body?.session?.accessToken
                val actualRefresh = body?.refreshToken ?: body?.session?.refreshToken
                
                if (body != null && actualToken != null) {
                    Log.d("AuthRepo", "Login success, saving token: ${actualToken.take(10)}...")
                    prefs.edit()
                        .putString("access_token", actualToken)
                        .putString("refresh_token", actualRefresh)
                        .putBoolean("has_pin", body.hasPin)
                        .apply()
                    NetworkClient.setAuthToken(actualToken)
                    Result.success(body.hasPin)
                } else {
                    val rawBody = response.errorBody()?.string() ?: NetworkClient.gson.toJson(body) ?: "No body"
                    val errorMsg = if (body == null) "Empty response body" else "Access token is null. Raw: $rawBody"
                    Log.e("AuthRepo", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e("AuthRepo", "Backend Reject (${response.code()}): $errorMsg")
                Result.failure(Exception("Backend: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepo", "Backend Connection Error", e)
            Result.failure(e)
        }
    }

    suspend fun setupPin(email: String, pin: String): Result<String> {
        return try {
            val response = NetworkClient.api.setupPin(PinSetupRequest(email, pin))
            if (response.isSuccessful) {
                prefs.edit().putBoolean("has_pin", true).apply()
                Result.success(response.body()?.message ?: "PIN setup successful")
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Result.failure(Exception("PIN Setup Failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPin(pin: String): Result<Boolean> {
        return try {
            val token = supabase.auth.currentSessionOrNull()?.accessToken
            if (token == null) {
                return Result.failure(Exception("No Supabase session found"))
            }

            val response = NetworkClient.api.loginWithPin("Bearer $token", PinLoginRequest(pin))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Result.failure(Exception("PIN Login Failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isPinRequired(): Boolean {
        return prefs.getBoolean("has_pin", false)
    }

    // Get the email for display purposes
    fun getUserEmail(): String? = supabase.auth.currentSessionOrNull()?.user?.email

    /**
     * This is the "Key" we send to your server.
     * The server will verify this JWT with Supabase to prove the user is who they say they are.
     */
    fun getAuthToken(): String? = supabase.auth.currentSessionOrNull()?.accessToken ?: prefs.getString("access_token", null)

    fun getUserId(): String? {
        // This reaches into the current session and gets the User's ID
        return supabase.auth.currentSessionOrNull()?.user?.id
    }

    suspend fun signOut() {
        supabase.auth.signOut()
        prefs.edit().clear().apply()
        NetworkClient.setAuthToken(null)
    }
}