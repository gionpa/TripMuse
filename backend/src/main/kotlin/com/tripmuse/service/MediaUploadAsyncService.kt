package com.tripmuse.service

import com.tripmuse.domain.MediaType
import com.tripmuse.repository.MediaRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class MediaUploadAsyncService(
    private val storageService: StorageService,
    private val mediaRepository: MediaRepository,
    private val albumService: AlbumService,
    private val cacheManager: CacheManager?
) {

    private val logger = LoggerFactory.getLogger(MediaUploadAsyncService::class.java)

    @Async("mediaUploadExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
                        mediaRepository.saveAndFlush(media)
                        registerCacheEvictAfterCommit()
                        return
                    }
                    storageService.storeImageBytesAtPath(fileBytes, media.filePath)
                }
                MediaType.VIDEO -> {
                    if (!alreadyStored) {
                        if (fileBytes == null) {
                            logger.warn("Video bytes missing for mediaId=$mediaId and alreadyStored=false")
                            media.markUploadFailed()
                            mediaRepository.saveAndFlush(media)
                            registerCacheEvictAfterCommit()
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
            mediaRepository.saveAndFlush(media)
            logger.info("Async upload completed mediaId=$mediaId, filePath=${media.filePath}, thumbnail=$thumbnailPath, status=${media.uploadStatus}")

            // Register cache eviction AFTER transaction commits
            registerCacheEvictAfterCommit()

            if (thumbnailPath != null) {
                albumService.updateCoverImageIfEmpty(albumId, "/media/files/$thumbnailPath")
            }
        } catch (e: Exception) {
            logger.error("Failed to process async upload for mediaId=$mediaId, filename=$originalFilename", e)
            try {
                mediaRepository.findById(mediaId).ifPresent {
                    it.markUploadFailed()
                    mediaRepository.saveAndFlush(it)
                }
                registerCacheEvictAfterCommit()
            } catch (ex: Exception) {
                logger.error("Failed to mark media as failed: $mediaId", ex)
            }
        }
    }

    private fun registerCacheEvictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    evictMediaCaches()
                }
            })
        } else {
            // If no transaction sync is active, evict immediately
            evictMediaCaches()
        }
    }

    private fun evictMediaCaches() {
        try {
            cacheManager?.getCache("albumMedia")?.clear()
            cacheManager?.getCache("mediaDetail")?.clear()
            logger.info("Media caches evicted after transaction commit")
        } catch (e: Exception) {
            logger.warn("Failed to evict media caches", e)
        }
    }
}
