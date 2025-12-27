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

data class StorageUsageResponse(
    val imageBytes: Long,
    val videoBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long,
    val usagePercent: Double
) {
    companion object {
        const val MAX_STORAGE_BYTES: Long = 500 * 1024 * 1024 // 500MB

        fun from(imageBytes: Long, videoBytes: Long): StorageUsageResponse {
            val totalBytes = imageBytes + videoBytes
            val usagePercent = (totalBytes.toDouble() / MAX_STORAGE_BYTES * 100).coerceIn(0.0, 100.0)
            return StorageUsageResponse(
                imageBytes = imageBytes,
                videoBytes = videoBytes,
                totalBytes = totalBytes,
                maxBytes = MAX_STORAGE_BYTES,
                usagePercent = usagePercent
            )
        }
    }
}
