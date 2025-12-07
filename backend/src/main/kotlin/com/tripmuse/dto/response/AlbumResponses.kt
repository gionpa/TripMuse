package com.tripmuse.dto.response

import com.tripmuse.domain.Album
import com.tripmuse.domain.AlbumVisibility
import java.time.LocalDate
import java.time.LocalDateTime

data class AlbumResponse(
    val id: Long,
    val title: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val coverImageUrl: String?,
    val visibility: AlbumVisibility,
    val mediaCount: Long,
    val isOwner: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(album: Album, mediaCount: Long, requestUserId: Long): AlbumResponse {
            return AlbumResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                visibility = album.visibility ?: AlbumVisibility.PRIVATE,
                mediaCount = mediaCount,
                isOwner = album.user.id == requestUserId,
                createdAt = album.createdAt,
                updatedAt = album.updatedAt
            )
        }

        // Use @Formula calculated mediaCount from Album entity
        fun from(album: Album, requestUserId: Long): AlbumResponse {
            return AlbumResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                visibility = album.visibility ?: AlbumVisibility.PRIVATE,
                mediaCount = album.mediaCount,
                isOwner = album.user.id == requestUserId,
                createdAt = album.createdAt,
                updatedAt = album.updatedAt
            )
        }
    }
}

data class AlbumListResponse(
    val albums: List<AlbumResponse>
)

data class AlbumDetailResponse(
    val id: Long,
    val title: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val coverImageUrl: String?,
    val visibility: AlbumVisibility,
    val mediaCount: Long,
    val commentCount: Long,
    val owner: UserResponse,
    val isOwner: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(album: Album, mediaCount: Long, commentCount: Long, requestUserId: Long): AlbumDetailResponse {
            return AlbumDetailResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                visibility = album.visibility ?: AlbumVisibility.PRIVATE,
                mediaCount = mediaCount,
                commentCount = commentCount,
                owner = UserResponse.from(album.user),
                isOwner = album.user.id == requestUserId,
                createdAt = album.createdAt,
                updatedAt = album.updatedAt
            )
        }

        // Use @Formula calculated mediaCount from Album entity
        fun from(album: Album, commentCount: Long, requestUserId: Long): AlbumDetailResponse {
            return AlbumDetailResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                visibility = album.visibility ?: AlbumVisibility.PRIVATE,
                mediaCount = album.mediaCount,
                commentCount = commentCount,
                owner = UserResponse.from(album.user),
                isOwner = album.user.id == requestUserId,
                createdAt = album.createdAt,
                updatedAt = album.updatedAt
            )
        }
    }
}
