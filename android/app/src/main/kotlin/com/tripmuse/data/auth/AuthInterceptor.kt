package com.tripmuse.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val authEventManager: AuthEventManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = tokenManager.getAccessTokenSync()

        val response = if (!accessToken.isNullOrBlank()) {
            val newReq = request.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(request)
        }

        // 403 Forbidden 에러 처리 - 토큰 만료 또는 권한 없음
        if (response.code == 403) {
            runBlocking {
                tokenManager.clear()
                authEventManager.emitUnauthorized()
            }
        }

        return response
        // Note: 401 handling is now done by TokenRefreshAuthenticator
    }
}

