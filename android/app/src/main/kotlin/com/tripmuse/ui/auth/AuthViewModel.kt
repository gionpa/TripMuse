package com.tripmuse.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.auth.TokenManager
import com.tripmuse.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val mode: AuthMode = AuthMode.LOGIN
)

enum class AuthMode { LOGIN, SIGNUP }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    tokenManager: TokenManager
) : ViewModel() {

    val isLoggedIn = tokenManager.accessToken.map { !it.isNullOrBlank() }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun toggleMode() {
        val next = if (_uiState.value.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
        _uiState.value = _uiState.value.copy(mode = next, error = null)
    }

    fun authenticate(onSuccess: () -> Unit) {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password
        val nickname = email.substringBefore("@", "TripMuse User").ifBlank { "TripMuse User" }
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(error = "이메일과 비밀번호를 입력하세요.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (state.mode == AuthMode.LOGIN) {
                authRepository.login(email, password)
            } else {
                authRepository.signup(email, password, nickname)
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "로그인에 실패했습니다."
                )
            }
        }
    }

    fun authenticateNaver(accessToken: String, onSuccess: () -> Unit) {
        if (accessToken.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "네이버 토큰을 확인해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.loginWithNaver(accessToken)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "네이버 로그인에 실패했습니다."
                )
            }
        }
    }
}

