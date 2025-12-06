package com.tripmuse.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val customUserDetailsService: CustomUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. JWT 토큰으로 인증 시도
        val token = resolveToken(request)
        if (token != null && jwtTokenProvider.validateToken(token)) {
            val userId = jwtTokenProvider.getUserId(token)
            authenticateUser(userId, request)
        } else {
            // 2. X-User-Id 헤더로 폴백 인증 (기존 앱 호환)
            val xUserId = request.getHeader("X-User-Id")?.toLongOrNull()
            if (xUserId != null) {
                try {
                    authenticateUser(xUserId, request)
                } catch (_: Exception) {
                    // 유저가 없으면 무시하고 진행
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticateUser(userId: Long, request: HttpServletRequest) {
        val userDetails = customUserDetailsService.loadUserById(userId)
        val authentication = UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.authorities
        ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
        }
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (bearerToken.startsWith("Bearer ", ignoreCase = true)) {
            bearerToken.substring(7)
        } else null
    }
}

