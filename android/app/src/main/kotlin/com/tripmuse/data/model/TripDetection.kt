package com.tripmuse.data.model

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 위치 정보가 포함된 미디어 아이템
 */
data class MediaWithLocation(
    val uri: Uri,
    val filename: String,
    val latitude: Double,
    val longitude: Double,
    val takenAt: LocalDateTime,
    val isVideo: Boolean
)

/**
 * 위치 기반으로 클러스터링된 미디어 그룹
 */
data class LocationCluster(
    val centerLat: Double,
    val centerLng: Double,
    val media: List<MediaWithLocation>,
    val startDate: LocalDate,
    val endDate: LocalDate
)

/**
 * 감지된 여행 정보
 */
data class DetectedTrip(
    val id: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val mediaUris: List<Uri>,
    val mediaCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val previewUris: List<Uri>,
    val suggestedTitle: String
)

/**
 * 사용자의 홈 위치 정보
 */
data class HomeLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val isAutoDetected: Boolean
)

/**
 * 무시된 여행 추천 정보 (7일 후 재표시)
 */
data class DismissedTrip(
    val tripId: String,
    val dismissedAt: Long
)
