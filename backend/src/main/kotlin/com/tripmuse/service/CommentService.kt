package com.tripmuse.service

import com.tripmuse.domain.Comment
import com.tripmuse.dto.request.CreateCommentRequest
import com.tripmuse.dto.request.UpdateCommentRequest
import com.tripmuse.dto.response.CommentListResponse
import com.tripmuse.dto.response.CommentResponse
import com.tripmuse.exception.ForbiddenException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.CommentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommentService(
    private val commentRepository: CommentRepository,
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
}
