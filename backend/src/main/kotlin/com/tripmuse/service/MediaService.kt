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
import com.tripmuse.repository.CommentReadRepository
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.MediaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val commentReadRepository: CommentReadRepository,
    private val albumService: AlbumService,
    private val storageService: StorageService,
    private val geocodingService: GeocodingService,
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(MediaService::class.java)

    companion object {
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif")
        private val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm", "video/3gpp", "video/3gpp2", "video/mpeg")
    }

    @Cacheable(
        cacheNames = ["albumMedia"],
        key = "#userId + ':' + #albumId + ':' + (#type?.name() ?: 'ALL') + ':' + #page + ':' + #size",
        unless = "#result.media.isEmpty()"
    )
    fun getMediaByAlbum(
        albumId: Long,
        userId: Long,
        type: MediaType? = null,
        page: Int = 0,
        size: Int = 12
    ): MediaListResponse {
        // Verify album access
        albumService.getAlbumDetail(albumId, userId)

        val pageSize = size.coerceIn(1, 100)
        val pageRequest = org.springframework.data.domain.PageRequest.of(page, pageSize)

        val mediaPage = if (type != null) {
            mediaRepository.findByAlbumIdAndTypeOrderByTakenAtDesc(albumId, type, pageRequest)
        } else {
            mediaRepository.findByAlbumIdOrderByTakenAtDesc(albumId, pageRequest)
        }

        val mediaList = mediaPage.content

        // 읽지 않은 댓글 상태 배치 조회
        val mediaIds = mediaList.map { it.id }
        val unreadStatusMap = getUnreadCommentStatusBatch(mediaIds, userId)

        return MediaListResponse(mediaList.map { media ->
            val locationName = geocodingService.reverseGeocode(media.latitude, media.longitude)
            val hasUnread = unreadStatusMap[media.id] ?: false
            MediaResponse.from(media, locationName, hasUnread)
        })
    }

    /**
     * 여러 미디어의 읽지 않은 댓글 존재 여부를 배치로 조회
     */
    private fun getUnreadCommentStatusBatch(mediaIds: List<Long>, userId: Long): Map<Long, Boolean> {
        if (mediaIds.isEmpty()) return emptyMap()

        val readRecords = commentReadRepository.findByUserIdAndMediaIdIn(userId, mediaIds)
        val readMap = readRecords.associateBy { it.media.id }

        val result = mutableMapOf<Long, Boolean>()
        for (mediaId in mediaIds) {
            val readRecord = readMap[mediaId]
            val hasUnread = if (readRecord != null) {
                commentReadRepository.countUnreadComments(mediaId, userId, readRecord.lastReadAt) > 0
            } else {
                commentReadRepository.countOtherUsersComments(mediaId, userId) > 0
            }
            result[mediaId] = hasUnread
        }

        return result
    }

    @Cacheable(
        cacheNames = ["mediaDetail"],
        key = "#userId + ':' + #mediaId"
    )
    fun getMediaDetail(mediaId: Long, userId: Long): MediaDetailResponse {
        // Use Fetch Join to load album in single query
        val media = mediaRepository.findByIdWithAlbum(mediaId)
            ?: throw NotFoundException("Media not found: $mediaId")

        // Verify album access
        albumService.getAlbumDetail(media.album.id, userId)

        val commentCount = commentRepository.countByMediaId(mediaId)
        val locationName = geocodingService.reverseGeocode(media.latitude, media.longitude)
        return MediaDetailResponse.from(media, commentCount, locationName)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["albumMedia"], allEntries = true),
        CacheEvict(cacheNames = ["mediaDetail"], allEntries = true)
    ])
    @Transactional
    fun uploadMedia(
        albumId: Long,
        userId: Long,
        file: MultipartFile,
        clientLatitude: Double? = null,
        clientLongitude: Double? = null,
        clientTakenAt: String? = null
    ): MediaResponse {
        // 저장공간 용량 체크
        if (!userService.canUpload(userId, file.size)) {
            val remaining = userService.getRemainingStorage(userId)
            val remainingMB = remaining / (1024.0 * 1024.0)
            throw BadRequestException("저장공간이 부족합니다. 남은 용량: ${String.format("%.1f", remainingMB)}MB")
        }

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

        // 중복 체크: 같은 앨범에 파일명+파일크기가 동일한 미디어가 있으면 업로드 거부
        val originalFilename = file.originalFilename
        if (originalFilename != null && file.size > 0) {
            val isDuplicate = mediaRepository.existsByAlbumIdAndOriginalFilenameAndFileSize(
                albumId, originalFilename, file.size
            )
            if (isDuplicate) {
                logger.info("Duplicate media detected: albumId=$albumId, filename=$originalFilename, size=${file.size}")
                throw BadRequestException("이미 같은 사진이 앨범에 존재합니다: $originalFilename")
            }
        }

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

        // 동기식 업로드: 파일 저장 및 썸네일 생성을 바로 처리
        // 이렇게 하면 API 응답 시점에 이미 COMPLETED 상태가 됨
        var thumbnailPath: String? = null
        var actualFileSize = file.size  // 압축 후 실제 파일 크기
        try {
            when (mediaType) {
                MediaType.IMAGE -> {
                    if (fileBytes != null) {
                        // 이미지 압축 저장 (최대 2048px, JPEG 85%)
                        actualFileSize = storageService.storeImageBytesAtPath(fileBytes, filePath)
                        thumbnailPath = storageService.generateImageThumbnail(filePath)
                    }
                }
                MediaType.VIDEO -> {
                    storageService.storeMultipartFileAtPath(file, filePath)
                    thumbnailPath = storageService.generateVideoThumbnail(filePath)
                }
            }
            logger.info("File stored and thumbnail generated: filePath=$filePath, thumbnailPath=$thumbnailPath, actualSize=$actualFileSize")
        } catch (e: Exception) {
            logger.error("Failed to store file or generate thumbnail for ${file.originalFilename}", e)
            // 썸네일 생성 실패해도 파일은 저장됨, 계속 진행
        }

        val media = Media(
            album = album,
            type = mediaType,
            uploadStatus = UploadStatus.COMPLETED,  // 동기 처리로 바로 COMPLETED
            filePath = filePath,
            thumbnailPath = thumbnailPath,
            originalFilename = file.originalFilename,
            fileSize = actualFileSize,  // 압축된 실제 파일 크기 저장
            latitude = finalLatitude,
            longitude = finalLongitude,
            takenAt = finalTakenAt
        )

        album.addMedia(media)
        val savedMedia = mediaRepository.save(media)
        logger.info("Media saved id=${savedMedia.id}, type=${savedMedia.type}, status=${savedMedia.uploadStatus}, filePath=${savedMedia.filePath}, thumbnailPath=${savedMedia.thumbnailPath}")

        // 앨범 커버 이미지가 없으면 설정
        if (thumbnailPath != null) {
            albumService.updateCoverImageIfEmpty(albumId, "/media/files/$thumbnailPath")
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

    @Caching(evict = [
        CacheEvict(cacheNames = ["albumMedia"], allEntries = true),
        CacheEvict(cacheNames = ["mediaDetail"], allEntries = true)
    ])
    @Transactional
    fun deleteMedia(mediaId: Long, userId: Long) {
        val media = findMediaById(mediaId)
        albumService.findAlbumByIdAndUserId(media.album.id, userId)

        // Delete file from storage
        storageService.delete(media.filePath)
        media.thumbnailPath?.let { storageService.delete(it) }

        mediaRepository.delete(media)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["albumMedia"], allEntries = true),
        CacheEvict(cacheNames = ["mediaDetail"], allEntries = true)
    ])
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
