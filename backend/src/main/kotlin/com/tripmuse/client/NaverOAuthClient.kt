package com.tripmuse.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Component
class NaverOAuthClient(
    builder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(NaverOAuthClient::class.java)

    private val httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(10))

    private val webClient: WebClient = builder
        .baseUrl("https://openapi.naver.com")
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun fetchUserInfo(accessToken: String): NaverUserInfo? {
        log.info("Fetching Naver user info with token: ${accessToken.take(10)}...")

        return try {
            val response = webClient.get()
                .uri("/v1/nid/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .bodyToMono<NaverUserInfoResponse>()
                .timeout(Duration.ofSeconds(10))
                .block()

            if (response == null) {
                log.warn("Naver API returned null response")
                return null
            }

            log.info("Naver API response - resultcode: ${response.resultcode}, message: ${response.message}")

            if (response.resultcode != "00") {
                log.warn("Naver API returned error - code: ${response.resultcode}, message: ${response.message}")
                return null
            }

            log.info("Successfully fetched Naver user info - email: ${response.response.email}, id: ${response.response.id}")
            response.response
        } catch (e: WebClientResponseException) {
            log.error("Naver API HTTP error - status: ${e.statusCode}, body: ${e.responseBodyAsString}", e)
            null
        } catch (e: Exception) {
            log.error("Naver API error - ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }
}

data class NaverUserInfoResponse(
    val resultcode: String,
    val message: String,
    val response: NaverUserInfo
)

data class NaverUserInfo(
    val id: String,
    val email: String?,
    val nickname: String?,
    val profile_image: String?
)

