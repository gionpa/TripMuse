package com.tripmuse.controller

import com.tripmuse.dto.AddFriendRequest
import com.tripmuse.dto.FriendListResponse
import com.tripmuse.dto.FriendResponse
import com.tripmuse.dto.UserSearchListResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.FriendService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/friends")
class FriendController(
    private val friendService: FriendService
) {

    @GetMapping
    fun getFriends(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<FriendListResponse> {
        val response = friendService.getFriends(user.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/search")
    fun searchUsers(
        @AuthenticationPrincipal user: CustomUserDetails,
        @RequestParam query: String
    ): ResponseEntity<UserSearchListResponse> {
        val response = friendService.searchUsers(user.id, query)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun addFriend(
        @AuthenticationPrincipal user: CustomUserDetails,
        @Valid @RequestBody request: AddFriendRequest
    ): ResponseEntity<FriendResponse> {
        val response = friendService.addFriend(user.id, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{friendId}")
    fun removeFriend(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable friendId: Long
    ): ResponseEntity<Unit> {
        friendService.removeFriend(user.id, friendId)
        return ResponseEntity.ok().build()
    }
}
