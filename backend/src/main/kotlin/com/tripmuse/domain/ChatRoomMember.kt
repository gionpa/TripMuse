package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 채팅방 참여자. 읽음 위치·입력중·이력 공개 범위를 참여자별로 들고 있다.
 */
@Entity
@Table(
    name = "chat_room_members",
    uniqueConstraints = [UniqueConstraint(name = "uk_chat_room_member", columnNames = ["room_id", "user_id"])],
    indexes = [
        Index(name = "idx_chat_room_members_user", columnList = "user_id"),
        Index(name = "idx_chat_room_members_room", columnList = "room_id"),
        // 안읽음 집계는 "내 활성 멤버십"을 방과 조인하므로 user_id, active, room_id로 커버한다
        Index(name = "idx_chat_room_members_user_active_room", columnList = "user_id, active, room_id")
    ]
)
class ChatRoomMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var lastReadMessageId: Long = 0,

    var typingAt: LocalDateTime? = null,

    /** 메타버스 스테이지의 최근 감정 표현 (짧게 유효) */
    @Column(length = 20)
    var emotion: String? = null,

    var emotionAt: LocalDateTime? = null,

    /**
     * 이 값보다 큰 ID의 메시지만 이 참여자에게 보인다.
     * 초대 시 이전 대화를 공개하면 0, 공개하지 않으면 초대 시점의 마지막 메시지 ID.
     */
    @Column(nullable = false)
    var visibleFromMessageId: Long = 0,

    /** 나가면 false. 이력과 읽음 위치는 남겨 둔다 */
    @Column(nullable = false)
    var active: Boolean = true
) : BaseEntity() {

    fun isTyping(): Boolean {
        val at = typingAt ?: return false
        return at.isAfter(LocalDateTime.now().minus(TYPING_TTL))
    }

    fun markTyping() {
        typingAt = LocalDateTime.now()
    }

    fun markEmotion(value: String) {
        emotion = value
        emotionAt = LocalDateTime.now()
    }

    /** 최근 EMOTION_TTL 이내에 표현한 감정만 유효 */
    fun currentEmotion(): String? {
        val at = emotionAt ?: return null
        return if (at.isAfter(LocalDateTime.now().minus(EMOTION_TTL))) emotion else null
    }

    fun updateLastRead(messageId: Long) {
        if (messageId > lastReadMessageId) lastReadMessageId = messageId
    }

    /** 이 참여자가 해당 메시지를 볼 수 있는지 */
    fun canSee(messageId: Long): Boolean = messageId > visibleFromMessageId

    companion object {
        /** 클라이언트가 입력 중 신호를 2~3초마다 보내므로 그보다 여유 있게 잡는다 */
        val TYPING_TTL: java.time.Duration = java.time.Duration.ofSeconds(6)

        /** 감정은 3초 폴링에 최소 한 번은 실려 전달되도록 여유 있게 */
        val EMOTION_TTL: java.time.Duration = java.time.Duration.ofSeconds(10)
    }
}
