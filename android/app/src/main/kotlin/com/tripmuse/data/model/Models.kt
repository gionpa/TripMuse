package com.tripmuse.data.model

import com.google.gson.annotations.SerializedName

// Album Visibility
enum class AlbumVisibility {
    PRIVATE,       // 나만 보기
    FRIENDS_ONLY,  // 친구에게 공개
    PUBLIC         // 전체 공개
}

// User
data class User(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val createdAt: String,
    val stats: UserStats? = null
)

data class UserStats(
    val albumCount: Long,
    val imageCount: Long,
    val videoCount: Long
)

// Album
data class Album(
    val id: Long,
    val title: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: String?,
    val endDate: String?,
    val coverImageUrl: String?,
    val visibility: AlbumVisibility,
    val mediaCount: Long,
    val isOwner: Boolean = true,
    val createdAt: String,
    val updatedAt: String
)

data class AlbumDetail(
    val id: Long,
    val title: String,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: String?,
    val endDate: String?,
    val coverImageUrl: String?,
    val visibility: AlbumVisibility,
    val mediaCount: Long,
    val commentCount: Long,
    val owner: User,
    val isOwner: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

data class AlbumListResponse(
    val albums: List<Album>
)

// Media
enum class MediaType {
    IMAGE, VIDEO
}

enum class UploadStatus {
    PROCESSING, COMPLETED, FAILED
}

data class Media(
    val id: Long,
    val type: MediaType,
    val uploadStatus: UploadStatus = UploadStatus.COMPLETED,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val takenAt: String?,
    val isCover: Boolean = false,
    val hasUnreadComments: Boolean = false,
    val createdAt: String
)

data class MediaDetail(
    val id: Long,
    val type: MediaType,
    val uploadStatus: UploadStatus = UploadStatus.COMPLETED,
    val filePath: String,
    val fileUrl: String,
    val thumbnailPath: String?,
    val thumbnailUrl: String?,
    val originalFilename: String?,
    val fileSize: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val takenAt: String?,
    val isCover: Boolean = false,
    val memo: Memo?,
    val commentCount: Long,
    val createdAt: String
)

data class MediaListResponse(
    val media: List<Media>
)

// Memo
data class Memo(
    val id: Long,
    val content: String,
    val createdAt: String,
    val updatedAt: String
)

// Comment
data class Comment(
    val id: Long,
    val content: String,
    val user: User,
    val createdAt: String,
    val updatedAt: String
)

data class CommentListResponse(
    val comments: List<Comment>
)

// Recommendation
enum class RecommendationType {
    NEW_TRIP,
    ADD_TO_EXISTING
}

data class RecommendationItem(
    val type: RecommendationType,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: String?,
    val endDate: String?,
    val mediaCount: Int,
    val previewFilenames: List<String>,
    val targetAlbumId: Long?,
    val targetAlbumTitle: String?
)

data class RecommendationResponse(
    val recommendations: List<RecommendationItem>
)

// Requests
data class CreateAlbumRequest(
    val title: String,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val coverImageUrl: String? = null,
    val visibility: AlbumVisibility = AlbumVisibility.PRIVATE
)

data class UpdateAlbumRequest(
    val title: String,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val coverImageUrl: String? = null,
    val visibility: AlbumVisibility = AlbumVisibility.PRIVATE
)

data class ReorderAlbumsRequest(
    val albumIds: List<Long>
)

data class UpdateMemoRequest(
    val content: String
)

data class CreateCommentRequest(
    val content: String
)

data class UpdateCommentRequest(
    val content: String
)

data class MediaInfo(
    val filename: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null
)

data class AnalyzeMediaRequest(
    val mediaInfoList: List<MediaInfo>
)
