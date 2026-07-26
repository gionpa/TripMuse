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
    val unreadCount: Int = 0,
    // Gson은 알 수 없는 값/누락 시 null을 넣으므로 nullable로 두고 사용처에서 TEXT로 취급
    val type: String? = null,
    val imageUrl: String? = null
) {
    val isImage: Boolean get() = type == ChatMessageType.IMAGE && imageUrl != null
}

object ChatMessageType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
}

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
