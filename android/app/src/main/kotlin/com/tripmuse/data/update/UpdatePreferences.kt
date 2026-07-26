package com.tripmuse.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "update_prefs")

/** "나중에"를 누른 버전을 하루 동안 다시 묻지 않기 위한 저장소 */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keySnoozedVersionCode = intPreferencesKey("snoozedVersionCode")
    private val keySnoozedAt = longPreferencesKey("snoozedAt")

    suspend fun isSnoozed(versionCode: Int, now: Long): Boolean {
        val prefs = context.updateDataStore.data.first()
        if (prefs[keySnoozedVersionCode] != versionCode) return false
        val snoozedAt = prefs[keySnoozedAt] ?: return false
        return now - snoozedAt < SNOOZE_MS
    }

    suspend fun snooze(versionCode: Int, now: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[keySnoozedVersionCode] = versionCode
            prefs[keySnoozedAt] = now
        }
    }

    companion object {
        /** 하루가 지나면 한 번 더 알린다. 더 짧으면 잔소리가 되고, 더 길면 놓친다. */
        private const val SNOOZE_MS = 24 * 60 * 60 * 1000L
    }
}
