package com.tripmuse.data.presence

import android.util.Log
import com.tripmuse.data.auth.TokenManager
import com.tripmuse.data.repository.FriendRepository
import com.tripmuse.data.repository.PresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val HEARTBEAT_INTERVAL_MS = 45_000L
private const val POLL_INTERVAL_MS = 30_000L
private const val TAG = "PresenceMonitor"

/**
 * 앱이 화면에 보이는 동안 동작한다.
 *  - 내 접속 상태를 heartbeat로 유지
 *  - 친구 접속 상태를 폴링해, 오프라인 → 온라인으로 바뀐 친구를 알림으로 알려준다
 *
 * 앱이 완전히 종료된 동안에는 동작하지 않는다 (백그라운드 푸시는 FCM 도입 시 대응).
 */
@Singleton
class PresenceMonitor @Inject constructor(
    private val presenceRepository: PresenceRepository,
    private val friendRepository: FriendRepository,
    private val notifier: FriendOnlineNotifier,
    private val notificationPreferences: NotificationPreferences,
    private val tokenManager: TokenManager
) {
    private var heartbeatJob: Job? = null
    private var pollJob: Job? = null

    /** friendId → 최근 확인된 온라인 여부 */
    private val knownOnline = mutableMapOf<Long, Boolean>()
    private var baselineReady = false

    private val _onlineFriendIds = MutableStateFlow<Set<Long>>(emptySet())
    val onlineFriendIds: StateFlow<Set<Long>> = _onlineFriendIds.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = scope.launch {
            while (isActive) {
                if (tokenManager.getAccessTokenSync() != null) {
                    presenceRepository.sendHeartbeat()
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        pollJob = scope.launch {
            while (isActive) {
                if (tokenManager.getAccessTokenSync() != null) {
                    checkFriendPresences()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        pollJob?.cancel()
        heartbeatJob = null
        pollJob = null
    }

    /** 로그아웃/계정 전환 시 이전 계정의 상태가 새 계정 알림에 섞이지 않게 초기화한다 */
    fun reset() {
        knownOnline.clear()
        baselineReady = false
        _onlineFriendIds.value = emptySet()
    }

    private suspend fun checkFriendPresences() {
        val presences = presenceRepository.getFriendPresences().getOrNull() ?: return
        _onlineFriendIds.value = presences.filter { it.isOnline }.map { it.friendId }.toSet()

        val newlyOnline = presences.filter { presence ->
            presence.isOnline && knownOnline[presence.friendId] == false
        }
        presences.forEach { knownOnline[it.friendId] = it.isOnline }

        // 첫 조회는 비교 기준을 만드는 용도라 알림을 보내지 않는다
        if (!baselineReady) {
            baselineReady = true
            return
        }
        if (newlyOnline.isEmpty()) return
        if (!notificationPreferences.isFriendOnlineAlertEnabled()) return

        // 닉네임은 친구 목록에서 가져온다 (presence 응답에는 ID만 있음)
        val friends = friendRepository.getFriends().getOrNull()?.friends ?: return
        newlyOnline.forEach { presence ->
            val nickname = friends.firstOrNull { it.id == presence.friendId }?.nickname ?: return@forEach
            Log.d(TAG, "friend came online: $nickname (${presence.friendId})")
            notifier.notifyFriendOnline(presence.friendId, nickname)
        }
    }
}
