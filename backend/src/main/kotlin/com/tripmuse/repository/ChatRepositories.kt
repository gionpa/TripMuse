package com.tripmuse.repository

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatRoom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {

    fun findByUser1IdAndUser2Id(user1Id: Long, user2Id: Long): ChatRoom?

    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.user1.id = :userId OR r.user2.id = :userId
        ORDER BY COALESCE(r.lastMessageAt, r.createdAt) DESC
    """)
    fun findAllByMember(userId: Long): List<ChatRoom>
}

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    fun findTop50ByRoomIdOrderByIdDesc(roomId: Long): List<ChatMessage>

    fun findTop50ByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, beforeId: Long): List<ChatMessage>

    fun findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId: Long, afterId: Long): List<ChatMessage>

    fun countByRoomIdAndIdGreaterThanAndSenderIdNot(roomId: Long, messageId: Long, senderId: Long): Long

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM ChatMessage m WHERE m.room.id = :roomId")
    fun findLatestMessageId(roomId: Long): Long
}
