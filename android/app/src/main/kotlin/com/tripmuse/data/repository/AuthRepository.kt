package com.tripmuse.data.repository

import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.auth.TokenManager
import com.tripmuse.data.model.auth.AuthResponse
import com.tripmuse.data.model.auth.LoginRequest
import com.tripmuse.data.model.auth.NaverLoginRequest
import com.tripmuse.data.model.auth.SignupRequest
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
                // 백엔드에서 반환하는 구체적인 에러 메시지 사용
                val errorBody = response.errorBody()?.string()
                val errorMessage = if (errorBody != null && errorBody.isNotEmpty()) {
                    try {
                        // Spring의 ResponseStatusException은 "message" 필드에 에러 메시지를 담아 반환
                        val jsonStart = errorBody.indexOf("\"message\":")
                        if (jsonStart != -1) {
                            val msgStart = errorBody.indexOf("\"", jsonStart + 10) + 1
                            val msgEnd = errorBody.indexOf("\"", msgStart)
                            if (msgEnd > msgStart) {
                                errorBody.substring(msgStart, msgEnd)
                            } else {
                                getDefaultErrorMessage(response.code())
                            }
                        } else {
                            getDefaultErrorMessage(response.code())
                        }
                    } catch (e: Exception) {
                        getDefaultErrorMessage(response.code())
                    }
                } else {
                    getDefaultErrorMessage(response.code())
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            Result.failure(Exception("네트워크 연결을 확인해주세요. 인터넷에 연결되어 있는지 확인하세요."))
        } catch (e: ConnectException) {
            Result.failure(Exception("서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."))
        } catch (e: Exception) {
            Result.failure(Exception("네트워크 오류: ${e.localizedMessage ?: "알 수 없는 오류"}"))
        }
    }

    private fun getDefaultErrorMessage(code: Int): String {
        return when (code) {
            401 -> "이메일 또는 비밀번호가 올바르지 않습니다."
            403 -> "접근이 거부되었습니다."
            404 -> "사용자를 찾을 수 없습니다."
            409 -> "이미 등록된 이메일입니다."
            500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            else -> "인증 실패: $code"
        }
    }
}

