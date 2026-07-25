package com.tripmuse.data.model

data class UpdateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null
)

data class FriendLocation(
    val friendId: Long,
    val nickname: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val recordedAt: String?
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/**
 * 좌표가 국내(네이버 지도 상세 데이터 제공 범위)인지 판별한다.
 * 국내면 네이버 지도, 해외면 구글 지도를 사용한다.
 */
object MapRegion {
    private const val KOREA_LAT_MIN = 33.0
    private const val KOREA_LAT_MAX = 38.65
    private const val KOREA_LON_MIN = 124.5
    private const val KOREA_LON_MAX = 132.0

    fun isDomestic(latitude: Double, longitude: Double): Boolean =
        latitude in KOREA_LAT_MIN..KOREA_LAT_MAX && longitude in KOREA_LON_MIN..KOREA_LON_MAX
}
