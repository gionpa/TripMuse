package com.tripmuse.dto.response

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import com.tripmuse.domain.UploadStatus
import java.time.LocalDateTime

data class MediaResponse(
    val id: Long,
    val type: MediaType,
    val uploadStatus: UploadStatus,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val takenAt: LocalDateTime?,
    val isCover: Boolean,
    val hasUnreadComments: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(
            media: Media,
            locationName: String? = null,
            hasUnreadComments: Boolean = false,
            baseUrl: String = "/media/files"
        ): MediaResponse {
            return MediaResponse(
                id = media.id,
                type = media.type,
                uploadStatus = media.uploadStatus,
                filePath = media.filePath,
                fileUrl = "$baseUrl/${media.filePath}",
                thumbnailPath = media.thumbnailPath,
                thumbnailUrl = media.thumbnailPath?.let { "$baseUrl/$it" },
                originalFilename = media.originalFilename,
                fileSize = media.fileSize,
                latitude = media.latitude,
                longitude = media.longitude,
                locationName = locationName,
                takenAt = media.takenAt,
                isCover = media.isCover,
                hasUnreadComments = hasUnreadComments,
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
    val uploadStatus: UploadStatus,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val takenAt: LocalDateTime?,
    val isCover: Boolean,
    val memo: MemoResponse?,
    val commentCount: Long,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(media: Media, commentCount: Long, locationName: String? = null, baseUrl: String = "/media/files"): MediaDetailResponse {
            return MediaDetailResponse(
                id = media.id,
                type = media.type,
                uploadStatus = media.uploadStatus,
                filePath = media.filePath,
                fileUrl = "$baseUrl/${media.filePath}",
                thumbnailPath = media.thumbnailPath,
                thumbnailUrl = media.thumbnailPath?.let { "$baseUrl/$it" },
                originalFilename = media.originalFilename,
                fileSize = media.fileSize,
                latitude = media.latitude,
                longitude = media.longitude,
                locationName = locationName,
                takenAt = media.takenAt,
                isCover = media.isCover,
                memo = media.memo?.let { MemoResponse.from(it) },
                commentCount = commentCount,
                createdAt = media.createdAt
            )
        }
    }
}
