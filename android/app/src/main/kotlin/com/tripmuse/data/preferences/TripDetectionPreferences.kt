package com.tripmuse.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tripmuse.data.model.HomeLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tripDetectionDataStore by preferencesDataStore(name = "trip_detection_prefs")

@Singleton
class TripDetectionPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_HOME_LAT = doublePreferencesKey("home_latitude")
        private val KEY_HOME_LNG = doublePreferencesKey("home_longitude")
        private val KEY_HOME_ADDRESS = stringPreferencesKey("home_address")
        private val KEY_HOME_AUTO_DETECTED = stringPreferencesKey("home_auto_detected")

        private val KEY_DISMISSED_TRIPS = stringSetPreferencesKey("dismissed_trips")
        private val KEY_LAST_SCAN_TIME = longPreferencesKey("last_scan_time")

        private const val DISMISS_DURATION_MS = 7L * 24 * 60 * 60 * 1000 // 7일
    }

    /**
     * 홈 위치 Flow
     */
    val homeLocation: Flow<HomeLocation?> = context.tripDetectionDataStore.data.map { prefs ->
        val lat = prefs[KEY_HOME_LAT]
        val lng = prefs[KEY_HOME_LNG]

        if (lat != null && lng != null) {
            HomeLocation(
                latitude = lat,
                longitude = lng,
                address = prefs[KEY_HOME_ADDRESS],
                isAutoDetected = prefs[KEY_HOME_AUTO_DETECTED] == "true"
            )
        } else {
            null
        }
    }

    /**
     * 홈 위치 저장
     */
    suspend fun saveHomeLocation(location: HomeLocation) {
        context.tripDetectionDataStore.edit { prefs ->
            prefs[KEY_HOME_LAT] = location.latitude
            prefs[KEY_HOME_LNG] = location.longitude
            location.address?.let { prefs[KEY_HOME_ADDRESS] = it }
            prefs[KEY_HOME_AUTO_DETECTED] = location.isAutoDetected.toString()
        }
    }

    /**
     * 무시된 여행 ID 목록 (형식: "tripId:timestamp")
     */
    val dismissedTripIds: Flow<Set<String>> = context.tripDetectionDataStore.data.map { prefs ->
        val dismissedSet = prefs[KEY_DISMISSED_TRIPS] ?: emptySet()
        val currentTime = System.currentTimeMillis()

        // 만료되지 않은 것만 반환 (tripId만 추출)
        dismissedSet.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val tripId = parts[0]
                val dismissedAt = parts[1].toLongOrNull() ?: 0L
                if (currentTime - dismissedAt < DISMISS_DURATION_MS) {
                    tripId
                } else {
                    null
                }
            } else {
                null
            }
        }.toSet()
    }

    /**
     * 여행 추천 무시 (7일간)
     */
    suspend fun dismissTrip(tripId: String) {
        context.tripDetectionDataStore.edit { prefs ->
            val currentSet = prefs[KEY_DISMISSED_TRIPS]?.toMutableSet() ?: mutableSetOf()
            val entry = "$tripId:${System.currentTimeMillis()}"
            currentSet.add(entry)
            prefs[KEY_DISMISSED_TRIPS] = currentSet
        }
    }

    /**
     * 만료된 무시 항목 정리
     */
    suspend fun clearExpiredDismissals() {
        context.tripDetectionDataStore.edit { prefs ->
            val currentSet = prefs[KEY_DISMISSED_TRIPS] ?: emptySet()
            val currentTime = System.currentTimeMillis()

            val validSet = currentSet.filter { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val dismissedAt = parts[1].toLongOrNull() ?: 0L
                    currentTime - dismissedAt < DISMISS_DURATION_MS
                } else {
                    false
                }
            }.toSet()

            prefs[KEY_DISMISSED_TRIPS] = validSet
        }
    }

    /**
     * 마지막 스캔 시간
     */
    val lastScanTime: Flow<Long?> = context.tripDetectionDataStore.data.map { prefs ->
        prefs[KEY_LAST_SCAN_TIME]
    }

    /**
     * 마지막 스캔 시간 업데이트
     */
    suspend fun updateLastScanTime() {
        context.tripDetectionDataStore.edit { prefs ->
            prefs[KEY_LAST_SCAN_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * 모든 설정 초기화
     */
    suspend fun clear() {
        context.tripDetectionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
