package com.tripmuse.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 클라이언트에 알려줄 최신 앱 버전.
 *
 * 새 버전을 배포하면 application.yml의 값(또는 Railway 환경변수)만 올리면 되고,
 * 앱은 실행할 때마다 이 값과 자기 버전을 비교한다.
 */
@ConfigurationProperties(prefix = "tripmuse.app-version")
data class AppVersionConfig(
    val android: PlatformVersion = PlatformVersion()
) {
    data class PlatformVersion(
        /** 스토어에 올라가 있는 최신 versionCode */
        val latestVersionCode: Int = 1,
        val latestVersionName: String = "1.0.0",
        /** 이 값보다 낮으면 업데이트해야만 쓸 수 있다 (서버 호환이 깨진 경우에만 올린다) */
        val minSupportedVersionCode: Int = 1,
        /** 업데이트 안내에 보여줄 변경점. 비워두면 안내 문구만 나온다 */
        val releaseNotes: String = "",
        val downloadUrl: String = "https://play.google.com/store/apps/details?id=com.tripmuse"
    )
}
