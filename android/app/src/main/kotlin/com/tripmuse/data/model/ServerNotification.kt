package com.tripmuse.data.model

/** 서버 알림 응답 (공유 앨범 이벤트) */
data class ServerNotificationListResponse(
    val notifications: List<ServerNotification> = emptyList()
)

data class ServerNotification(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val albumId: Long?,
    val read: Boolean,
    val createdAt: String
)

data class ServerUnreadCountResponse(
    val unreadCount: Long = 0
)
