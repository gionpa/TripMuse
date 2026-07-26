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

    // user를 함께 로딩해, 방 목록에서 멤버 닉네임을 꺼낼 때 멤버마다 LAZY 조회가 나가지 않게 한다
    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.user WHERE m.room.id IN :roomIds AND m.active = true")
    fun findByRoomIdInAndActiveTrue(@Param("roomIds") roomIds: Collection<Long>): List<ChatRoomMember>

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

    /**
     * 한 사용자의 모든 방을 통틀어 안읽은 메시지 총합을 한 쿼리로 센다.
     * 방마다 멤버 조회 + count를 돌리던 것(1+2N)을 대체한다.
     * 각 방의 기준선은 max(내가 마지막 읽은 id, 내 이력 공개 시작 id).
     */
    @Query("""
        SELECT COUNT(m) FROM ChatMessage m, ChatRoomMember mem
        WHERE mem.user.id = :userId
          AND mem.active = true
          AND m.room = mem.room
          AND m.sender.id <> :userId
          AND (m.type IS NULL OR m.type <> com.tripmuse.domain.ChatMessageType.SYSTEM)
          AND m.id > CASE WHEN mem.lastReadMessageId > mem.visibleFromMessageId
                          THEN mem.lastReadMessageId ELSE mem.visibleFromMessageId END
    """)
    fun countTotalUnread(@Param("userId") userId: Long): Long

    /**
     * 여러 방의 방별 안읽음 수를 한 쿼리로. (방 목록 화면의 방별 배지용)
     * 반환: [roomId, unreadCount] 행들 — 안읽음이 0인 방은 빠질 수 있다.
     */
    @Query("""
        SELECT m.room.id, COUNT(m) FROM ChatMessage m, ChatRoomMember mem
        WHERE mem.user.id = :userId
          AND mem.active = true
          AND m.room = mem.room
          AND m.room.id IN :roomIds
          AND m.sender.id <> :userId
          AND (m.type IS NULL OR m.type <> com.tripmuse.domain.ChatMessageType.SYSTEM)
          AND m.id > CASE WHEN mem.lastReadMessageId > mem.visibleFromMessageId
                          THEN mem.lastReadMessageId ELSE mem.visibleFromMessageId END
        GROUP BY m.room.id
    """)
    fun countUnreadByRoom(
        @Param("userId") userId: Long,
        @Param("roomIds") roomIds: Collection<Long>
    ): List<Array<Any>>
}
