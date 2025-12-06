package com.tripmuse.data.model.auth

data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class NaverLoginRequest(
    val accessToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)

