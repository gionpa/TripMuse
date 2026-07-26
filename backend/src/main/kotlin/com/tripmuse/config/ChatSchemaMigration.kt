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

        // 2) type이 비어 있는 기존 방은 1:1로 표시
        runQuietly(jdbcTemplate, "UPDATE chat_rooms SET type = 'DIRECT' WHERE type IS NULL")

        // 3) 참여자 백필 + 읽음 위치 보정 (이미 있는 행도 lastRead가 밀려 있으면 채운다)
        if (columnExists(jdbcTemplate, "chat_rooms", "user1_id")) {
            val changed = backfillMembers(jdbcTemplate)
            if (changed > 0) {
                logger.info("Chat migration: backfilled/updated $changed chat_room_members rows")
            }
        }

        // 4) 레거시 참여자 컬럼 정리.
        //    엔티티에서 빠졌으므로 NOT NULL이 남아 있으면 새 방 INSERT가 실패한다.
        //    NULL 허용으로 못 바꾸면, 백필이 끝난 것을 확인한 뒤 컬럼을 제거한다.
        relaxOrDropLegacyColumns(jdbcTemplate)
    }

    private fun relaxOrDropLegacyColumns(jdbcTemplate: JdbcTemplate) {
        // 엔티티에서 user1/user2 필드가 사라졌으므로 해당 컬럼은 모두 레거시다.
        // 이름 규칙이 버전마다 달라(user1_id vs user1last_read_message_id) 하드코딩하지 않고 찾는다.
        val legacyColumns = findColumns(jdbcTemplate, "chat_rooms", listOf("user1%", "user2%"))
        if (legacyColumns.isEmpty()) return

        legacyColumns.forEach { column ->
            if (isNotNull(jdbcTemplate, "chat_rooms", column)) {
                runQuietly(jdbcTemplate, "ALTER TABLE chat_rooms ALTER COLUMN $column DROP NOT NULL")
            }
        }

        val stillBlocking = legacyColumns.filter { isNotNull(jdbcTemplate, "chat_rooms", it) }
        if (stillBlocking.isEmpty()) return

        if (!everyRoomHasMember(jdbcTemplate)) {
            logger.error(
                "Chat migration: $stillBlocking still NOT NULL but member backfill is incomplete — " +
                    "leaving columns alone. New chat rooms cannot be created until this is resolved."
            )
            return
        }

        // 참여자 정보가 chat_room_members로 모두 옮겨진 것을 확인했으므로 제거해도 안전하다
        logger.warn("Chat migration: dropping legacy columns $stillBlocking after verifying member backfill")
        stillBlocking.forEach { column ->
            runQuietly(jdbcTemplate, "ALTER TABLE chat_rooms DROP COLUMN IF EXISTS $column")
        }
    }

    private fun isNotNull(jdbcTemplate: JdbcTemplate, table: String, column: String): Boolean {
        val nullable = jdbcTemplate.query(
            "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            { rs, _ -> rs.getString("is_nullable") },
            table,
            column
        ).firstOrNull() ?: return false
        return nullable.equals("NO", ignoreCase = true)
    }

    /** 모든 방이 참여자 행을 갖고 있는지 (레거시 컬럼을 지워도 되는지 판단) */
    private fun everyRoomHasMember(jdbcTemplate: JdbcTemplate): Boolean {
        val orphanRooms = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM chat_rooms r
            WHERE NOT EXISTS (SELECT 1 FROM chat_room_members m WHERE m.room_id = r.id)
            """.trimIndent(),
            Int::class.java
        ) ?: 1
        return orphanRooms == 0
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

    /** 여러 패턴에 맞는 컬럼 이름을 모두 찾는다 */
    private fun findColumns(jdbcTemplate: JdbcTemplate, table: String, patterns: List<String>): List<String> =
        patterns.flatMap { pattern ->
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND column_name LIKE ?",
                String::class.java,
                table,
                pattern
            )
        }.distinct()

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
