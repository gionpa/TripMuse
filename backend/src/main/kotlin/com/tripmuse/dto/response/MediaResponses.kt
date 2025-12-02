package com.tripmuse.dto.response

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import java.time.LocalDateTime

data class MediaResponse(
    val id: Long,
    val type: MediaType,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: LocalDateTime?,
    val isCover: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(media: Media, baseUrl: String = "/media/files"): MediaResponse {
            return MediaResponse(
                id = media.id,
                type = media.type,
                filePath = media.filePath,
                fileUrl = "$baseUrl/${media.filePath}",
                thumbnailPath = media.thumbnailPath,
                thumbnailUrl = media.thumbnailPath?.let { "$baseUrl/$it" },
                originalFilename = media.originalFilename,
                fileSize = media.fileSize,
                latitude = media.latitude,
                longitude = media.longitude,
                takenAt = media.takenAt,
                isCover = media.isCover,
                createdAt = media.createdAt
            )
        }
    }
}

data class MediaListResponse(
    val media: List<MediaResponse>
)

data class MediaDetailResponse(
    val id: Long,
    val type: MediaType,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: LocalDateTime?,
    val isCover: Boolean,
    val memo: MemoResponse?,
    val commentCount: Long,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(media: Media, commentCount: Long, baseUrl: String = "/media/files"): MediaDetailResponse {
            return MediaDetailResponse(
                id = media.id,
                type = media.type,
                filePath = media.filePath,
                fileUrl = "$baseUrl/${media.filePath}",
                thumbnailPath = media.thumbnailPath,
                thumbnailUrl = media.thumbnailPath?.let { "$baseUrl/$it" },
                originalFilename = media.originalFilename,
                fileSize = media.fileSize,
                latitude = media.latitude,
                longitude = media.longitude,
                takenAt = media.takenAt,
                isCover = media.isCover,
                memo = media.memo?.let { MemoResponse.from(it) },
                commentCount = commentCount,
                createdAt = media.createdAt
            )
        }
    }
}
