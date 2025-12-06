package com.tripmuse.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    @field:Size(min = 6, max = 50)
    val password: String,
    @field:NotBlank
    val nickname: String
)

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val password: String
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String
)

data class NaverLoginRequest(
    @field:NotBlank
    val accessToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)

