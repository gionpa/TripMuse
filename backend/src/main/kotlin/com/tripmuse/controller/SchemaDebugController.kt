package com.tripmuse.controller

import com.tripmuse.security.CustomUserDetails
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 스키마 상태 확인용 임시 엔드포인트. 컬럼 메타데이터만 반환하며 로그인이 필요하다.
 * 채팅 마이그레이션 확인이 끝나면 제거한다.
 */
@RestController
@RequestMapping("/api/v1/users/me/debug")
class SchemaDebugController(
    private val jdbcTemplate: JdbcTemplate,
    private val chatService: com.tripmuse.service.ChatService
) {

    /** 초대 실패 원인을 보기 위한 임시 프로브. 예외를 그대로 돌려준다 */
    @org.springframework.web.bind.annotation.PostMapping("/invite-probe/{roomId}")
    fun inviteProbe(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable roomId: Long,
        @org.springframework.web.bind.annotation.RequestBody request: com.tripmuse.dto.InviteMembersRequest
    ): ResponseEntity<Map<String, String>> {
        return try {
            chatService.inviteMembers(roomId, user.id, request)
            ResponseEntity.ok(mapOf("result" to "ok"))
        } catch (e: Throwable) {
            var root: Throwable = e
            while (root.cause != null && root.cause !== root) root = root.cause!!
            ResponseEntity.ok(
                mapOf(
                    "type" to e::class.java.name,
                    "message" to (e.message ?: ""),
                    "rootType" to root::class.java.name,
                    "rootMessage" to (root.message ?: ""),
                    "frame" to (e.stackTrace.firstOrNull { it.className.startsWith("com.tripmuse") }?.toString() ?: "")
                )
            )
        }
    }

    @GetMapping("/schema/{table}")
    fun tableSchema(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable table: String
    ): ResponseEntity<Map<String, Any>> {
        val safeTable = table.filter { it.isLetterOrDigit() || it == '_' }
        val columns = jdbcTemplate.query(
            """
            SELECT column_name, is_nullable, data_type, column_default
            FROM information_schema.columns
            WHERE table_name = ?
            ORDER BY ordinal_position
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "name" to rs.getString("column_name"),
                    "nullable" to rs.getString("is_nullable"),
                    "type" to rs.getString("data_type"),
                    "default" to (rs.getString("column_default") ?: "")
                )
            },
            safeTable
        )
        val orphanRooms = runCatching {
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM chat_rooms r
                WHERE NOT EXISTS (SELECT 1 FROM chat_room_members m WHERE m.room_id = r.id)
                """.trimIndent(),
                Int::class.java
            )
        }.getOrNull()

        return ResponseEntity.ok(
            mapOf(
                "table" to safeTable,
                "columns" to columns,
                "roomsWithoutMembers" to (orphanRooms ?: -1)
            )
        )
    }
}
