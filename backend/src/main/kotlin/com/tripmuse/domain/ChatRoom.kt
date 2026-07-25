package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 1:1 채팅방. user1.id < user2.id로 정규화해 쌍당 한 방만 존재한다.
 * lastMessage* 는 방 목록 정렬/미리보기용 비정규화 필드.
 */
@Entity
@Table(
    name = "chat_rooms",
    uniqueConstraints = [UniqueConstraint(name = "uk_chat_room_pair", columnNames = ["user1_id", "user2_id"])],
    indexes = [
        Index(name = "idx_chat_rooms_user1", columnList = "user1_id"),
        Index(name = "idx_chat_rooms_user2", columnList = "user2_id")
    ]
)
class ChatRoom(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    val user1: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    val user2: User,

    var lastMessageAt: LocalDateTime? = null,

    @Column(length = 300)
    var lastMessagePreview: String? = null,

    @Column(nullable = false)
    var user1LastReadMessageId: Long = 0,

    @Column(nullable = false)
    var user2LastReadMessageId: Long = 0
) : BaseEntity() {

    fun isMember(userId: Long): Boolean = user1.id == userId || user2.id == userId

    fun otherUser(userId: Long): User = if (user1.id == userId) user2 else user1

    fun lastReadMessageIdOf(userId: Long): Long =
        if (user1.id == userId) user1LastReadMessageId else user2LastReadMessageId

    fun updateLastRead(userId: Long, messageId: Long) {
        if (user1.id == userId) {
            if (messageId > user1LastReadMessageId) user1LastReadMessageId = messageId
        } else {
            if (messageId > user2LastReadMessageId) user2LastReadMessageId = messageId
        }
    }
}
