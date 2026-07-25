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

private const val POLL_INTERVAL_MS = 20_000L

/**
 * 하단 채팅 탭의 안읽음 표시(레드닷)를 위한 총합 감시.
 * 읽음 처리 직후에는 폴링 주기를 기다리지 않고 refreshNow()로 즉시 반영한다.
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
