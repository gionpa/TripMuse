package com.tripmuse.auth

import com.tripmuse.auth.dto.AuthResponse
import com.tripmuse.auth.dto.LoginRequest
import com.tripmuse.auth.dto.NaverLoginRequest
import com.tripmuse.auth.dto.RefreshRequest
import com.tripmuse.auth.dto.SignupRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<AuthResponse> {
        val response = authService.signup(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/naver")
    fun loginWithNaver(@Valid @RequestBody request: NaverLoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.loginWithNaver(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.refresh(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Unit> {
        authService.logout(request)
        return ResponseEntity.ok().build()
    }
}

