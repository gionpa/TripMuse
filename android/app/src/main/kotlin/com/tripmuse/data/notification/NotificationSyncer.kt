package com.tripmuse.data.notification

import android.util.Log
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.auth.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val POLL_INTERVAL_MS = 30_000L
private const val TAG = "NotificationSyncer"

/**
 * 서버의 공유 앨범 알림을 주기적으로 가져와 [NotificationStore]에 반영한다.
 * 친구 접속 감시(PresenceMonitor)와 같은 생명주기로, 앱이 살아 있는 동안 돈다.
 */
@Singleton
class NotificationSyncer @Inject constructor(
    private val api: TripMuseApi,
    private val store: NotificationStore,
    private val tokenManager: TokenManager
) {
    private var job: Job? = null
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        this.scope = scope
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 폴링 주기를 기다리지 않고 즉시 한 번 갱신 */
    fun refreshNow() {
        scope?.launch { refresh() }
    }

    suspend fun refresh() {
        if (tokenManager.getAccessTokenSync() == null) return
        runCatching { api.getNotifications() }
            .onFailure { Log.d(TAG, "알림 동기화 실패: ${it.message}") }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                store.syncFromServer(it.notifications)
            }
    }

    /** 모두 읽음: 로컬 표시 + 서버에도 반영 */
    suspend fun markAllRead() {
        store.markAllRead()
        runCatching { api.markNotificationsRead() }
            .onFailure { Log.d(TAG, "서버 읽음 처리 실패: ${it.message}") }
    }
}
