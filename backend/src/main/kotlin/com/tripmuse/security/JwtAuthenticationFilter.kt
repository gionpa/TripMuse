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
        // JWT 토큰만으로 인증한다.
        // (과거 X-User-Id 헤더 폴백은 헤더 하나로 아무 사용자나 될 수 있어 제거했다.
        //  토큰이 없거나 위조면 인증 컨텍스트를 비워둔 채 진행하고, 보호된 경로는
        //  SecurityConfig가 막는다.)
        val token = resolveToken(request)
        if (token != null && jwtTokenProvider.validateToken(token)) {
            val userId = jwtTokenProvider.getUserId(token)
            try {
                authenticateUser(userId, request)
            } catch (_: Exception) {
                // 유저가 삭제되었으면 인증하지 않고 진행
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

