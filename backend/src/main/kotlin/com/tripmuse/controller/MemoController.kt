package com.tripmuse.controller

import com.tripmuse.dto.request.UpdateMemoRequest
import com.tripmuse.dto.response.MemoResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.MemoService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/media/{mediaId}/memo")
class MemoController(
    private val memoService: MemoService
) {
    @GetMapping
    fun getMemo(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long
    ): ResponseEntity<MemoResponse?> {
        val memo = memoService.getMemo(mediaId, user.id)
        return ResponseEntity.ok(memo)
    }

    @PutMapping
    fun createOrUpdateMemo(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long,
        @RequestBody request: UpdateMemoRequest
    ): ResponseEntity<MemoResponse> {
        val memo = memoService.createOrUpdateMemo(mediaId, user.id, request)
        return ResponseEntity.ok(memo)
    }

    @DeleteMapping
    fun deleteMemo(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable mediaId: Long
    ): ResponseEntity<Void> {
        memoService.deleteMemo(mediaId, user.id)
        return ResponseEntity.noContent().build()
    }
}
