package com.tripmuse.data.auth

import android.util.Log
import com.tripmuse.BuildConfig
import com.tripmuse.data.model.auth.AuthResponse
import com.tripmuse.data.model.auth.RefreshRequest
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authEventManager: AuthEventManager
) : Authenticator {

    companion object {
        private const val TAG = "TokenRefreshAuth"
        private const val MAX_RETRY_COUNT = 2
    }

    private val refreshClient = OkHttpClient.Builder()
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0

        if (retryCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Max retry count reached, clearing tokens")
            tokenManager.clearSync()
            authEventManager.emitUnauthorized()
            return null
        }

        val refreshToken = tokenManager.getRefreshTokenSync()
        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "No refresh token available")
            tokenManager.clearSync()
            authEventManager.emitUnauthorized()
            return null
        }

        synchronized(this) {
            val currentAccessToken = tokenManager.getAccessTokenSync()
            val requestAccessToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            if (currentAccessToken != null && currentAccessToken != requestAccessToken) {
                Log.d(TAG, "Token already refreshed by another request")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .header("X-Retry-Count", (retryCount + 1).toString())
                    .build()
            }

            return try {
                val newTokens = refreshTokenSync(refreshToken)
                if (newTokens != null) {
                    Log.d(TAG, "Token refresh successful")
                    tokenManager.saveTokensSync(newTokens.accessToken, newTokens.refreshToken)

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .header("X-Retry-Count", (retryCount + 1).toString())
                        .build()
                } else {
                    Log.w(TAG, "Token refresh failed")
                    tokenManager.clearSync()
                    authEventManager.emitUnauthorized()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh error", e)
                tokenManager.clearSync()
                authEventManager.emitUnauthorized()
                null
            }
        }
    }

    private fun refreshTokenSync(refreshToken: String): AuthResponse? {
        val json = JSONObject().apply {
            put("refreshToken", refreshToken)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "auth/refresh")
            .post(requestBody)
            .build()

        return try {
            val response = refreshClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    val jsonResponse = JSONObject(body)
                    AuthResponse(
                        accessToken = jsonResponse.getString("accessToken"),
                        refreshToken = jsonResponse.getString("refreshToken")
                    )
                }
            } else {
                Log.w(TAG, "Refresh request failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh request error", e)
            null
        }
    }
}
