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
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val storageService: StorageService
) {
    companion object {
        const val PROFILE_IMAGE_DIR = "profiles"
        const val PROFILE_THUMBNAIL_DIR = "profiles/thumbnails"
    }

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

    @Transactional
    fun updateProfileImage(userId: Long, file: MultipartFile): UserResponse {
        val user = findUserById(userId)

        // Delete old profile image if exists
        user.profileImageUrl?.let { oldUrl ->
            val oldPath = oldUrl.removePrefix("/media/files/")
            try {
                storageService.delete(oldPath)
                // Delete thumbnail too
                val thumbnailPath = oldPath.replace(PROFILE_IMAGE_DIR, PROFILE_THUMBNAIL_DIR)
                storageService.delete(thumbnailPath)
            } catch (e: Exception) {
                // Ignore deletion errors
            }
        }

        // Store new profile image
        val imagePath = storageService.store(file, PROFILE_IMAGE_DIR)

        // Generate thumbnail
        val thumbnailPath = storageService.generateImageThumbnail(imagePath)

        // Update user's profile image URL (use thumbnail URL for faster loading)
        val imageUrl = "/media/files/$imagePath"
        user.profileImageUrl = imageUrl

        val savedUser = userRepository.save(user)
        val stats = getUserStats(userId)
        return UserResponse.from(savedUser, stats)
    }

    @Transactional
    fun deleteProfileImage(userId: Long): UserResponse {
        val user = findUserById(userId)

        user.profileImageUrl?.let { oldUrl ->
            val oldPath = oldUrl.removePrefix("/media/files/")
            try {
                storageService.delete(oldPath)
                // Delete thumbnail too
                val thumbnailPath = oldPath.replace(PROFILE_IMAGE_DIR, PROFILE_THUMBNAIL_DIR)
                storageService.delete(thumbnailPath)
            } catch (e: Exception) {
                // Ignore deletion errors
            }
        }

        user.profileImageUrl = null
        val savedUser = userRepository.save(user)
        val stats = getUserStats(userId)
        return UserResponse.from(savedUser, stats)
    }
}
