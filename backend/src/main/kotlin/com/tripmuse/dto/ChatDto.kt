package com.tripmuse.dto

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatMessageType
import com.tripmuse.domain.ChatRoomType
import com.tripmuse.domain.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ChatUserResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val characterStyle: String?
) {
    companion object {
        fun from(user: User) = ChatUserResponse(user.id, user.nickname, user.profileImageUrl, user.characterStyle)
    }
}

data class ChatRoomResponse(
    val roomId: Long,
    val type: ChatRoomType,
    /** 표시용 방 이름. 1:1이면 상대 닉네임, 그룹이면 방 제목 또는 참여자 닉네임 조합 */
    val title: String,
    /** 1:1 방의 상대. 그룹이면 null */
    val otherUser: ChatUserResponse?,
    val members: List<ChatUserResponse>,
    val memberCount: Int,
    val lastMessage: String?,
    val lastMessageAt: LocalDateTime?,
    val unreadCount: Long
)

data class ChatRoomListResponse(
    val rooms: List<ChatRoomResponse>
)

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val senderNickname: String,
    val senderProfileImageUrl: String?,
    val content: String,
    val createdAt: LocalDateTime,
    val isMine: Boolean,
    /** 아직 읽지 않은 참여자 수. 카카오톡의 말풍선 옆 숫자와 같은 의미 */
    val unreadCount: Int = 0,
    val type: ChatMessageType = ChatMessageType.TEXT,
    /** 사진/동영상 메시지의 파일 URL (/media/files/...) */
    val imageUrl: String? = null,
    /** 동영상 메시지의 썸네일 URL */
    val thumbnailUrl: String? = null
) {
    companion object {
        fun from(message: ChatMessage, requestUserId: Long, unreadCount: Int = 0): ChatMessageResponse {
            return ChatMessageResponse(
                id = message.id,
                roomId = message.room.id,
                senderId = message.sender.id,
                senderNickname = message.sender.nickname,
                senderProfileImageUrl = message.sender.profileImageUrl,
                content = message.content,
                createdAt = message.createdAt,
                isMine = message.sender.id == requestUserId,
                unreadCount = unreadCount,
                type = message.type ?: ChatMessageType.TEXT,
                imageUrl = message.imagePath?.let { "/media/files/$it" },
                thumbnailUrl = message.thumbnailPath?.let { "/media/files/$it" }
            )
        }
    }
}

/** 참여자별 읽음 위치. 클라이언트가 말풍선 옆 숫자를 즉시 갱신하는 데 쓴다 */
data class ChatReadCursor(
    val userId: Long,
    val lastReadMessageId: Long,
    val visibleFromMessageId: Long
)

data class ChatMessageListResponse(
    val messages: List<ChatMessageResponse>,
    val hasMore: Boolean,
    /** 1:1 방 호환용. 그룹에서는 readCursors를 사용한다 */
    val otherLastReadMessageId: Long = 0,
    val otherTyping: Boolean = false,
    /** 그룹에서 누가 입력 중인지 표시하기 위한 닉네임 */
    val typingNickname: String? = null,
    val readCursors: List<ChatReadCursor> = emptyList()
)

data class ChatUnreadCountResponse(
    val totalUnread: Long
)

data class CreateChatRoomRequest(
    val friendId: Long
)

data class SendMessageRequest(
    @field:NotBlank(message = "메시지 내용을 입력해주세요")
    @field:Size(max = 2000, message = "메시지는 2000자 이내로 입력해주세요")
    val content: String
)

data class InviteMembersRequest(
    @field:NotEmpty(message = "초대할 친구를 선택해주세요")
    val friendIds: List<Long>,

    /** true면 기존 대화를 새 참여자에게도 공개한다 */
    val shareHistory: Boolean = false
)
