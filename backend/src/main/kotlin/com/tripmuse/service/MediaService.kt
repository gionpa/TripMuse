package com.tripmuse.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import com.tripmuse.domain.UploadStatus
import com.tripmuse.dto.response.MediaDetailResponse
import com.tripmuse.dto.response.MediaListResponse
import com.tripmuse.dto.response.MediaResponse
import com.tripmuse.exception.BadRequestException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.MediaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MediaService(
    private val mediaRepository: MediaRepository,
    private val commentRepository: CommentRepository,
    private val albumService: AlbumService,
    private val storageService: StorageService,
    private val mediaUploadAsyncService: MediaUploadAsyncService
) {
    private val logger = LoggerFactory.getLogger(MediaService::class.java)

    companion object {
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif")
        private val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm", "video/3gpp", "video/3gpp2", "video/mpeg")
    }

    fun getMediaByAlbum(albumId: Long, userId: Long, type: MediaType? = null): MediaListResponse {
        // Verify album access
        albumService.getAlbumDetail(albumId, userId)

        // Use Fetch Join to avoid N+1 queries
        val mediaList = if (type != null) {
            mediaRepository.findByAlbumIdAndTypeWithAlbumOrderByTakenAtDesc(albumId, type)
        } else {
            mediaRepository.findByAlbumIdWithAlbumOrderByTakenAtDesc(albumId)
        }

        return MediaListResponse(mediaList.map { MediaResponse.from(it) })
    }

    fun getMediaDetail(mediaId: Long, userId: Long): MediaDetailResponse {
        // Use Fetch Join to load album in single query
        val media = mediaRepository.findByIdWithAlbum(mediaId)
            ?: throw NotFoundException("Media not found: $mediaId")

        // Verify album access
        albumService.getAlbumDetail(media.album.id, userId)

        val commentCount = commentRepository.countByMediaId(mediaId)
        return MediaDetailResponse.from(media, commentCount)
    }

    @Transactional
    fun uploadMedia(
        albumId: Long,
        userId: Long,
        file: MultipartFile,
        clientLatitude: Double? = null,
        clientLongitude: Double? = null,
        clientTakenAt: String? = null
    ): MediaResponse {
        val album = albumService.findAlbumByIdAndUserId(albumId, userId)

        val contentType = file.contentType
        val mediaType = when {
            contentType != null && IMAGE_TYPES.contains(contentType) -> MediaType.IMAGE
            contentType != null && (VIDEO_TYPES.contains(contentType) || contentType.startsWith("video")) -> MediaType.VIDEO
            contentType != null && contentType.startsWith("image") -> MediaType.IMAGE
            else -> {
                // Fall back to extension
                val ext = (file.originalFilename ?: "").substringAfterLast(".", "").lowercase()
                val typeFromExt = when (ext) {
                    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" -> MediaType.IMAGE
                    "mp4", "mov", "avi", "webm", "3gp", "3gpp", "mpeg", "mpg" -> MediaType.VIDEO
                    else -> throw BadRequestException("Unsupported file type: ${contentType ?: "unknown"}")
                }
                logger.info("Inferred media type from extension '$ext' -> $typeFromExt for filename='${file.originalFilename}'")
                typeFromExt
            }
        }
        logger.info("Upload request albumId=$albumId, userId=$userId, filename='${file.originalFilename}', contentType=$contentType, resolvedType=$mediaType")

        // Extract metadata (images only) using bytes
        val fileBytes: ByteArray? = if (mediaType == MediaType.IMAGE) file.bytes else null
        val fileMetadata = if (mediaType == MediaType.IMAGE && fileBytes != null) {
            extractMetadata(fileBytes, mediaType)
        } else {
            ExtractedMetadata()
        }

        // Use client-provided metadata if available, otherwise use file metadata
        val finalLatitude = clientLatitude ?: fileMetadata.latitude
        val finalLongitude = clientLongitude ?: fileMetadata.longitude
        val finalTakenAt = clientTakenAt?.let { parseClientDateTime(it) } ?: fileMetadata.takenAt

        logger.info("Final metadata - Client: lat=$clientLatitude, lon=$clientLongitude, takenAt=$clientTakenAt")
        logger.info("Final metadata - File: lat=${fileMetadata.latitude}, lon=${fileMetadata.longitude}, takenAt=${fileMetadata.takenAt}")
        logger.info("Final metadata - Used: lat=$finalLatitude, lon=$finalLongitude, takenAt=$finalTakenAt")

        val filePath = generateRelativePath(mediaType, file.originalFilename)

        // For videos, stream directly to disk to avoid OOM
        var alreadyStored = false
        if (mediaType == MediaType.VIDEO) {
            storageService.storeMultipartFileAtPath(file, filePath)
            alreadyStored = true
        }

        val media = Media(
            album = album,
            type = mediaType,
            uploadStatus = UploadStatus.PROCESSING,
            filePath = filePath,
            originalFilename = file.originalFilename,
            fileSize = file.size,
            latitude = finalLatitude,
            longitude = finalLongitude,
            takenAt = finalTakenAt
        )

        album.addMedia(media)
        val savedMedia = mediaRepository.save(media)
        logger.info("Media saved id=${savedMedia.id}, type=${savedMedia.type}, filePath=${savedMedia.filePath}")

        // 트랜잭션 커밋 후 비동기로 파일 업로드 및 썸네일 생성
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    mediaUploadAsyncService.processUploadAsync(
                        mediaId = savedMedia.id,
                        albumId = albumId,
                        mediaType = mediaType,
                        fileBytes = fileBytes,
                        originalFilename = file.originalFilename,
                        alreadyStored = alreadyStored
                    )
                }
            })
        } else {
            mediaUploadAsyncService.processUploadAsync(
                mediaId = savedMedia.id,
                albumId = albumId,
                mediaType = mediaType,
                fileBytes = fileBytes,
                originalFilename = file.originalFilename,
                alreadyStored = alreadyStored
            )
        }

        return MediaResponse.from(savedMedia)
    }

    private fun parseClientDateTime(dateTimeString: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(dateTimeString)
        } catch (e: Exception) {
            logger.warn("Failed to parse client datetime: $dateTimeString", e)
            null
        }
    }

    @Transactional
    fun deleteMedia(mediaId: Long, userId: Long) {
        val media = findMediaById(mediaId)
        albumService.findAlbumByIdAndUserId(media.album.id, userId)

        // Delete file from storage
        storageService.delete(media.filePath)
        media.thumbnailPath?.let { storageService.delete(it) }

        mediaRepository.delete(media)
    }

    @Transactional
    fun setCoverImage(mediaId: Long, userId: Long): MediaResponse {
        val media = findMediaById(mediaId)
        val album = albumService.findAlbumByIdAndUserId(media.album.id, userId)

        // 기존 대표 이미지 해제
        mediaRepository.findByAlbumIdAndIsCoverTrue(album.id)?.let { currentCover ->
            currentCover.setAsCover(false)
        }

        // 새 대표 이미지 설정
        media.setAsCover(true)

        // 앨범 coverImageUrl도 업데이트
        val coverUrl = media.thumbnailPath?.let { "/media/files/$it" }
            ?: "/media/files/${media.filePath}"
        album.updateCoverImage(coverUrl)

        return MediaResponse.from(media)
    }

    fun findMediaById(mediaId: Long): Media {
        return mediaRepository.findById(mediaId)
            .orElseThrow { NotFoundException("Media not found: $mediaId") }
    }

    private fun extractMetadata(fileBytes: ByteArray, mediaType: MediaType): ExtractedMetadata {
        if (mediaType != MediaType.IMAGE) {
            logger.debug("Skipping metadata extraction for non-image type: $mediaType")
            return ExtractedMetadata()
        }

        return try {
            val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(fileBytes))
            logger.debug("EXIF directories found: ${metadata.directories.map { it.name }}")

            var latitude: Double? = null
            var longitude: Double? = null
            var takenAt: LocalDateTime? = null

            // Extract GPS data
            metadata.getFirstDirectoryOfType(GpsDirectory::class.java)?.let { gpsDir ->
                logger.debug("GPS Directory found with tags: ${gpsDir.tags.map { "${it.tagName}=${it.description}" }}")
                gpsDir.geoLocation?.let { geo ->
                    latitude = geo.latitude
                    longitude = geo.longitude
                    logger.info("GPS extracted: lat=$latitude, lon=$longitude")
                }
            } ?: run {
                logger.debug("No GPS Directory found in image")
            }

            // Extract date taken
            metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.let { exifDir ->
                exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.let { date ->
                    takenAt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    logger.info("Date taken extracted: $takenAt")
                }
            }

            logger.info("Metadata extraction complete: lat=$latitude, lon=$longitude, takenAt=$takenAt")
            ExtractedMetadata(latitude, longitude, takenAt)
        } catch (e: Exception) {
            logger.error("Failed to extract metadata: ${e.message}", e)
            ExtractedMetadata()
        }
    }

    private data class ExtractedMetadata(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val takenAt: LocalDateTime? = null
    )

    private fun generateRelativePath(mediaType: MediaType, originalFilename: String?): String {
        val extension = when {
            originalFilename?.contains(".") == true -> originalFilename.substringAfterLast(".")
            mediaType == MediaType.IMAGE -> "jpg"
            else -> "mp4"
        }
        val directory = if (mediaType == MediaType.IMAGE) "images" else "videos"
        return "$directory/${UUID.randomUUID()}.$extension"
    }
}
