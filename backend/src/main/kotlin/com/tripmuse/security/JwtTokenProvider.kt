package com.tripmuse.security

import com.tripmuse.domain.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-validity-in-seconds:3600}") private val accessTokenValidityInSeconds: Long,
    @Value("\${jwt.refresh-token-validity-in-seconds:1209600}") private val refreshTokenValidityInSeconds: Long
) {

    private val signingKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun generateAccessToken(userId: Long, role: Role): String {
        return createToken(userId, role, accessTokenValidityInSeconds)
    }

    fun generateRefreshToken(userId: Long, role: Role): String {
        return createToken(userId, role, refreshTokenValidityInSeconds)
    }

    private fun createToken(userId: Long, role: Role, validitySeconds: Long): String {
        val now = Date()
        val expiry = Date(now.time + validitySeconds * 1000)
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role.name)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getUserId(token: String): Long {
        return parseClaims(token).subject.toLong()
    }

    fun getRole(token: String): Role {
        val roleName = parseClaims(token)["role"] as String
        return Role.valueOf(roleName)
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getRefreshTokenValiditySeconds(): Long = refreshTokenValidityInSeconds

    private fun parseClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .body
    }
}

