package com.tripmuse.controller

import com.tripmuse.dto.response.UserResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {
    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<UserResponse> {
        val currentUser = userService.getCurrentUser(user.id)
        return ResponseEntity.ok(currentUser)
    }
}
