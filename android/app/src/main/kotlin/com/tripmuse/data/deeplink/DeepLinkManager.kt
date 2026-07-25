package com.tripmuse.data.deeplink

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인이 필요해 처리하지 못한 딥링크를 보관했다가, 로그인 완료 후 이어서 처리한다.
 */
@Singleton
class DeepLinkManager @Inject constructor() {
    var pendingShareToken: String? = null

    fun consumePendingShareToken(): String? {
        val token = pendingShareToken
        pendingShareToken = null
        return token
    }
}
