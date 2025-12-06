package com.tripmuse.client

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class NaverOAuthClient(
    builder: WebClient.Builder
) {

    private val webClient: WebClient = builder
        .baseUrl("https://openapi.naver.com")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun fetchUserInfo(accessToken: String): NaverUserInfo? {
        return webClient.get()
            .uri("/v1/nid/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .bodyToMono<NaverUserInfoResponse>()
            .block()
            ?.takeIf { it.resultcode == "00" }
            ?.response
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

