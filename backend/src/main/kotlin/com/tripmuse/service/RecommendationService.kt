package com.tripmuse.service

import com.tripmuse.dto.request.AnalyzeMediaRequest
import com.tripmuse.dto.request.MediaInfo
import com.tripmuse.dto.response.RecommendationItem
import com.tripmuse.dto.response.RecommendationResponse
import com.tripmuse.dto.response.RecommendationType
import com.tripmuse.repository.AlbumRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

@Service
class RecommendationService(
    private val albumRepository: AlbumRepository
) {
    companion object {
        // 50km radius for same location clustering
        private const val LOCATION_CLUSTER_RADIUS_KM = 50.0
        // 3 days gap for same trip clustering
        private const val DATE_CLUSTER_GAP_DAYS = 3L
    }

    fun analyzeAndRecommend(userId: Long, request: AnalyzeMediaRequest): RecommendationResponse {
        val mediaInfoList = request.mediaInfoList
        if (mediaInfoList.isEmpty()) {
            return RecommendationResponse(emptyList())
        }

        // Cluster media by location and date
        val clusters = clusterMedia(mediaInfoList)

        // Get user's existing albums
        val existingAlbums = albumRepository.findByUserIdOrderByCreatedAtDesc(userId)

        val recommendations = mutableListOf<RecommendationItem>()

        for (cluster in clusters) {
            // Find matching album
            val matchingAlbum = existingAlbums.find { album ->
                isClusterMatchingAlbum(cluster, album)
            }

            if (matchingAlbum != null) {
                // Recommend adding to existing album
                recommendations.add(
                    RecommendationItem(
                        type = RecommendationType.ADD_TO_EXISTING,
                        location = cluster.location,
                        latitude = cluster.centerLatitude,
                        longitude = cluster.centerLongitude,
                        startDate = cluster.startDate,
                        endDate = cluster.endDate,
                        mediaCount = cluster.mediaCount,
                        previewFilenames = cluster.previewFilenames,
                        targetAlbumId = matchingAlbum.id,
                        targetAlbumTitle = matchingAlbum.title
                    )
                )
            } else {
                // Recommend creating new album
                recommendations.add(
                    RecommendationItem(
                        type = RecommendationType.NEW_TRIP,
                        location = cluster.location,
                        latitude = cluster.centerLatitude,
                        longitude = cluster.centerLongitude,
                        startDate = cluster.startDate,
                        endDate = cluster.endDate,
                        mediaCount = cluster.mediaCount,
                        previewFilenames = cluster.previewFilenames
                    )
                )
            }
        }

        return RecommendationResponse(recommendations)
    }

    private fun clusterMedia(mediaInfoList: List<MediaInfo>): List<MediaCluster> {
        val clusters = mutableListOf<MediaCluster>()
        val processed = mutableSetOf<Int>()

        for (i in mediaInfoList.indices) {
            if (i in processed) continue

            val current = mediaInfoList[i]
            val clusterMembers = mutableListOf(current)
            processed.add(i)

            for (j in (i + 1) until mediaInfoList.size) {
                if (j in processed) continue

                val other = mediaInfoList[j]
                if (shouldClusterTogether(current, other)) {
                    clusterMembers.add(other)
                    processed.add(j)
                }
            }

            clusters.add(createCluster(clusterMembers))
        }

        return clusters
    }

    private fun shouldClusterTogether(a: MediaInfo, b: MediaInfo): Boolean {
        // Check date proximity
        val dateClose = if (a.takenAt != null && b.takenAt != null) {
            val daysDiff = abs(ChronoUnit.DAYS.between(a.takenAt.toLocalDate(), b.takenAt.toLocalDate()))
            daysDiff <= DATE_CLUSTER_GAP_DAYS
        } else {
            false
        }

        // Check location proximity
        val locationClose = if (a.latitude != null && a.longitude != null &&
            b.latitude != null && b.longitude != null) {
            val distance = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            distance <= LOCATION_CLUSTER_RADIUS_KM
        } else {
            false
        }

        return dateClose || locationClose
    }

    private fun createCluster(members: List<MediaInfo>): MediaCluster {
        val latitudes = members.mapNotNull { it.latitude }
        val longitudes = members.mapNotNull { it.longitude }
        val dates = members.mapNotNull { it.takenAt?.toLocalDate() }

        return MediaCluster(
            location = null, // Could integrate reverse geocoding here
            centerLatitude = latitudes.average().takeIf { latitudes.isNotEmpty() },
            centerLongitude = longitudes.average().takeIf { longitudes.isNotEmpty() },
            startDate = dates.minOrNull(),
            endDate = dates.maxOrNull(),
            mediaCount = members.size,
            previewFilenames = members.take(3).map { it.filename }
        )
    }

    private fun isClusterMatchingAlbum(cluster: MediaCluster, album: com.tripmuse.domain.Album): Boolean {
        // Check location match
        val locationMatch = if (cluster.centerLatitude != null && cluster.centerLongitude != null &&
            album.latitude != null && album.longitude != null) {
            val distance = haversineDistance(
                cluster.centerLatitude, cluster.centerLongitude,
                album.latitude!!, album.longitude!!
            )
            distance <= LOCATION_CLUSTER_RADIUS_KM
        } else {
            false
        }

        // Check date overlap
        val dateMatch = if (cluster.startDate != null && cluster.endDate != null &&
            album.startDate != null && album.endDate != null) {
            !(cluster.endDate.isBefore(album.startDate) || cluster.startDate.isAfter(album.endDate))
        } else {
            false
        }

        return locationMatch || dateMatch
    }

    // Haversine formula for distance calculation
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * asin(sqrt(a))

        return R * c
    }

    private data class MediaCluster(
        val location: String?,
        val centerLatitude: Double?,
        val centerLongitude: Double?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val mediaCount: Int,
        val previewFilenames: List<String>
    )
}
