package com.tripmuse.repository

import com.tripmuse.domain.CommentRead
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.Optional

interface CommentReadRepository : JpaRepository<CommentRead, Long> {
    fun findByUserIdAndMediaId(userId: Long, mediaId: Long): Optional<CommentRead>

    /**
     * 특정 미디어에서 마지막 읽은 시간 이후 작성된 댓글 수 조회
     * (앨범 소유자가 아닌 다른 사용자가 작성한 댓글만 카운트)
     */
    @Query("""
        SELECT COUNT(c) FROM Comment c
        WHERE c.media.id = :mediaId
        AND c.createdAt > :lastReadAt
        AND c.user.id != :userId
    """)
    fun countUnreadComments(mediaId: Long, userId: Long, lastReadAt: LocalDateTime): Long

    /**
     * 읽음 기록이 없는 경우 (처음 보는 미디어) 전체 댓글 수 조회
     * (앨범 소유자가 아닌 다른 사용자가 작성한 댓글만 카운트)
     */
    @Query("""
        SELECT COUNT(c) FROM Comment c
        WHERE c.media.id = :mediaId
        AND c.user.id != :userId
    """)
    fun countOtherUsersComments(mediaId: Long, userId: Long): Long

    /**
     * 앨범 내 모든 미디어의 읽지 않은 댓글 존재 여부 확인을 위한 배치 조회
     */
    @Query("""
        SELECT cr FROM CommentRead cr
        WHERE cr.user.id = :userId
        AND cr.media.id IN :mediaIds
    """)
    fun findByUserIdAndMediaIdIn(userId: Long, mediaIds: List<Long>): List<CommentRead>
}
