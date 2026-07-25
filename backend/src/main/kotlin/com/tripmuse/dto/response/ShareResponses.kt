package com.tripmuse.dto.response

data class ShareLinkResponse(
    val shareToken: String,
    val shareUrl: String
)

data class ShareResolveResponse(
    val albumId: Long,
    val title: String
)
