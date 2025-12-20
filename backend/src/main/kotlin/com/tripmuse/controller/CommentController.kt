package com.tripmuse.controller

import com.tripmuse.dto.request.CreateCommentRequest
import com.tripmuse.dto.request.UpdateCommentRequest
import com.tripmuse.dto.response.CommentListResponse
import com.tripmuse.dto.response.CommentResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.CommentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class CommentController(
    private val commentService: CommentService
) {
    @GetMapping("/media/{mediaId}/comments")
    fun getComments(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long
    ): ResponseEntity<CommentListResponse> {
        val comments = commentService.getComments(mediaId, user.id)
        return ResponseEntity.ok(comments)
    }

    @PostMapping("/media/{mediaId}/comments")
    fun createComment(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long,
        @Valid @RequestBody request: CreateCommentRequest
    ): ResponseEntity<CommentResponse> {
        val comment = commentService.createComment(mediaId, user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @PutMapping("/comments/{commentId}")
    fun updateComment(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: UpdateCommentRequest
    ): ResponseEntity<CommentResponse> {
        val comment = commentService.updateComment(commentId, user.id, request)
        return ResponseEntity.ok(comment)
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable commentId: Long
    ): ResponseEntity<Void> {
        commentService.deleteComment(commentId, user.id)
        return ResponseEntity.noContent().build()
    }

    /**
     * 미디어의 댓글을 읽음으로 표시
     * 미디어 상세 화면 진입 시 호출
     */
    @PostMapping("/media/{mediaId}/comments/read")
    fun markCommentsAsRead(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long
    ): ResponseEntity<Void> {
        commentService.markCommentsAsRead(mediaId, user.id)
        return ResponseEntity.ok().build()
    }
}
