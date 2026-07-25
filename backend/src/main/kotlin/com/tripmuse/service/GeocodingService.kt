package com.tripmuse.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.util.concurrent.ConcurrentHashMap

@Service
class GeocodingService {
    private val logger = LoggerFactory.getLogger(GeocodingService::class.java)

    // Nominatim usage policy requires an identifying User-Agent; default Java UA is blocked (403)
    private val restTemplate = RestTemplate().apply {
        interceptors.add({ request, body, execution ->
            request.headers.set("User-Agent", "TripMuse/1.0 (contact@tripmuse.com)")
            execution.execute(request, body)
        })
    }

    // In-memory cache for geocoding results (to reduce API calls)
    // ConcurrentHashMap disallows null values, so "no result" is cached as ""
    private val cache = ConcurrentHashMap<String, String>()

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
        cache[cacheKey]?.let { return it.ifEmpty { null } }

        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=10&accept-language=ko"

            val response = restTemplate.getForObject<NominatimResponse>(url)

            val locationName = response?.let { buildLocationName(it) }

            // Cache the result ("" = no result, since ConcurrentHashMap disallows null)
            cache[cacheKey] = locationName ?: ""

            logger.debug("Geocoded ($latitude, $longitude) -> $locationName")
            locationName
        } catch (e: Exception) {
            logger.warn("Failed to reverse geocode ($latitude, $longitude): ${e.message}")
            cache[cacheKey] = ""
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
