package com.tripmuse.repository

import com.tripmuse.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(email: String, nickname: String): List<User>

    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN Album a ON a.user = u
        LEFT JOIN Media m ON m.album.user = u
        WHERE u.createdAt >= :since
           OR a.updatedAt >= :since
           OR m.createdAt >= :since
        ORDER BY u.id
    """)
    fun findRecentActiveUsers(since: LocalDateTime): List<User>
}
