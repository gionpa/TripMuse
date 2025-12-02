package com.tripmuse.controller

import com.tripmuse.dto.response.UserResponse
import com.tripmuse.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {
    // TODO: Replace with actual authentication
    // For now, using header-based user ID
    @GetMapping("/me")
    fun getCurrentUser(
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<UserResponse> {
        val user = userService.getCurrentUser(userId)
        return ResponseEntity.ok(user)
    }
}
