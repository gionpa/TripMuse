package com.tripmuse.data.notification

import kotlinx.serialization.Serializable

/** 인앱 알림 센터에 쌓이는 한 건. 지금은 친구 접속뿐이지만 type으로 확장한다. */
@Serializable
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean = false,
    /** 친구 접속 알림이면 해당 친구 id */
    val friendId: Long? = null
)

@Serializable
enum class NotificationType {
    FRIEND_ONLINE
}
