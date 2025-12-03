package com.tripmuse.service

import com.tripmuse.domain.MediaType
import com.tripmuse.domain.User
import com.tripmuse.dto.response.UserResponse
import com.tripmuse.dto.response.UserStats
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.AlbumRepository
import com.tripmuse.repository.MediaRepository
import com.tripmuse.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository
) {
    fun getCurrentUser(userId: Long): UserResponse {
        val user = findUserById(userId)
        val stats = getUserStats(userId)
        return UserResponse.from(user, stats)
    }

    private fun getUserStats(userId: Long): UserStats {
        val albumCount = albumRepository.countByUserId(userId)
        val imageCount = mediaRepository.countByUserIdAndType(userId, MediaType.IMAGE)
        val videoCount = mediaRepository.countByUserIdAndType(userId, MediaType.VIDEO)
        return UserStats(albumCount, imageCount, videoCount)
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
