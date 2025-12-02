package com.tripmuse.dto.request

import java.time.LocalDateTime

data class MediaMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: LocalDateTime? = null
)

data class AnalyzeMediaRequest(
    val mediaInfoList: List<MediaInfo>
)

data class MediaInfo(
    val filename: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: LocalDateTime? = null
)
