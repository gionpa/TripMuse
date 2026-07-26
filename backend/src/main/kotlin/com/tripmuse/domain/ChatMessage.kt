package com.tripmuse.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "chat_messages",
    indexes = [Index(name = "idx_chat_messages_room_id", columnList = "room_id, id")]
)
class ChatMessage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    // 이모지 포함 유니코드 텍스트 (PostgreSQL TEXT, UTF-8)
    // 이미지 메시지의 경우 목록 미리보기용 문구가 들어간다
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    @org.hibernate.annotations.ColumnDefault("'TEXT'")
    val type: ChatMessageType? = ChatMessageType.TEXT,

    /** 이미지 메시지의 저장 경로 (예: chat/uuid.jpg) */
    @Column(length = 500)
    val imagePath: String? = null
) : BaseEntity()

enum class ChatMessageType {
    TEXT,
    IMAGE
}
