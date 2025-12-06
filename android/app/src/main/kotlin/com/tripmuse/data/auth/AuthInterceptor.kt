package com.tripmuse.data.auth

import kotlinx.coroutines.flow.firstOrNull
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
        val accessToken = runBlocking { tokenManager.accessToken.firstOrNull() }

        val response = if (!accessToken.isNullOrBlank()) {
            val newReq = request.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(request)
        }

        // Handle 401/403 responses - clear token and emit unauthorized event
        if (response.code == 401 || response.code == 403) {
            runBlocking { tokenManager.clear() }
            authEventManager.emitUnauthorized()
        }

        return response
    }
}

