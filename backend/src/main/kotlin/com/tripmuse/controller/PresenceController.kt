package com.tripmuse.controller

import com.tripmuse.dto.FriendPresenceListResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.PresenceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PresenceController(
    private val presenceService: PresenceService
) {

    /** 앱이 보이는 동안 주기적으로 호출해 접속 상태를 유지한다 */
    @PostMapping("/users/me/heartbeat")
    fun heartbeat(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<Void> {
        presenceService.heartbeat(user.id)
        return ResponseEntity.noContent().build()
    }

    /** 친구들의 접속 상태 (폴링용 경량 응답) */
    @GetMapping("/friends/presence")
    fun getFriendPresences(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<FriendPresenceListResponse> {
        return ResponseEntity.ok(presenceService.getFriendPresences(user.id))
    }
}
