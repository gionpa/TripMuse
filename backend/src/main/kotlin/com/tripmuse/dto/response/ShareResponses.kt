package com.tripmuse.dto.response

data class ShareLinkResponse(
    val shareToken: String,
    val shareUrl: String
)

data class ShareRevokeResponse(
    /** 실제로 해제된 링크가 있었는지 */
    val revoked: Boolean,
    /** 링크로 열람 권한을 얻었다가 회수된 사람 수 */
    val revokedViewerCount: Long
)

data class ShareResolveResponse(
    val albumId: Long,
    val title: String
)
