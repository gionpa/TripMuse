package com.tripmuse.controller

import com.tripmuse.dto.response.UserResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.UserService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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

    @PostMapping("/me/profile-image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadProfileImage(
        @AuthenticationPrincipal user: CustomUserDetails,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<UserResponse> {
        val updatedUser = userService.updateProfileImage(user.id, file)
        return ResponseEntity.ok(updatedUser)
    }

    @DeleteMapping("/me/profile-image")
    fun deleteProfileImage(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<UserResponse> {
        val updatedUser = userService.deleteProfileImage(user.id)
        return ResponseEntity.ok(updatedUser)
    }
}
