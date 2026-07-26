package com.tripmuse.data.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationCenterStore by preferencesDataStore(name = "notification_center")

/** 최근 알림만 보관한다. 친구 접속 알림이 무한정 쌓이지 않게. */
private const val MAX_ITEMS = 100

/**
 * 인앱 알림 센터 저장소.
 *
 * 앱이 떠 있는 동안 감지한 친구 접속 등을 DataStore에 JSON으로 쌓아두고,
 * 상단 벨 배지(안읽음 수)와 알림 화면이 같은 소스를 본다.
 */
@Singleton
class NotificationStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(AppNotification.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val items: StateFlow<List<AppNotification>> =
        context.notificationCenterStore.data
            .map { prefs -> decode(prefs[key]) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val unreadCount: StateFlow<Int> =
        items.map { list -> list.count { !it.read } }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    suspend fun add(notification: AppNotification) {
        context.notificationCenterStore.edit { prefs ->
            val current = decode(prefs[key])
            // 최신이 위로, 최대 개수까지만
            val updated = (listOf(notification) + current).take(MAX_ITEMS)
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    suspend fun markAllRead() {
        context.notificationCenterStore.edit { prefs ->
            val current = decode(prefs[key])
            if (current.none { !it.read }) return@edit
            prefs[key] = json.encodeToString(listSerializer, current.map { it.copy(read = true) })
        }
    }

    suspend fun clear() {
        context.notificationCenterStore.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<AppNotification> =
        raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
}
