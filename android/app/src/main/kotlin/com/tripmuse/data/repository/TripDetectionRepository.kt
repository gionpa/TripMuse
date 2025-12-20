package com.tripmuse.data.repository

import android.content.Context
import android.net.Uri
import com.tripmuse.data.model.AlbumVisibility
import com.tripmuse.data.model.CreateAlbumRequest
import com.tripmuse.data.model.DetectedTrip
import com.tripmuse.data.model.HomeLocation
import com.tripmuse.data.preferences.TripDetectionPreferences
import com.tripmuse.data.service.TripDetectionService
import com.tripmuse.util.GeocodingUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripDetectionRepository @Inject constructor(
    private val tripDetectionService: TripDetectionService,
    private val tripDetectionPreferences: TripDetectionPreferences,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CACHE_DURATION_MS = 24L * 60 * 60 * 1000 // 24시간
    }

    private val detectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadMutex = Mutex()

    // 캐시된 여행 목록
    private var cachedTrips: List<DetectedTrip>? = null
    private var cacheTimestamp: Long = 0

    /**
     * 여행 감지 (캐시 포함)
     */
    suspend fun detectTrips(forceRefresh: Boolean = false): Result<List<DetectedTrip>> =
        withContext(Dispatchers.IO) {
            // 캐시 확인
            val currentTime = System.currentTimeMillis()
            if (!forceRefresh && cachedTrips != null && currentTime - cacheTimestamp < CACHE_DURATION_MS) {
                val dismissedIds = tripDetectionPreferences.dismissedTripIds.first()
                val filteredTrips = cachedTrips!!.filter { it.id !in dismissedIds }
                return@withContext Result.success(filteredTrips)
            }

            // 만료된 무시 항목 정리
            tripDetectionPreferences.clearExpiredDismissals()

            // 홈 위치 가져오기
            val homeLocation = tripDetectionPreferences.homeLocation.first()

            // 이미 업로드된 파일명 목록 가져오기
            val uploadedFilenames = getUploadedMediaFilenames()

            // 여행 감지 수행 (이미 업로드된 파일 제외)
            val result = tripDetectionService.detectTrips(homeLocation, uploadedFilenames)

            result.onSuccess { trips ->
                // 캐시 업데이트
                cachedTrips = trips
                cacheTimestamp = currentTime
                tripDetectionPreferences.updateLastScanTime()

                // 무시된 여행 필터링
                val dismissedIds = tripDetectionPreferences.dismissedTripIds.first()
                val filteredTrips = trips.filter { it.id !in dismissedIds }
                return@withContext Result.success(filteredTrips)
            }

            result
        }

    /**
     * 서버에 업로드된 모든 미디어의 원본 파일명 목록 조회
     */
    private suspend fun getUploadedMediaFilenames(): Set<String> {
        val filenames = mutableSetOf<String>()

        try {
            // 모든 앨범 조회
            val albumsResult = albumRepository.getAlbums()
            if (albumsResult.isFailure) return emptySet()

            val albums = albumsResult.getOrThrow()

            // 각 앨범의 미디어에서 원본 파일명 수집
            for (album in albums) {
                val mediaResult = mediaRepository.getMediaByAlbum(album.id, size = 1000)
                if (mediaResult.isSuccess) {
                    val mediaList = mediaResult.getOrThrow()
                    mediaList.forEach { media ->
                        media.originalFilename?.let { filename ->
                            filenames.add(filename)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 에러 발생 시 빈 목록 반환 (필터링 없이 진행)
        }

        return filenames
    }

    /**
     * 추천 무시 (7일간 숨김)
     */
    suspend fun dismissTrip(tripId: String) {
        tripDetectionPreferences.dismissTrip(tripId)
    }

    /**
     * 홈 위치 수동 설정
     */
    suspend fun setHomeLocation(lat: Double, lng: Double) {
        val address = GeocodingUtil.getAddressFromCoordinates(context, lat, lng)
        val homeLocation = HomeLocation(
            latitude = lat,
            longitude = lng,
            address = address,
            isAutoDetected = false
        )
        tripDetectionPreferences.saveHomeLocation(homeLocation)
        // 캐시 무효화
        cachedTrips = null
    }

    /**
     * 홈 위치 가져오기
     */
    suspend fun getHomeLocation(): HomeLocation? {
        return tripDetectionPreferences.homeLocation.first()
    }

    /**
     * 감지된 여행으로 앨범 생성 및 미디어 업로드
     */
    suspend fun createAlbumFromTrip(
        trip: DetectedTrip,
        title: String,
        onProgress: (uploaded: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 1. 앨범 생성
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val createRequest = CreateAlbumRequest(
                title = title,
                location = trip.location,
                latitude = trip.latitude,
                longitude = trip.longitude,
                startDate = trip.startDate.format(dateFormatter),
                endDate = trip.endDate.format(dateFormatter),
                visibility = AlbumVisibility.PRIVATE
            )

            val albumResult = albumRepository.createAlbum(createRequest)
            if (albumResult.isFailure) {
                return@withContext Result.failure(
                    albumResult.exceptionOrNull() ?: Exception("앨범 생성 실패")
                )
            }

            val album = albumResult.getOrThrow()
            val albumId = album.id

            // 2. 미디어 순차 업로드
            var successCount = 0
            var failCount = 0
            val total = trip.mediaUris.size

            for ((index, uri) in trip.mediaUris.withIndex()) {
                uploadMutex.withLock {
                    val result = mediaRepository.uploadMedia(albumId, uri)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                    onProgress(index + 1, total)
                }
            }

            // 3. 무시 목록에서 해당 여행 제거 (앨범 생성 완료)
            // 캐시에서도 제거
            cachedTrips = cachedTrips?.filter { it.id != trip.id }

            Result.success(albumId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 캐시 강제 무효화
     */
    fun invalidateCache() {
        cachedTrips = null
        cacheTimestamp = 0
    }
}
