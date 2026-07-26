package com.tripmuse.repository

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatRoom
import com.tripmuse.domain.ChatRoomMember
import com.tripmuse.domain.ChatRoomType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {

    /**
     * 두 사람만 있는 DIRECT 방을 찾는다. 그룹으로 전환된 방은 제외되므로
     * 전환 후 다시 1:1 채팅을 시작하면 새 방이 만들어진다.
     */
    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.type = :directType
          AND (SELECT COUNT(m) FROM ChatRoomMember m WHERE m.room = r AND m.active = true) = 2
          AND EXISTS (SELECT 1 FROM ChatRoomMember m1 WHERE m1.room = r AND m1.user.id = :userId AND m1.active = true)
          AND EXISTS (SELECT 1 FROM ChatRoomMember m2 WHERE m2.room = r AND m2.user.id = :otherId AND m2.active = true)
        ORDER BY r.id DESC
    """)
    fun findDirectRooms(
        @Param("userId") userId: Long,
        @Param("otherId") otherId: Long,
        @Param("directType") directType: ChatRoomType = ChatRoomType.DIRECT
    ): List<ChatRoom>

    @Query("""
        SELECT m.room FROM ChatRoomMember m
        WHERE m.user.id = :userId AND m.active = true
        ORDER BY COALESCE(m.room.lastMessageAt, m.room.createdAt) DESC
    """)
    fun findAllByMember(@Param("userId") userId: Long): List<ChatRoom>
}

interface ChatRoomMemberRepository : JpaRepository<ChatRoomMember, Long> {

    fun findByRoomIdAndUserId(roomId: Long, userId: Long): ChatRoomMember?

    fun findByRoomIdAndActiveTrue(roomId: Long): List<ChatRoomMember>

    fun findByRoomIdInAndActiveTrue(roomIds: Collection<Long>): List<ChatRoomMember>

    fun countByRoomIdAndActiveTrue(roomId: Long): Long
}

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    /** 참여자가 볼 수 있는 범위(visibleFrom 이후)의 최신 페이지 */
    fun findTop50ByRoomIdAndIdGreaterThanOrderByIdDesc(roomId: Long, visibleFrom: Long): List<ChatMessage>

    /** 위로 스크롤: beforeId 이전이면서 visibleFrom 이후 */
    fun findTop50ByRoomIdAndIdLessThanAndIdGreaterThanOrderByIdDesc(
        roomId: Long,
        beforeId: Long,
        visibleFrom: Long
    ): List<ChatMessage>

    fun findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId: Long, afterId: Long): List<ChatMessage>

    /** 안읽음 수. 시스템 메시지와 내가 보낸 메시지는 세지 않는다 */
    @Query("""
        SELECT COUNT(m) FROM ChatMessage m
        WHERE m.room.id = :roomId
          AND m.id > :afterId
          AND m.sender.id <> :userId
          AND (m.type IS NULL OR m.type <> com.tripmuse.domain.ChatMessageType.SYSTEM)
    """)
    fun countUnread(
        @Param("roomId") roomId: Long,
        @Param("afterId") afterId: Long,
        @Param("userId") userId: Long
    ): Long

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM ChatMessage m WHERE m.room.id = :roomId")
    fun findLatestMessageId(@Param("roomId") roomId: Long): Long
}
