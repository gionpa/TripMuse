package com.tripmuse.service

import com.tripmuse.domain.MediaType
import com.tripmuse.repository.MediaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaUploadAsyncService(
    private val storageService: StorageService,
    private val mediaRepository: MediaRepository,
    private val albumService: AlbumService
) {

    private val logger = LoggerFactory.getLogger(MediaUploadAsyncService::class.java)

    @Async("mediaUploadExecutor")
    @Transactional
    fun processUploadAsync(
        mediaId: Long,
        albumId: Long,
        mediaType: MediaType,
        fileBytes: ByteArray?,
        originalFilename: String?,
        alreadyStored: Boolean = false
    ) {
        try {
            logger.info("Async upload start mediaId=$mediaId, albumId=$albumId, type=$mediaType, filename=$originalFilename, bytes=${fileBytes?.size ?: -1}, alreadyStored=$alreadyStored")
            val media = mediaRepository.findById(mediaId).orElse(null) ?: run {
                logger.warn("Media not found for async upload: $mediaId")
                return
            }

            when (mediaType) {
                MediaType.IMAGE -> {
                    if (fileBytes == null) {
                        logger.warn("Image bytes missing for mediaId=$mediaId")
                        media.markUploadFailed()
                        return
                    }
                    storageService.storeImageBytesAtPath(fileBytes, media.filePath)
                }
                MediaType.VIDEO -> {
                    if (!alreadyStored) {
                        if (fileBytes == null) {
                            logger.warn("Video bytes missing for mediaId=$mediaId and alreadyStored=false")
                            media.markUploadFailed()
                            return
                        }
                        storageService.storeBytesAt(media.filePath, fileBytes)
                    }
                }
            }

            val thumbnailPath = when (mediaType) {
                MediaType.IMAGE -> storageService.generateImageThumbnail(media.filePath)
                MediaType.VIDEO -> storageService.generateVideoThumbnail(media.filePath)
            }

            media.markUploadCompleted(thumbnailPath)
            logger.info("Async upload completed mediaId=$mediaId, filePath=${media.filePath}, thumbnail=$thumbnailPath")

            if (thumbnailPath != null) {
                albumService.updateCoverImageIfEmpty(albumId, "/media/files/$thumbnailPath")
            }
        } catch (e: Exception) {
            logger.error("Failed to process async upload for mediaId=$mediaId, filename=$originalFilename", e)
            mediaRepository.findById(mediaId).ifPresent { it.markUploadFailed() }
        }
    }
}
