package com.tripmuse.security

import com.tripmuse.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(userId: String): UserDetails {
        val id = userId.toLongOrNull() ?: throw UsernameNotFoundException("Invalid user id: $userId")
        val user = userRepository.findById(id)
            .orElseThrow { UsernameNotFoundException("User not found with id: $userId") }
        return CustomUserDetails(user.id, user.email, user.role)
    }

    fun loadUserById(id: Long): UserDetails {
        val user = userRepository.findById(id)
            .orElseThrow { UsernameNotFoundException("User not found with id: $id") }
        return CustomUserDetails(user.id, user.email, user.role)
    }
}

