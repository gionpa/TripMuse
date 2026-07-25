package com.tripmuse.data.presence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyFriendOnline = booleanPreferencesKey("friendOnlineAlert")

    /** 친구 접속 알림 사용 여부 (기본 켜짐) */
    val friendOnlineAlertEnabled: Flow<Boolean> =
        context.notificationDataStore.data.map { prefs -> prefs[keyFriendOnline] ?: true }

    suspend fun isFriendOnlineAlertEnabled(): Boolean = friendOnlineAlertEnabled.first()

    suspend fun setFriendOnlineAlertEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs -> prefs[keyFriendOnline] = enabled }
    }
}
