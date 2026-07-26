package com.tripmuse.controller

import com.tripmuse.repository.UserRepository
import com.tripmuse.service.MediaMigrationService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/admin")
// SecurityConfig에서도 막지만, URL 설정이 바뀌어도 뚫리지 않도록 컨트롤러에서 한 번 더 막는다
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val mediaMigrationService: MediaMigrationService,
    private val userRepository: UserRepository
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

    /**
     * 최근 활성 유저 조회
     * @param days 조회 기간 (기본 1일)
     */
    @GetMapping("/active-users")
    fun getActiveUsers(@RequestParam(defaultValue = "1") days: Int): ResponseEntity<List<ActiveUserInfo>> {
        val since = LocalDateTime.now().minusDays(days.toLong())
        val users = userRepository.findRecentActiveUsers(since)
        val result = users.map { user ->
            ActiveUserInfo(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                createdAt = user.createdAt
            )
        }
        return ResponseEntity.ok(result)
    }

    data class ActiveUserInfo(
        val id: Long,
        val email: String,
        val nickname: String,
        val createdAt: LocalDateTime
    )
}
