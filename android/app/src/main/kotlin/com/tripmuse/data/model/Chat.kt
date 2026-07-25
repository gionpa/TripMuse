package com.tripmuse.data.model

data class ChatUser(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
)

data class ChatRoom(
    val roomId: Long,
    val otherUser: ChatUser,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val unreadCount: Long
)

data class ChatRoomListResponse(
    val rooms: List<ChatRoom>
)

data class ChatMessage(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val senderNickname: String,
    val content: String,
    val createdAt: String,
    val isMine: Boolean,
    val unreadCount: Int = 0
)

data class ChatMessageListResponse(
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    val otherLastReadMessageId: Long = 0,
    val otherTyping: Boolean = false
)

data class ChatUnreadCountResponse(
    val totalUnread: Long
)

data class CreateChatRoomRequest(
    val friendId: Long
)

data class SendMessageRequest(
    val content: String
)

data class LocationShareStatusResponse(
    val friendId: Long,
    val locationShareStatus: String
)

/** 친구별 위치 공유 UI 상태 (백엔드 LocationShareUiStatus와 동일한 문자열) */
object LocationShareStatus {
    const val NONE = "NONE"
    const val REQUESTED_BY_ME = "REQUESTED_BY_ME"
    const val PENDING_MY_APPROVAL = "PENDING_MY_APPROVAL"
    const val APPROVED = "APPROVED"
}
