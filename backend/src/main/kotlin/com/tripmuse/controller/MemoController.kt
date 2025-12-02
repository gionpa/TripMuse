package com.tripmuse.controller

import com.tripmuse.dto.request.UpdateMemoRequest
import com.tripmuse.dto.response.MemoResponse
import com.tripmuse.service.MemoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/media/{mediaId}/memo")
class MemoController(
    private val memoService: MemoService
) {
    @GetMapping
    fun getMemo(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<MemoResponse?> {
        val memo = memoService.getMemo(mediaId, userId)
        return ResponseEntity.ok(memo)
    }

    @PutMapping
    fun createOrUpdateMemo(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long,
        @RequestBody request: UpdateMemoRequest
    ): ResponseEntity<MemoResponse> {
        val memo = memoService.createOrUpdateMemo(mediaId, userId, request)
        return ResponseEntity.ok(memo)
    }

    @DeleteMapping
    fun deleteMemo(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<Void> {
        memoService.deleteMemo(mediaId, userId)
        return ResponseEntity.noContent().build()
    }
}
