package com.tripmuse.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthEvent {
    object Unauthorized : AuthEvent()
}

@Singleton
class AuthEventManager @Inject constructor() {
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents = _authEvents.asSharedFlow()

    // 인증 에러 발생 여부 (로그인 화면 전환 중 에러 표시 방지용)
    private val _isAuthError = MutableStateFlow(false)
    val isAuthError = _isAuthError.asStateFlow()

    fun emitUnauthorized() {
        _isAuthError.value = true
        _authEvents.tryEmit(AuthEvent.Unauthorized)
    }

    fun clearAuthError() {
        _isAuthError.value = false
    }
}
