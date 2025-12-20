package com.tripmuse.util

import android.content.Context
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeocodingUtil {

    /**
     * 좌표를 주소로 변환 (Reverse Geocoding)
     * @return "해운대구" 또는 "부산 해운대구" 형태의 주소
     */
    suspend fun getAddressFromCoordinates(
        context: Context,
        lat: Double,
        lng: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null

            val geocoder = Geocoder(context, Locale.KOREAN)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (addresses.isNullOrEmpty()) return@withContext null

            val address = addresses[0]
            val locality = address.locality           // 시/군/구 (예: 해운대구)
            val subLocality = address.subLocality     // 동/읍/면 (예: 우동)
            val adminArea = address.adminArea         // 시/도 (예: 부산광역시)

            // 간결한 주소 생성
            when {
                locality != null && adminArea != null -> {
                    val shortAdmin = adminArea
                        .replace("광역시", "")
                        .replace("특별시", "")
                        .replace("특별자치시", "")
                        .replace("특별자치도", "")
                    "$shortAdmin $locality"
                }
                locality != null -> locality
                subLocality != null -> subLocality
                adminArea != null -> adminArea
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 두 좌표 간의 거리 계산 (meters)
     */
    fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * 위치 기반 여행 제목 생성
     * @return "부산 해운대 여행" 형태
     */
    suspend fun generateTripTitle(
        context: Context,
        lat: Double,
        lng: Double
    ): String {
        val address = getAddressFromCoordinates(context, lat, lng)
        return if (address != null) {
            "$address 여행"
        } else {
            "새로운 여행"
        }
    }
}
