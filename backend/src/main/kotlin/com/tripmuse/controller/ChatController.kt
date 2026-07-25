package com.tripmuse.controller

import com.tripmuse.dto.ChatMessageListResponse
import com.tripmuse.dto.ChatMessageResponse
import com.tripmuse.dto.ChatRoomListResponse
import com.tripmuse.dto.ChatRoomResponse
import com.tripmuse.dto.CreateChatRoomRequest
import com.tripmuse.dto.SendMessageRequest
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.ChatService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val chatService: ChatService
) {

    @PostMapping
    fun getOrCreateRoom(
        @AuthenticationPrincipal user: CustomUserDetails,
        @RequestBody request: CreateChatRoomRequest
    ): ResponseEntity<ChatRoomResponse> {
        return ResponseEntity.ok(chatService.getOrCreateRoom(user.id, request.friendId))
    }

    @GetMapping
    fun getRooms(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<ChatRoomListResponse> {
        return ResponseEntity.ok(chatService.getRooms(user.id))
    }

    @GetMapping("/{roomId}")
    fun getRoom(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable roomId: Long
    ): ResponseEntity<ChatRoomResponse> {
        return ResponseEntity.ok(chatService.getRoom(roomId, user.id))
    }

    @GetMapping("/{roomId}/messages")
    fun getMessages(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable roomId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) afterId: Long?
    ): ResponseEntity<ChatMessageListResponse> {
        return ResponseEntity.ok(chatService.getMessages(roomId, user.id, beforeId, afterId))
    }

    @PostMapping("/{roomId}/messages")
    fun sendMessage(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<ChatMessageResponse> {
        val message = chatService.sendMessage(roomId, user.id, request.content)
        return ResponseEntity.status(HttpStatus.CREATED).body(message)
    }

    @PostMapping("/{roomId}/read")
    fun markAsRead(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable roomId: Long
    ): ResponseEntity<Void> {
        chatService.markAsRead(roomId, user.id)
        return ResponseEntity.noContent().build()
    }
}
