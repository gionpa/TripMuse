package com.tripmuse.controller

import com.tripmuse.dto.FriendLocationResponse
import com.tripmuse.dto.UpdateLocationRequest
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.LocationShareService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class LocationController(
    private val locationShareService: LocationShareService
) {

    @PutMapping("/users/me/location")
    fun updateMyLocation(
        @AuthenticationPrincipal user: CustomUserDetails,
        @Valid @RequestBody request: UpdateLocationRequest
    ): ResponseEntity<Void> {
        locationShareService.updateMyLocation(user.id, request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/friends/{friendId}/location")
    fun getFriendLocation(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable friendId: Long
    ): ResponseEntity<FriendLocationResponse> {
        return ResponseEntity.ok(locationShareService.getFriendLocation(user.id, friendId))
    }
}
