package com.tripmuse.auth

import com.tripmuse.auth.dto.AuthResponse
import com.tripmuse.auth.dto.LoginRequest
import com.tripmuse.auth.dto.NaverLoginRequest
import com.tripmuse.auth.dto.RefreshRequest
import com.tripmuse.auth.dto.SignupRequest
import com.tripmuse.client.NaverOAuthClient
import com.tripmuse.domain.Provider
import com.tripmuse.domain.Role
import com.tripmuse.domain.User
import com.tripmuse.repository.UserRepository
import com.tripmuse.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate,
    private val naverOAuthClient: NaverOAuthClient
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun signup(request: SignupRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다")
        }

        val user = User(
            email = request.email,
            nickname = request.nickname,
            password = passwordEncoder.encode(request.password),
            role = Role.USER,
            provider = Provider.LOCAL,
            providerId = null
        )
        val savedUser = userRepository.save(user)
        return issueTokens(savedUser)
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "존재하지 않는 아이디입니다") }

        if (user.provider != Provider.LOCAL) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "소셜 계정으로 로그인해주세요")
        }

        if (user.password == null || !passwordEncoder.matches(request.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다")
        }

        return issueTokens(user)
    }

    @Transactional
    fun loginWithNaver(request: NaverLoginRequest): AuthResponse {
        log.info("Naver login attempt with token: ${request.accessToken.take(10)}...")

        val profile = naverOAuthClient.fetchUserInfo(request.accessToken)
        if (profile == null) {
            log.error("Failed to fetch Naver user info - token may be invalid or expired")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "네이버 토큰 인증에 실패했습니다")
        }

        log.info("Naver profile fetched - id: ${profile.id}, email: ${profile.email}, nickname: ${profile.nickname}")

        val email = profile.email
        if (email == null) {
            log.error("Naver profile missing email - id: ${profile.id}")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "네이버 계정에서 이메일 정보를 가져올 수 없습니다")
        }

        val existingUser = userRepository.findByEmail(email)
        val isNewUser = existingUser.isEmpty
        log.info("User lookup by email '$email' - found: ${!isNewUser}")

        val user = existingUser.orElseGet {
            log.info("Creating new user for email: $email")
            User(
                email = email,
                nickname = profile.nickname ?: "TripMuse User",
                profileImageUrl = profile.profile_image,
                password = null,
                role = Role.USER,
                provider = Provider.NAVER,
                providerId = profile.id
            )
        }

        if (user.provider == Provider.LOCAL) {
            log.info("User has LOCAL provider, keeping existing provider")
        } else {
            if (user.profileImageUrl == null && profile.profile_image != null) {
                log.info("Updating profile image for user: ${user.id}")
                user.profileImageUrl = profile.profile_image
            }
            if (user.nickname.isBlank() && profile.nickname != null) {
                log.info("Updating nickname for user: ${user.id}")
                user.nickname = profile.nickname
            }
            if (user.providerId == null) {
                log.info("Setting providerId for user: ${user.id}")
                user.providerId = profile.id
            }
        }

        val saved = userRepository.save(user)
        log.info("User saved successfully - id: ${saved.id}, email: ${saved.email}, isNewUser: $isNewUser")

        val tokens = issueTokens(saved)
        log.info("Tokens issued successfully for user: ${saved.id}")

        return tokens
    }

    @Transactional(readOnly = true)
    fun refresh(request: RefreshRequest): AuthResponse {
        val refreshToken = request.refreshToken
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다")
        }
        val userId = jwtTokenProvider.getUserId(refreshToken)
        val stored = redisTemplate.opsForValue().get(refreshKey(userId))
        if (stored == null || stored != refreshToken) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰을 찾을 수 없습니다")
        }
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다") }
        return issueTokens(user)
    }

    @Transactional
    fun logout(request: RefreshRequest) {
        val refreshToken = request.refreshToken
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return
        }
        val userId = jwtTokenProvider.getUserId(refreshToken)
        redisTemplate.delete(refreshKey(userId))
    }

    private fun issueTokens(user: User): AuthResponse {
        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.role)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id, user.role)
        redisTemplate.opsForValue().set(
            refreshKey(user.id),
            refreshToken,
            Duration.ofSeconds(jwtTokenProvider.getRefreshTokenValiditySeconds())
        )
        return AuthResponse(accessToken, refreshToken)
    }

    private fun refreshKey(userId: Long): String = "rt:$userId"
}

