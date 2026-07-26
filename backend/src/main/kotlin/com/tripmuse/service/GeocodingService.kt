package com.tripmuse.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.time.Duration

@Service
class GeocodingService {
    private val logger = LoggerFactory.getLogger(GeocodingService::class.java)

    // Nominatim usage policy requires an identifying User-Agent; default Java UA is blocked (403).
    // timeout을 두지 않으면 Nominatim이 느려질 때 목록 응답 전체가 무한정 매달린다.
    private val restTemplate = RestTemplate(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2000)
            setReadTimeout(3000)
        }
    ).apply {
        interceptors.add({ request, body, execution ->
            request.headers.set("User-Agent", "TripMuse/1.0 (contact@tripmuse.com)")
            execution.execute(request, body)
        })
    }

    // 지오코딩 결과 캐시. 무제한 ConcurrentHashMap 대신 크기·수명을 제한한다.
    // 지명은 거의 변하지 않으므로 TTL을 길게, "결과 없음"은 ""로 저장한다 (null 불가).
    private val cache = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofDays(30))
        .build<String, String>()

    /**
     * Reverse geocode latitude/longitude to a location name (city, country)
     * Uses OpenStreetMap Nominatim API (free, no API key required)
     */
    fun reverseGeocode(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        if (latitude == 0.0 && longitude == 0.0) return null

        // Round to 2 decimal places for cache key (reduces API calls, ~1km precision)
        val cacheKey = "%.2f,%.2f".format(latitude, longitude)

        // Check in-memory cache first
        cache.getIfPresent(cacheKey)?.let { return it.ifEmpty { null } }

        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=10&accept-language=ko"

            val response = restTemplate.getForObject<NominatimResponse>(url)

            val locationName = response?.let { buildLocationName(it) }

            // Cache the result ("" = no result, since the cache disallows null values)
            cache.put(cacheKey, locationName ?: "")

            logger.debug("Geocoded ($latitude, $longitude) -> $locationName")
            locationName
        } catch (e: Exception) {
            logger.warn("Failed to reverse geocode ($latitude, $longitude): ${e.message}")
            cache.put(cacheKey, "")
            null
        }
    }

    private fun buildLocationName(response: NominatimResponse): String? {
        val address = response.address ?: return null

        // Priority: city > town > county > state
        val city = address.city
            ?: address.town
            ?: address.village
            ?: address.county
            ?: address.state

        val country = address.country

        return when {
            city != null && country != null && city != country -> "$city, $country"
            city != null -> city
            country != null -> country
            else -> null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NominatimResponse(
    val address: NominatimAddress?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NominatimAddress(
    val city: String?,
    val town: String?,
    val village: String?,
    val county: String?,
    val state: String?,
    val country: String?
)
