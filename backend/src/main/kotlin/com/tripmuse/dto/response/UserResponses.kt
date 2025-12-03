package com.tripmuse.dto.response

import com.tripmuse.domain.User
import java.time.LocalDateTime

data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val createdAt: LocalDateTime,
    val stats: UserStats? = null
) {
    companion object {
        fun from(user: User, stats: UserStats? = null): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                createdAt = user.createdAt,
                stats = stats
            )
        }
    }
}

data class UserStats(
    val albumCount: Long,
    val imageCount: Long,
    val videoCount: Long
)
