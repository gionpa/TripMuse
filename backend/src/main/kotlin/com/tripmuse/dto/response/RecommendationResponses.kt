package com.tripmuse.dto.response

import java.time.LocalDate

data class RecommendationResponse(
    val recommendations: List<RecommendationItem>
)

data class RecommendationItem(
    val type: RecommendationType,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val mediaCount: Int,
    val previewFilenames: List<String>,
    val targetAlbumId: Long? = null,
    val targetAlbumTitle: String? = null
)

enum class RecommendationType {
    NEW_TRIP,           // 새로운 여행 발견 (앨범 생성 추천)
    ADD_TO_EXISTING     // 기존 앨범에 추가 추천
}
