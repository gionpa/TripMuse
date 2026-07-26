package com.tripmuse.controller

import com.tripmuse.config.AppVersionConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 앱이 실행될 때 자기 버전이 최신인지 물어보는 곳.
 *
 * 로그인 전에도 호출되므로 인증이 필요 없다 (SecurityConfig의 permitAll 목록 참고).
 */
@RestController
@RequestMapping("/api/v1/app")
@EnableConfigurationProperties(AppVersionConfig::class)
class AppVersionController(
    private val appVersionConfig: AppVersionConfig
) {

    @GetMapping("/version")
    fun getVersion(
        @RequestParam(required = false, defaultValue = "android") platform: String,
        @RequestParam(required = false) versionCode: Int?
    ): ResponseEntity<AppVersionResponse> {
        // 지금은 안드로이드만 있다. iOS가 생기면 platform으로 갈라준다.
        val latest = appVersionConfig.android
        val current = versionCode ?: 0

        return ResponseEntity.ok(
            AppVersionResponse(
                latestVersionCode = latest.latestVersionCode,
                latestVersionName = latest.latestVersionName,
                minSupportedVersionCode = latest.minSupportedVersionCode,
                // 판단을 서버에서 해두면 정책을 바꿀 때 앱을 다시 배포하지 않아도 된다
                updateAvailable = current < latest.latestVersionCode,
                updateRequired = current < latest.minSupportedVersionCode,
                releaseNotes = latest.releaseNotes.ifBlank { null },
                downloadUrl = latest.downloadUrl
            )
        )
    }
}

data class AppVersionResponse(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val updateAvailable: Boolean,
    val updateRequired: Boolean,
    val releaseNotes: String?,
    val downloadUrl: String
)
