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
 * 접속 상태를 다루는 두 흐름의 생명주기가 다르다.
 *
 *  - heartbeat: 앱이 화면에 보이는 동안만. "온라인"은 지금 앱을 보고 있다는 뜻이어야 하므로,
 *    백그라운드에서 계속 보내면 몇 시간 전에 앱을 내린 사람도 온라인으로 남는다.
 *  - 친구 접속 감시: 앱 프로세스가 살아 있는 동안 계속. 알림이 정작 필요한 순간은
 *    앱을 다른 화면으로 내려둔 때이므로 화면 표시 여부와 묶지 않는다.
 *
 * 앱이 완전히 종료되면 감시도 멈춘다 (그 경우까지 알림을 보내려면 FCM이 필요).
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

    /** 앱이 화면에 보이는 동안 호출 — 내 접속 상태를 유지한다 */
    fun startHeartbeat(scope: CoroutineScope) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                if (tokenManager.getAccessTokenSync() != null) {
                    presenceRepository.sendHeartbeat()
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** 앱 실행 중(백그라운드 포함) 계속 호출 — 친구 접속을 감지해 알림을 띄운다 */
    fun startWatching(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                if (tokenManager.getAccessTokenSync() != null) {
                    checkFriendPresences()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
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
