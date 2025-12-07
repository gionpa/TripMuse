package com.tripmuse.repository

import com.tripmuse.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(email: String, nickname: String): List<User>
}
