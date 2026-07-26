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

        // 4) 참여자 백필 (이미 있는 행은 건너뛴다)
        if (columnExists(jdbcTemplate, "chat_rooms", "user1_id")) {
            val inserted = backfillMembers(jdbcTemplate)
            if (inserted > 0) {
                logger.info("Chat migration: backfilled $inserted chat_room_members rows")
            }
        }
    }

    private fun backfillMembers(jdbcTemplate: JdbcTemplate): Int {
        var total = 0
        // user1 / user2 각각을 참여자로 옮기고, 읽음 위치도 함께 가져온다
        listOf("user1_id" to "user1_last_read_message_id", "user2_id" to "user2_last_read_message_id")
            .forEach { (userColumn, lastReadColumn) ->
                val lastRead = if (columnExists(jdbcTemplate, "chat_rooms", lastReadColumn)) {
                    "COALESCE(r.$lastReadColumn, 0)"
                } else {
                    "0"
                }
                total += jdbcTemplate.update(
                    """
                    INSERT INTO chat_room_members
                        (room_id, user_id, last_read_message_id, visible_from_message_id, active, created_at, updated_at)
                    SELECT r.id, r.$userColumn, $lastRead, 0, true, NOW(), NOW()
                    FROM chat_rooms r
                    WHERE r.$userColumn IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM chat_room_members m
                        WHERE m.room_id = r.id AND m.user_id = r.$userColumn
                      )
                    """.trimIndent()
                )
            }
        return total
    }

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
