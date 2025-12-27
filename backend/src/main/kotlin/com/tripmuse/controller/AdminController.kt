package com.tripmuse.controller

import com.tripmuse.service.MediaMigrationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val mediaMigrationService: MediaMigrationService
) {

    /**
     * 기존 미디어 파일 마이그레이션
     * - 이미지: 압축 (최대 2048px, JPEG 85%) + 썸네일 생성
     * - 동영상: 썸네일 생성
     *
     * 주의: 시간이 오래 걸릴 수 있음
     */
    @PostMapping("/migrate-media")
    fun migrateMedia(): ResponseEntity<MediaMigrationService.MigrationResult> {
        val result = mediaMigrationService.migrateAllMedia()
        return ResponseEntity.ok(result)
    }
}
