package com.tripmuse.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthEvent {
    object Unauthorized : AuthEvent()
}

@Singleton
class AuthEventManager @Inject constructor() {
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents = _authEvents.asSharedFlow()

    fun emitUnauthorized() {
        _authEvents.tryEmit(AuthEvent.Unauthorized)
    }
}
