package com.tripmuse.data.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tripmuse.data.model.ServerNotification
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

    /**
     * 서버 알림(공유 앨범 이벤트)을 로컬에 반영한다.
     * 서버가 read 상태의 진실이므로 "server-" 항목은 매번 서버 목록으로 통째로 교체하고,
     * 기기에서 감지한 로컬 항목(친구 접속)은 그대로 둔다.
     */
    suspend fun syncFromServer(items: List<ServerNotification>) {
        context.notificationCenterStore.edit { prefs ->
            val local = decode(prefs[key]).filterNot { it.id.startsWith("server-") }
            val server = items.mapNotNull { s ->
                val type = NotificationType.fromServer(s.type) ?: return@mapNotNull null
                AppNotification(
                    id = "server-${s.id}",
                    type = type,
                    title = s.title,
                    body = s.body,
                    timestamp = parseTime(s.createdAt),
                    read = s.read,
                    albumId = s.albumId
                )
            }
            val merged = (local + server).sortedByDescending { it.timestamp }.take(MAX_ITEMS)
            prefs[key] = json.encodeToString(listSerializer, merged)
        }
    }

    // 서버(Railway)는 createdAt을 UTC LocalDateTime으로 준다. 기기 타임존으로 해석하면
    // 그 시차만큼 어긋나(예: KST에서 9시간 전으로) 표시되므로 UTC로 파싱한다.
    private fun parseTime(iso: String): Long = runCatching {
        java.time.LocalDateTime.parse(iso)
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    suspend fun clear() {
        context.notificationCenterStore.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<AppNotification> =
        raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
}
