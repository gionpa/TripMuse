package com.tripmuse.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val keyAccess = stringPreferencesKey("accessToken")
    private val keyRefresh = stringPreferencesKey("refreshToken")

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyAccess]
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyRefresh]
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[keyAccess] = access
            prefs[keyRefresh] = refresh
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAccess)
            prefs.remove(keyRefresh)
        }
    }
}

