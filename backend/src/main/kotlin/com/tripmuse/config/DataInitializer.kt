package com.tripmuse.config

import com.tripmuse.domain.User
import com.tripmuse.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

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
class ProdDataInitializer {

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
        }
    }
}
