package com.tripmuse.data.presence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tripmuse.data.sound.ChatSound
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
    private val keyChatSoundEnabled = booleanPreferencesKey("chatSoundEnabled")
    private val keyChatSoundName = stringPreferencesKey("chatSoundName")

    /** 친구 접속 알림 사용 여부 (기본 켜짐) */
    val friendOnlineAlertEnabled: Flow<Boolean> =
        context.notificationDataStore.data.map { prefs -> prefs[keyFriendOnline] ?: true }

    suspend fun isFriendOnlineAlertEnabled(): Boolean = friendOnlineAlertEnabled.first()

    suspend fun setFriendOnlineAlertEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs -> prefs[keyFriendOnline] = enabled }
    }

    /** 채팅 수신음 사용 여부 (기본 켜짐) */
    val chatSoundEnabled: Flow<Boolean> =
        context.notificationDataStore.data.map { prefs -> prefs[keyChatSoundEnabled] ?: true }

    suspend fun isChatSoundEnabled(): Boolean = chatSoundEnabled.first()

    suspend fun setChatSoundEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs -> prefs[keyChatSoundEnabled] = enabled }
    }

    /** 사용자가 고른 채팅 수신음 */
    val chatSound: Flow<ChatSound> =
        context.notificationDataStore.data.map { prefs -> ChatSound.fromKey(prefs[keyChatSoundName]) }

    suspend fun getChatSound(): ChatSound = chatSound.first()

    suspend fun setChatSound(sound: ChatSound) {
        context.notificationDataStore.edit { prefs -> prefs[keyChatSoundName] = sound.key }
    }
}
