package com.tripmuse.controller

import com.tripmuse.dto.request.CreateCommentRequest
import com.tripmuse.dto.request.UpdateCommentRequest
import com.tripmuse.dto.response.CommentListResponse
import com.tripmuse.dto.response.CommentResponse
import com.tripmuse.service.CommentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class CommentController(
    private val commentService: CommentService
) {
    @GetMapping("/media/{mediaId}/comments")
    fun getComments(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<CommentListResponse> {
        val comments = commentService.getComments(mediaId, userId)
        return ResponseEntity.ok(comments)
    }

    @PostMapping("/media/{mediaId}/comments")
    fun createComment(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long,
        @Valid @RequestBody request: CreateCommentRequest
    ): ResponseEntity<CommentResponse> {
        val comment = commentService.createComment(mediaId, userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @PutMapping("/comments/{commentId}")
    fun updateComment(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: UpdateCommentRequest
    ): ResponseEntity<CommentResponse> {
        val comment = commentService.updateComment(commentId, userId, request)
        return ResponseEntity.ok(comment)
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable commentId: Long
    ): ResponseEntity<Void> {
        commentService.deleteComment(commentId, userId)
        return ResponseEntity.noContent().build()
    }
}
