package com.tripmuse.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDateTime

data class UpdateLocationRequest(
    @field:Min(value = -90, message = "위도 범위를 벗어났습니다")
    @field:Max(value = 90, message = "위도 범위를 벗어났습니다")
    val latitude: Double,

    @field:Min(value = -180, message = "경도 범위를 벗어났습니다")
    @field:Max(value = 180, message = "경도 범위를 벗어났습니다")
    val longitude: Double,

    val accuracy: Float? = null
)

/**
 * 친구의 현재 위치. 아직 공유한 위치가 없으면 좌표가 null이다.
 */
data class FriendLocationResponse(
    val friendId: Long,
    val nickname: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val recordedAt: LocalDateTime?
)
