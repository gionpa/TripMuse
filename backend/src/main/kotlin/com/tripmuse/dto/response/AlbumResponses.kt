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
    val owner: UserResponse,
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
                owner = UserResponse.from(album.user),
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
                owner = UserResponse.from(album.user),
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
    val updatedAt: LocalDateTime,
    /** 공유 링크가 살아 있는지 (소유자에게만 의미 있음) */
    val isShared: Boolean = false,
    /** 그 링크로 열람 권한을 얻은 사람 수 */
    val sharedViewerCount: Long = 0
) {
    companion object {
        // Use @Formula calculated mediaCount from Album entity
        fun from(
            album: Album,
            commentCount: Long,
            requestUserId: Long,
            sharedViewerCount: Long = 0
        ): AlbumDetailResponse {
            val isOwner = album.user.id == requestUserId
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
                isOwner = isOwner,
                createdAt = album.createdAt,
                updatedAt = album.updatedAt,
                // 공유 상태는 소유자에게만 노출한다
                isShared = isOwner && album.shareToken != null,
                sharedViewerCount = if (isOwner) sharedViewerCount else 0
            )
        }
    }
}
