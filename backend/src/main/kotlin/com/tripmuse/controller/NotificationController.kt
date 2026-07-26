package com.tripmuse.controller

import com.tripmuse.domain.NotificationType
import com.tripmuse.repository.NotificationRepository
import com.tripmuse.security.CustomUserDetails
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationRepository: NotificationRepository
) {

    @GetMapping
    fun getNotifications(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<NotificationListResponse> {
        val items = notificationRepository
            .findTop50ByRecipientUserIdOrderByCreatedAtDesc(user.id)
            .map {
                NotificationResponse(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    body = it.body,
                    albumId = it.albumId,
                    read = it.read,
                    createdAt = it.createdAt
                )
            }
        return ResponseEntity.ok(NotificationListResponse(items))
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<UnreadCountResponse> {
        val count = notificationRepository.countByRecipientUserIdAndReadFalse(user.id)
        return ResponseEntity.ok(UnreadCountResponse(count))
    }

    @PostMapping("/read")
    @Transactional
    fun markAllRead(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<Unit> {
        notificationRepository.markAllRead(user.id)
        return ResponseEntity.ok().build()
    }
}

data class NotificationListResponse(
    val notifications: List<NotificationResponse>
)

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val albumId: Long?,
    val read: Boolean,
    val createdAt: LocalDateTime
)

data class UnreadCountResponse(
    val unreadCount: Long
)
