package com.tripmuse.data.presence

import com.tripmuse.data.auth.TokenManager
import com.tripmuse.data.repository.ChatRepository
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

private const val POLL_INTERVAL_MS = 10_000L

/**
 * 하단 채팅 탭 배지에 표시할 안읽은 메시지 총합.
 *
 * 채팅 목록 화면도 같은 값을 쓰도록 updateCount()로 밀어넣어, 목록의 방별 개수와
 * 탭 배지가 서로 다른 숫자를 보여주지 않게 한다.
 */
@Singleton
class ChatUnreadMonitor @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager
) {
    private var pollJob: Job? = null
    private var scope: CoroutineScope? = null

    private val _totalUnread = MutableStateFlow(0L)
    val totalUnread: StateFlow<Long> = _totalUnread.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return
        this.scope = scope
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        scope?.launch { refresh() }
    }

    /**
     * 이미 방 목록을 받아온 화면이 계산한 총합을 그대로 반영한다 (중복 호출 없이 즉시 일치).
     */
    fun updateCount(count: Long) {
        _totalUnread.value = count
    }

    fun reset() {
        _totalUnread.value = 0
    }

    private suspend fun refresh() {
        if (tokenManager.getAccessTokenSync() == null) {
            _totalUnread.value = 0
            return
        }
        chatRepository.getTotalUnreadCount().onSuccess { _totalUnread.value = it }
    }
}
