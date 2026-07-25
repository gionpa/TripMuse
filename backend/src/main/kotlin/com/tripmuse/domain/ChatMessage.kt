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
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String
) : BaseEntity()
