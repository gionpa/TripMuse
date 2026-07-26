package com.tripmuse.data.notification

import kotlinx.serialization.Serializable

/** 인앱 알림 센터에 쌓이는 한 건. 기기에서 감지한 친구 접속 + 서버가 준 공유 앨범 이벤트. */
@Serializable
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean = false,
    /** 친구 접속 알림이면 해당 친구 id */
    val friendId: Long? = null,
    /** 공유 앨범 알림이면 이동할 앨범 id */
    val albumId: Long? = null
)

@Serializable
enum class NotificationType {
    FRIEND_ONLINE,      // 기기에서 감지 (로컬)
    ALBUM_MEDIA_ADDED,  // 공유 앨범 새 사진 (서버)
    ALBUM_COMMENT;      // 공유 앨범 새 댓글 (서버)

    companion object {
        fun fromServer(raw: String): NotificationType? =
            entries.firstOrNull { it.name == raw }
    }
}
