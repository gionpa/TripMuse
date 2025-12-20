package com.tripmuse.service

import com.tripmuse.domain.Comment
import com.tripmuse.domain.CommentRead
import com.tripmuse.dto.request.CreateCommentRequest
import com.tripmuse.dto.request.UpdateCommentRequest
import com.tripmuse.dto.response.CommentListResponse
import com.tripmuse.dto.response.CommentResponse
import com.tripmuse.exception.ForbiddenException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.CommentReadRepository
import com.tripmuse.repository.CommentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CommentService(
    private val commentRepository: CommentRepository,
    private val commentReadRepository: CommentReadRepository,
    private val mediaService: MediaService,
    private val albumService: AlbumService,
    private val userService: UserService
) {
    fun getComments(mediaId: Long, userId: Long): CommentListResponse {
        val media = mediaService.findMediaById(mediaId)
        albumService.getAlbumDetail(media.album.id, userId)

        val comments = commentRepository.findByMediaIdOrderByCreatedAtAsc(mediaId)
        return CommentListResponse(comments.map { CommentResponse.from(it) })
    }

    @Transactional
    fun createComment(mediaId: Long, userId: Long, request: CreateCommentRequest): CommentResponse {
        val media = mediaService.findMediaById(mediaId)
        val user = userService.findUserById(userId)

        // Verify access to album (must be public or owned by user)
        albumService.getAlbumDetail(media.album.id, userId)

        val comment = Comment(
            media = media,
            user = user,
            content = request.content
        )

        val savedComment = commentRepository.save(comment)
        return CommentResponse.from(savedComment)
    }

    @Transactional
    fun updateComment(commentId: Long, userId: Long, request: UpdateCommentRequest): CommentResponse {
        val comment = findCommentById(commentId)

        if (comment.user.id != userId) {
            throw ForbiddenException("You can only edit your own comments")
        }

        comment.updateContent(request.content)
        return CommentResponse.from(comment)
    }

    @Transactional
    fun deleteComment(commentId: Long, userId: Long) {
        val comment = findCommentById(commentId)

        // Allow deletion by comment owner or album owner
        val albumOwner = comment.media.album.user.id == userId
        val commentOwner = comment.user.id == userId

        if (!albumOwner && !commentOwner) {
            throw ForbiddenException("You can only delete your own comments or comments on your albums")
        }

        commentRepository.delete(comment)
    }

    private fun findCommentById(commentId: Long): Comment {
        return commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found: $commentId") }
    }

    /**
     * 미디어의 댓글을 읽음으로 표시
     * 미디어 상세 화면 진입 시 호출
     */
    @Transactional
    fun markCommentsAsRead(mediaId: Long, userId: Long) {
        val media = mediaService.findMediaById(mediaId)
        val user = userService.findUserById(userId)

        val existingRead = commentReadRepository.findByUserIdAndMediaId(userId, mediaId)

        if (existingRead.isPresent) {
            existingRead.get().updateLastReadAt()
        } else {
            val commentRead = CommentRead(
                user = user,
                media = media,
                lastReadAt = LocalDateTime.now()
            )
            commentReadRepository.save(commentRead)
        }
    }

    /**
     * 특정 미디어의 읽지 않은 댓글 수 조회
     */
    fun getUnreadCommentCount(mediaId: Long, userId: Long): Long {
        val readRecord = commentReadRepository.findByUserIdAndMediaId(userId, mediaId)

        return if (readRecord.isPresent) {
            commentReadRepository.countUnreadComments(mediaId, userId, readRecord.get().lastReadAt)
        } else {
            // 읽음 기록이 없는 경우 다른 사용자가 작성한 모든 댓글을 읽지 않은 것으로 처리
            commentReadRepository.countOtherUsersComments(mediaId, userId)
        }
    }

    /**
     * 여러 미디어의 읽지 않은 댓글 존재 여부를 배치로 조회
     * key: mediaId, value: 읽지 않은 댓글이 있는지 여부
     */
    fun getUnreadCommentStatusBatch(mediaIds: List<Long>, userId: Long): Map<Long, Boolean> {
        if (mediaIds.isEmpty()) return emptyMap()

        // 읽음 기록 조회
        val readRecords = commentReadRepository.findByUserIdAndMediaIdIn(userId, mediaIds)
        val readMap = readRecords.associateBy { it.media.id }

        // 각 미디어별 댓글 정보 조회
        val result = mutableMapOf<Long, Boolean>()
        for (mediaId in mediaIds) {
            val readRecord = readMap[mediaId]
            val hasUnread = if (readRecord != null) {
                commentReadRepository.countUnreadComments(mediaId, userId, readRecord.lastReadAt) > 0
            } else {
                commentReadRepository.countOtherUsersComments(mediaId, userId) > 0
            }
            result[mediaId] = hasUnread
        }

        return result
    }
}
