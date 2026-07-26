package com.tripmuse.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 1:1 전용이던 채팅방 구조를 참여자 테이블 기반으로 옮긴다.
 *
 * chat_rooms.user1_id / user2_id 로 저장돼 있던 참여자와 읽음 위치를 chat_room_members로
 * 백필하고, 두 사람당 방 하나를 강제했던 unique 제약을 푼다(그룹 전환 후 같은 두 사람이
 * 다시 1:1 방을 만들 수 있어야 한다).
 *
 * 모든 단계는 여러 번 실행해도 안전하도록 작성했다.
 */
@Configuration
class ChatSchemaMigration {

    private val logger = LoggerFactory.getLogger(ChatSchemaMigration::class.java)

    @Bean
    fun migrateChatRoomsToMembers(jdbcTemplate: JdbcTemplate) = ApplicationRunner {
        if (!tableExists(jdbcTemplate, "chat_rooms") || !tableExists(jdbcTemplate, "chat_room_members")) {
            logger.info("Chat migration skipped: tables not ready")
            return@ApplicationRunner
        }

        // 1) 두 사람당 방 하나를 강제했던 제약 해제
        runQuietly(jdbcTemplate, "ALTER TABLE chat_rooms DROP CONSTRAINT IF EXISTS uk_chat_room_pair")

        // 2) 레거시 컬럼은 더 이상 채우지 않으므로 NULL을 허용해야 새 방 INSERT가 통과한다
        if (columnExists(jdbcTemplate, "chat_rooms", "user1_id")) {
            runQuietly(jdbcTemplate, "ALTER TABLE chat_rooms ALTER COLUMN user1_id DROP NOT NULL")
            runQuietly(jdbcTemplate, "ALTER TABLE chat_rooms ALTER COLUMN user2_id DROP NOT NULL")
        }

        // 3) type이 비어 있는 기존 방은 1:1로 표시
        runQuietly(jdbcTemplate, "UPDATE chat_rooms SET type = 'DIRECT' WHERE type IS NULL")

        // 4) 참여자 백필 + 읽음 위치 보정 (이미 있는 행도 lastRead가 0이면 채운다)
        if (columnExists(jdbcTemplate, "chat_rooms", "user1_id")) {
            val changed = backfillMembers(jdbcTemplate)
            if (changed > 0) {
                logger.info("Chat migration: backfilled/updated $changed chat_room_members rows")
            }
        }
    }

    private fun backfillMembers(jdbcTemplate: JdbcTemplate): Int {
        var total = 0
        listOf("user1" to "user1_id", "user2" to "user2_id").forEach { (prefix, userColumn) ->
            // 레거시 읽음 컬럼 이름은 네이밍 전략에 따라 다를 수 있어 실제 이름을 찾아 쓴다
            val lastReadColumn = findColumn(jdbcTemplate, "chat_rooms", "$prefix%read%")
            val lastReadExpr = lastReadColumn?.let { "COALESCE(r.$it, 0)" } ?: "0"

            total += jdbcTemplate.update(
                """
                INSERT INTO chat_room_members
                    (room_id, user_id, last_read_message_id, visible_from_message_id, active, created_at, updated_at)
                SELECT r.id, r.$userColumn, $lastReadExpr, 0, true, NOW(), NOW()
                FROM chat_rooms r
                WHERE r.$userColumn IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM chat_room_members m
                    WHERE m.room_id = r.id AND m.user_id = r.$userColumn
                  )
                """.trimIndent()
            )

            // 이전 배포에서 0으로 들어간 읽음 위치를 레거시 값으로 되살린다
            if (lastReadColumn != null) {
                total += jdbcTemplate.update(
                    """
                    UPDATE chat_room_members m
                    SET last_read_message_id = r.$lastReadColumn
                    FROM chat_rooms r
                    WHERE m.room_id = r.id
                      AND m.user_id = r.$userColumn
                      AND r.$lastReadColumn IS NOT NULL
                      AND m.last_read_message_id < r.$lastReadColumn
                    """.trimIndent()
                )
            }
        }
        return total
    }

    /** 패턴에 맞는 컬럼 이름을 하나 찾는다 (없으면 null) */
    private fun findColumn(jdbcTemplate: JdbcTemplate, table: String, pattern: String): String? =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND column_name LIKE ?",
            String::class.java,
            table,
            pattern
        ).firstOrNull()

    private fun tableExists(jdbcTemplate: JdbcTemplate, table: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            Int::class.java,
            table
        ) != 0

    private fun columnExists(jdbcTemplate: JdbcTemplate, table: String, column: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Int::class.java,
            table,
            column
        ) != 0

    private fun runQuietly(jdbcTemplate: JdbcTemplate, sql: String) {
        try {
            jdbcTemplate.execute(sql)
        } catch (e: Exception) {
            logger.warn("Chat migration step skipped ($sql): ${e.message}")
        }
    }
}
