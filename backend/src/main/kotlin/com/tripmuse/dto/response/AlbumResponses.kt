package com.tripmuse.dto.response

import com.tripmuse.domain.Album
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
    val isPublic: Boolean,
    val mediaCount: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(album: Album, mediaCount: Long): AlbumResponse {
            return AlbumResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                isPublic = album.isPublic,
                mediaCount = mediaCount,
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
    val isPublic: Boolean,
    val mediaCount: Long,
    val commentCount: Long,
    val owner: UserResponse,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(album: Album, mediaCount: Long, commentCount: Long): AlbumDetailResponse {
            return AlbumDetailResponse(
                id = album.id,
                title = album.title,
                location = album.location,
                latitude = album.latitude,
                longitude = album.longitude,
                startDate = album.startDate,
                endDate = album.endDate,
                coverImageUrl = album.coverImageUrl,
                isPublic = album.isPublic,
                mediaCount = mediaCount,
                commentCount = commentCount,
                owner = UserResponse.from(album.user),
                createdAt = album.createdAt,
                updatedAt = album.updatedAt
            )
        }
    }
}
