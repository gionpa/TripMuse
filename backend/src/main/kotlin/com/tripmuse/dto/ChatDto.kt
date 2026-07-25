package com.tripmuse.dto

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatRoom
import com.tripmuse.domain.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ChatUserResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
) {
    companion object {
        fun from(user: User) = ChatUserResponse(user.id, user.nickname, user.profileImageUrl)
    }
}

data class ChatRoomResponse(
    val roomId: Long,
    val otherUser: ChatUserResponse,
    val lastMessage: String?,
    val lastMessageAt: LocalDateTime?,
    val unreadCount: Long
) {
    companion object {
        fun from(room: ChatRoom, requestUserId: Long, unreadCount: Long): ChatRoomResponse {
            return ChatRoomResponse(
                roomId = room.id,
                otherUser = ChatUserResponse.from(room.otherUser(requestUserId)),
                lastMessage = room.lastMessagePreview,
                lastMessageAt = room.lastMessageAt,
                unreadCount = unreadCount
            )
        }
    }
}

data class ChatRoomListResponse(
    val rooms: List<ChatRoomResponse>
)

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val senderNickname: String,
    val content: String,
    val createdAt: LocalDateTime,
    val isMine: Boolean,
    /** 아직 읽지 않은 참여자 수 (1:1이면 0 또는 1). 카카오톡의 말풍선 옆 숫자와 같은 의미 */
    val unreadCount: Int = 0
) {
    companion object {
        fun from(message: ChatMessage, requestUserId: Long, unreadCount: Int = 0): ChatMessageResponse {
            return ChatMessageResponse(
                id = message.id,
                roomId = message.room.id,
                senderId = message.sender.id,
                senderNickname = message.sender.nickname,
                content = message.content,
                createdAt = message.createdAt,
                isMine = message.sender.id == requestUserId,
                unreadCount = unreadCount
            )
        }
    }
}

data class ChatMessageListResponse(
    val messages: List<ChatMessageResponse>,
    val hasMore: Boolean,
    /** 상대가 어디까지 읽었는지 — 클라이언트가 이 값으로 안읽음 배지를 즉시 갱신한다 */
    val otherLastReadMessageId: Long = 0,
    val otherTyping: Boolean = false
)

data class CreateChatRoomRequest(
    val friendId: Long
)

data class SendMessageRequest(
    @field:NotBlank(message = "메시지 내용을 입력해주세요")
    @field:Size(max = 2000, message = "메시지는 2000자 이내로 입력해주세요")
    val content: String
)
