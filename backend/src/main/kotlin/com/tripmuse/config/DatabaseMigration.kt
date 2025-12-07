package com.tripmuse.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Database migration runner to handle schema changes that Hibernate cannot auto-migrate.
 * This runs before Hibernate's ddl-auto=update.
 */
@Component
@Order(1)
class DatabaseMigration(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DatabaseMigration::class.java)

    override fun run(args: ApplicationArguments) {
        logger.info("Running database migrations...")

        try {
            // Check if visibility column exists
            val columnExists = checkColumnExists("albums", "visibility")

            if (!columnExists) {
                logger.info("Adding visibility column to albums table...")
                // Add column without NOT NULL first, set default value, then add constraint
                jdbcTemplate.execute("""
                    ALTER TABLE albums
                    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) DEFAULT 'PRIVATE'
                """.trimIndent())

                // Update any null values (shouldn't happen but just in case)
                jdbcTemplate.execute("""
                    UPDATE albums SET visibility = 'PRIVATE' WHERE visibility IS NULL
                """.trimIndent())

                // Now add NOT NULL constraint
                jdbcTemplate.execute("""
                    ALTER TABLE albums ALTER COLUMN visibility SET NOT NULL
                """.trimIndent())

                // Add check constraint
                jdbcTemplate.execute("""
                    ALTER TABLE albums
                    DROP CONSTRAINT IF EXISTS albums_visibility_check
                """.trimIndent())
                jdbcTemplate.execute("""
                    ALTER TABLE albums
                    ADD CONSTRAINT albums_visibility_check
                    CHECK (visibility IN ('PRIVATE', 'FRIENDS_ONLY', 'PUBLIC'))
                """.trimIndent())

                logger.info("Successfully added visibility column to albums table")
            } else {
                logger.info("visibility column already exists, skipping migration")

                // Ensure no null values exist
                jdbcTemplate.execute("""
                    UPDATE albums SET visibility = 'PRIVATE' WHERE visibility IS NULL
                """.trimIndent())
            }
        } catch (e: Exception) {
            logger.warn("Migration warning (may be safe to ignore): ${e.message}")
        }

        logger.info("Database migrations completed")
    }

    private fun checkColumnExists(table: String, column: String): Boolean {
        return try {
            val count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
            """.trimIndent(), Int::class.java, table, column)
            (count ?: 0) > 0
        } catch (e: Exception) {
            logger.warn("Could not check column existence: ${e.message}")
            false
        }
    }
}
