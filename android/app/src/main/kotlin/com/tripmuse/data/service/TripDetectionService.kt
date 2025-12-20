package com.tripmuse.data.service

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.tripmuse.data.model.DetectedTrip
import com.tripmuse.data.model.HomeLocation
import com.tripmuse.data.model.LocationCluster
import com.tripmuse.data.model.MediaWithLocation
import com.tripmuse.util.GeocodingUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripDetectionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CLUSTER_RADIUS_METERS = 500f
        private const val HOME_DISTANCE_THRESHOLD_METERS = 5000f
        private const val MIN_MEDIA_COUNT_FOR_TRIP = 3
        private const val MAX_MEDIA_SCAN_COUNT = 1000
    }

    /**
     * 여행 감지 메인 함수
     * @param excludedFilenames 이미 앨범에 업로드된 파일명 목록 (제외 대상)
     */
    suspend fun detectTrips(
        homeLocation: HomeLocation?,
        excludedFilenames: Set<String> = emptySet(),
        days: Int = 30
    ): Result<List<DetectedTrip>> = withContext(Dispatchers.IO) {
        try {
            // 1. 최근 미디어 로드 (위치 정보 포함)
            val allMedia = getRecentMediaWithLocation(days)

            // 1-1. 이미 업로드된 미디어 제외
            val mediaList = if (excludedFilenames.isNotEmpty()) {
                allMedia.filter { media ->
                    media.filename !in excludedFilenames
                }
            } else {
                allMedia
            }

            if (mediaList.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 2. 홈 위치 결정 (없으면 자동 감지)
            val home = homeLocation ?: autoDetectHomeLocation(mediaList)

            if (home == null) {
                return@withContext Result.success(emptyList())
            }

            // 3. 홈에서 멀리 떨어진 미디어만 필터링
            val awayFromHome = mediaList.filter { media ->
                val distance = GeocodingUtil.calculateDistance(
                    home.latitude, home.longitude,
                    media.latitude, media.longitude
                )
                distance >= HOME_DISTANCE_THRESHOLD_METERS
            }

            if (awayFromHome.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 4. 위치 기반 클러스터링
            val clusters = clusterByLocation(awayFromHome)

            // 5. 최소 미디어 개수 필터링 및 DetectedTrip 변환
            val trips = clusters
                .filter { it.media.size >= MIN_MEDIA_COUNT_FOR_TRIP }
                .map { cluster -> clusterToDetectedTrip(cluster) }

            Result.success(trips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 최근 N일 내의 위치 정보가 있는 미디어 조회
     */
    suspend fun getRecentMediaWithLocation(days: Int = 30): List<MediaWithLocation> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<MediaWithLocation>()
            val cutoffTime = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)

            // 이미지 조회
            result.addAll(queryMediaWithLocation(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cutoffTime,
                isVideo = false
            ))

            // 비디오 조회
            result.addAll(queryMediaWithLocation(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cutoffTime,
                isVideo = true
            ))

            result.sortedByDescending { it.takenAt }
                .take(MAX_MEDIA_SCAN_COUNT)
        }

    private fun queryMediaWithLocation(
        uri: Uri,
        cutoffTime: Long,
        isVideo: Boolean
    ): List<MediaWithLocation> {
        val result = mutableListOf<MediaWithLocation>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME
        )

        val selection = "${MediaStore.MediaColumns.DATE_TAKEN} >= ?"
        val selectionArgs = arrayOf(cutoffTime.toString())
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext() && result.size < MAX_MEDIA_SCAN_COUNT) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateColumn)
                val filePath = cursor.getString(dataColumn)
                val displayName = cursor.getString(nameColumn) ?: ""

                // GPS 좌표 추출 시도
                val location = extractGpsFromFile(filePath)
                if (location != null) {
                    val contentUri = Uri.withAppendedPath(uri, id.toString())
                    val takenAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(dateTaken),
                        ZoneId.systemDefault()
                    )
                    result.add(
                        MediaWithLocation(
                            uri = contentUri,
                            filename = displayName,
                            latitude = location.first,
                            longitude = location.second,
                            takenAt = takenAt,
                            isVideo = isVideo
                        )
                    )
                }
            }
        }

        return result
    }

    private fun extractGpsFromFile(filePath: String?): Pair<Double, Double>? {
        if (filePath == null) return null

        return try {
            val exif = ExifInterface(filePath)
            val latLong = exif.latLong
            if (latLong != null && latLong[0] != 0.0 && latLong[1] != 0.0) {
                Pair(latLong[0], latLong[1])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 위치 기반 클러스터링 (DBSCAN 유사 알고리즘)
     */
    fun clusterByLocation(
        media: List<MediaWithLocation>,
        radiusMeters: Float = CLUSTER_RADIUS_METERS
    ): List<LocationCluster> {
        if (media.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<MediaWithLocation>>()
        val visited = mutableSetOf<MediaWithLocation>()

        for (item in media) {
            if (item in visited) continue

            // 새 클러스터 시작
            val cluster = mutableListOf(item)
            visited.add(item)

            // 같은 클러스터에 속하는 미디어 찾기
            for (other in media) {
                if (other in visited) continue

                val distance = GeocodingUtil.calculateDistance(
                    item.latitude, item.longitude,
                    other.latitude, other.longitude
                )

                if (distance <= radiusMeters) {
                    cluster.add(other)
                    visited.add(other)
                }
            }

            clusters.add(cluster)
        }

        // MutableList<MediaWithLocation> → LocationCluster 변환
        return clusters.map { clusterMedia ->
            val centerLat = clusterMedia.map { it.latitude }.average()
            val centerLng = clusterMedia.map { it.longitude }.average()
            val dates = clusterMedia.map { it.takenAt.toLocalDate() }

            LocationCluster(
                centerLat = centerLat,
                centerLng = centerLng,
                media = clusterMedia,
                startDate = dates.minOrNull() ?: LocalDate.now(),
                endDate = dates.maxOrNull() ?: LocalDate.now()
            )
        }
    }

    /**
     * 홈 위치 자동 감지 (가장 많이 촬영된 위치)
     */
    private fun autoDetectHomeLocation(media: List<MediaWithLocation>): HomeLocation? {
        if (media.isEmpty()) return null

        // 클러스터링 후 가장 큰 클러스터의 중심을 홈으로 설정
        val clusters = clusterByLocation(media, radiusMeters = 1000f)
        val largestCluster = clusters.maxByOrNull { it.media.size } ?: return null

        return HomeLocation(
            latitude = largestCluster.centerLat,
            longitude = largestCluster.centerLng,
            address = null,
            isAutoDetected = true
        )
    }

    /**
     * LocationCluster를 DetectedTrip으로 변환
     */
    private suspend fun clusterToDetectedTrip(cluster: LocationCluster): DetectedTrip {
        val location = GeocodingUtil.getAddressFromCoordinates(
            context,
            cluster.centerLat,
            cluster.centerLng
        ) ?: "알 수 없는 위치"

        val suggestedTitle = GeocodingUtil.generateTripTitle(
            context,
            cluster.centerLat,
            cluster.centerLng
        )

        val photoCount = cluster.media.count { !it.isVideo }
        val videoCount = cluster.media.count { it.isVideo }

        // 미리보기 URI (최대 3개, 사진 우선)
        val previewUris = cluster.media
            .sortedBy { it.isVideo }  // 사진 먼저
            .take(3)
            .map { it.uri }

        return DetectedTrip(
            id = UUID.randomUUID().toString(),
            location = location,
            latitude = cluster.centerLat,
            longitude = cluster.centerLng,
            startDate = cluster.startDate,
            endDate = cluster.endDate,
            mediaUris = cluster.media.map { it.uri },
            mediaCount = cluster.media.size,
            photoCount = photoCount,
            videoCount = videoCount,
            previewUris = previewUris,
            suggestedTitle = suggestedTitle
        )
    }
}
