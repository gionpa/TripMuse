package com.tripmuse.config

import com.tripmuse.domain.User
import com.tripmuse.repository.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.annotation.Transactional

@Configuration
@Profile("local")
class DataInitializer {

    @Bean
    fun initDummyData(userRepository: UserRepository): CommandLineRunner {
        return CommandLineRunner {
            // Create dummy user if not exists
            if (!userRepository.existsByEmail("test@tripmuse.com")) {
                val dummyUser = User(
                    email = "test@tripmuse.com",
                    nickname = "테스트유저"
                )
                userRepository.save(dummyUser)
                println("Dummy user created: id=${dummyUser.id}, email=${dummyUser.email}")
            }

            // Create second dummy user for testing comments
            if (!userRepository.existsByEmail("friend@tripmuse.com")) {
                val friendUser = User(
                    email = "friend@tripmuse.com",
                    nickname = "친구유저"
                )
                userRepository.save(friendUser)
                println("Friend user created: id=${friendUser.id}, email=${friendUser.email}")
            }
        }
    }
}

@Configuration
@Profile("prod")
class ProdDataInitializer(
    private val entityManager: EntityManager
) {

    @Bean
    fun initProdData(userRepository: UserRepository): CommandLineRunner {
        return CommandLineRunner {
            // Create default user if not exists
            if (!userRepository.existsByEmail("user@tripmuse.com")) {
                val defaultUser = User(
                    email = "user@tripmuse.com",
                    nickname = "TripMuse User"
                )
                userRepository.save(defaultUser)
                println("Default user created: id=${defaultUser.id}, email=${defaultUser.email}")
            }

            // Fix file paths ending with dot (migration for files without extension)
            fixBrokenFilePaths()
        }
    }

    @Transactional
    fun fixBrokenFilePaths() {
        // Fix media file paths using native query
        val mediaUpdated = entityManager.createNativeQuery("""
            UPDATE media SET
                file_path = CONCAT(SUBSTRING(file_path, 1, LENGTH(file_path) - 1), 'jpg')
            WHERE file_path LIKE '%.'
        """.trimIndent()).executeUpdate()

        val thumbnailUpdated = entityManager.createNativeQuery("""
            UPDATE media SET
                thumbnail_path = CONCAT(SUBSTRING(thumbnail_path, 1, LENGTH(thumbnail_path) - 1), 'jpg')
            WHERE thumbnail_path LIKE '%.'
        """.trimIndent()).executeUpdate()

        // Fix album cover image URLs
        val albumUpdated = entityManager.createNativeQuery("""
            UPDATE albums SET
                cover_image_url = CONCAT(SUBSTRING(cover_image_url, 1, LENGTH(cover_image_url) - 1), 'jpg')
            WHERE cover_image_url LIKE '%.'
        """.trimIndent()).executeUpdate()

        if (mediaUpdated > 0 || thumbnailUpdated > 0 || albumUpdated > 0) {
            println("File path migration completed: $mediaUpdated media paths, $thumbnailUpdated thumbnails, $albumUpdated album covers fixed")
        }
    }
}
