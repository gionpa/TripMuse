package com.tripmuse.data.model

/** 서버가 알려주는 최신 앱 버전과 업데이트 필요 여부 */
data class AppVersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val updateAvailable: Boolean,
    /** true면 업데이트 전까지 앱을 쓸 수 없다 */
    val updateRequired: Boolean,
    val releaseNotes: String?,
    val downloadUrl: String
)
