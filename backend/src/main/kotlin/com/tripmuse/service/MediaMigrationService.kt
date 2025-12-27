package com.tripmuse.service

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import com.tripmuse.repository.MediaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files

/**
 * 기존 미디어 파일 마이그레이션 서비스
 * - 이미지: 압축 (최대 2048px, JPEG 85%) + 썸네일 생성
 * - 동영상: 썸네일 생성
 */
@Service
class MediaMigrationService(
    private val mediaRepository: MediaRepository,
    private val storageService: StorageService
) {
    private val logger = LoggerFactory.getLogger(MediaMigrationService::class.java)

    data class MigrationResult(
        val totalProcessed: Int,
        val imagesCompressed: Int,
        val thumbnailsGenerated: Int,
        val errors: Int,
        val totalBytesSaved: Long
    )

    /**
     * 모든 미디어 파일 마이그레이션 실행
     */
    @Transactional
    fun migrateAllMedia(): MigrationResult {
        logger.info("Starting media migration...")

        val allMedia = mediaRepository.findAll()
        var imagesCompressed = 0
        var thumbnailsGenerated = 0
        var errors = 0
        var totalBytesSaved = 0L

        for (media in allMedia) {
            try {
                val result = migrateMedia(media)
                if (result.compressed) {
                    imagesCompressed++
                    totalBytesSaved += result.bytesSaved
                }
                if (result.thumbnailGenerated) {
                    thumbnailsGenerated++
                }
            } catch (e: Exception) {
                logger.error("Failed to migrate media id=${media.id}: ${e.message}", e)
                errors++
            }
        }

        val result = MigrationResult(
            totalProcessed = allMedia.size,
            imagesCompressed = imagesCompressed,
            thumbnailsGenerated = thumbnailsGenerated,
            errors = errors,
            totalBytesSaved = totalBytesSaved
        )

        logger.info("Migration completed: $result")
        return result
    }

    private data class MediaMigrationResult(
        val compressed: Boolean,
        val thumbnailGenerated: Boolean,
        val bytesSaved: Long
    )

    private fun migrateMedia(media: Media): MediaMigrationResult {
        var compressed = false
        var thumbnailGenerated = false
        var bytesSaved = 0L

        val filePath = storageService.getFullPath(media.filePath)
        if (!Files.exists(filePath)) {
            logger.warn("File not found for media id=${media.id}: ${media.filePath}")
            return MediaMigrationResult(false, false, 0)
        }

        when (media.type) {
            MediaType.IMAGE -> {
                // 1. 이미지 압축 (원본 파일 덮어쓰기)
                val originalSize = Files.size(filePath)
                val fileBytes = Files.readAllBytes(filePath)

                val compressedSize = storageService.compressImageInPlace(media.filePath, fileBytes)
                if (compressedSize < originalSize) {
                    bytesSaved = originalSize - compressedSize
                    media.fileSize = compressedSize
                    compressed = true
                    logger.info("Image compressed: id=${media.id}, ${originalSize/1024}KB -> ${compressedSize/1024}KB")
                }

                // 2. 썸네일이 없으면 생성
                if (media.thumbnailPath == null) {
                    val thumbnailPath = storageService.generateImageThumbnail(media.filePath)
                    if (thumbnailPath != null) {
                        media.thumbnailPath = thumbnailPath
                        thumbnailGenerated = true
                        logger.info("Thumbnail generated for image id=${media.id}: $thumbnailPath")
                    }
                }
            }

            MediaType.VIDEO -> {
                // 동영상은 썸네일만 생성 (압축은 복잡하고 시간이 오래 걸려서 스킵)
                if (media.thumbnailPath == null) {
                    val thumbnailPath = storageService.generateVideoThumbnail(media.filePath)
                    if (thumbnailPath != null) {
                        media.thumbnailPath = thumbnailPath
                        thumbnailGenerated = true
                        logger.info("Thumbnail generated for video id=${media.id}: $thumbnailPath")
                    }
                }
            }
        }

        // DB 업데이트
        if (compressed || thumbnailGenerated) {
            mediaRepository.save(media)
        }

        return MediaMigrationResult(compressed, thumbnailGenerated, bytesSaved)
    }
}
