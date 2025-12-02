package com.tripmuse.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import com.tripmuse.dto.response.MediaDetailResponse
import com.tripmuse.dto.response.MediaListResponse
import com.tripmuse.dto.response.MediaResponse
import com.tripmuse.exception.BadRequestException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.MediaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class MediaService(
    private val mediaRepository: MediaRepository,
    private val commentRepository: CommentRepository,
    private val albumService: AlbumService,
    private val storageService: StorageService
) {
    companion object {
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif")
        private val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm")
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
    fun uploadMedia(albumId: Long, userId: Long, file: MultipartFile): MediaResponse {
        val album = albumService.findAlbumByIdAndUserId(albumId, userId)

        val contentType = file.contentType ?: throw BadRequestException("Unknown file type")
        val mediaType = when {
            IMAGE_TYPES.contains(contentType) -> MediaType.IMAGE
            VIDEO_TYPES.contains(contentType) -> MediaType.VIDEO
            else -> throw BadRequestException("Unsupported file type: $contentType")
        }

        // Extract metadata
        val metadata = extractMetadata(file.inputStream, mediaType)

        // Store file
        val filePath = when (mediaType) {
            MediaType.IMAGE -> storageService.storeImage(file)
            MediaType.VIDEO -> storageService.storeVideo(file)
        }

        // Generate thumbnail for images
        val thumbnailPath = if (mediaType == MediaType.IMAGE) {
            storageService.generateImageThumbnail(filePath)
        } else {
            null
        }

        val media = Media(
            album = album,
            type = mediaType,
            filePath = filePath,
            thumbnailPath = thumbnailPath,
            originalFilename = file.originalFilename,
            fileSize = file.size,
            latitude = metadata.latitude,
            longitude = metadata.longitude,
            takenAt = metadata.takenAt
        )

        album.addMedia(media)
        val savedMedia = mediaRepository.save(media)

        // Auto-set album cover image if not set
        if (thumbnailPath != null) {
            albumService.updateCoverImageIfEmpty(albumId, "/media/files/$thumbnailPath")
        }

        return MediaResponse.from(savedMedia)
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

    private fun extractMetadata(inputStream: InputStream, mediaType: MediaType): ExtractedMetadata {
        if (mediaType != MediaType.IMAGE) {
            return ExtractedMetadata()
        }

        return try {
            val metadata = ImageMetadataReader.readMetadata(inputStream)

            var latitude: Double? = null
            var longitude: Double? = null
            var takenAt: LocalDateTime? = null

            // Extract GPS data
            metadata.getFirstDirectoryOfType(GpsDirectory::class.java)?.let { gpsDir ->
                gpsDir.geoLocation?.let { geo ->
                    latitude = geo.latitude
                    longitude = geo.longitude
                }
            }

            // Extract date taken
            metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.let { exifDir ->
                exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.let { date ->
                    takenAt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                }
            }

            ExtractedMetadata(latitude, longitude, takenAt)
        } catch (e: Exception) {
            ExtractedMetadata()
        }
    }

    private data class ExtractedMetadata(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val takenAt: LocalDateTime? = null
    )
}
