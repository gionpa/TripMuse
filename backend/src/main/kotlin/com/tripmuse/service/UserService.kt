package com.tripmuse.service

import com.tripmuse.domain.User
import com.tripmuse.dto.response.UserResponse
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    fun getCurrentUser(userId: Long): UserResponse {
        val user = findUserById(userId)
        return UserResponse.from(user)
    }

    fun findUserById(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
    }

    @Transactional
    fun createUser(email: String, nickname: String): User {
        val user = User(
            email = email,
            nickname = nickname
        )
        return userRepository.save(user)
    }

    fun findOrCreateUser(email: String, nickname: String): User {
        return userRepository.findByEmail(email)
            .orElseGet { createUser(email, nickname) }
    }
}
