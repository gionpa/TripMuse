package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.auth.TokenManager
import com.tripmuse.data.model.auth.AuthResponse
import com.tripmuse.data.model.auth.LoginRequest
import com.tripmuse.data.model.auth.NaverLoginRequest
import com.tripmuse.data.model.auth.SignupRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: TripMuseApi,
    private val tokenManager: TokenManager
) {

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return runCatchingAuth {
            api.login(LoginRequest(email, password))
        }
    }

    suspend fun signup(email: String, password: String, nickname: String): Result<AuthResponse> {
        return runCatchingAuth {
            api.signup(SignupRequest(email, password, nickname))
        }
    }

    suspend fun loginWithNaver(accessToken: String): Result<AuthResponse> {
        return runCatchingAuth {
            api.loginWithNaver(NaverLoginRequest(accessToken))
        }
    }

    suspend fun logout() {
        tokenManager.clear()
    }

    private suspend fun runCatchingAuth(block: suspend () -> retrofit2.Response<AuthResponse>): Result<AuthResponse> {
        return try {
            val response = block()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                Result.success(body)
            } else {
                Result.failure(Exception("Auth failed: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

