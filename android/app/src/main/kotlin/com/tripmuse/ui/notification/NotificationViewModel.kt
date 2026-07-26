package com.tripmuse.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.notification.AppNotification
import com.tripmuse.data.notification.NotificationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationStore: NotificationStore
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> =
        notificationStore.items.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            notificationStore.items.value
        )

    /** 화면을 열면 배지가 사라지도록 모두 읽음 처리한다 (항목 자체는 남는다) */
    fun markAllRead() {
        viewModelScope.launch { notificationStore.markAllRead() }
    }

    fun clearAll() {
        viewModelScope.launch { notificationStore.clear() }
    }
}
