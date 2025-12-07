package com.tripmuse.controller

import com.tripmuse.dto.AddFriendRequest
import com.tripmuse.dto.FriendListResponse
import com.tripmuse.dto.FriendResponse
import com.tripmuse.dto.UserSearchListResponse
import com.tripmuse.service.FriendService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/friends")
class FriendController(
    private val friendService: FriendService
) {

    @GetMapping
    fun getFriends(
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<FriendListResponse> {
        val response = friendService.getFriends(userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/search")
    fun searchUsers(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam query: String
    ): ResponseEntity<UserSearchListResponse> {
        val response = friendService.searchUsers(userId, query)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun addFriend(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: AddFriendRequest
    ): ResponseEntity<FriendResponse> {
        val response = friendService.addFriend(userId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{friendId}")
    fun removeFriend(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable friendId: Long
    ): ResponseEntity<Unit> {
        friendService.removeFriend(userId, friendId)
        return ResponseEntity.ok().build()
    }
}
