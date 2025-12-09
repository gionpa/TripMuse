package com.tripmuse.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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

    fun getAccessTokenSync(): String? = runBlocking {
        accessToken.firstOrNull()
    }

    fun getRefreshTokenSync(): String? = runBlocking {
        refreshToken.firstOrNull()
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[keyAccess] = access
            prefs[keyRefresh] = refresh
        }
    }

    fun saveTokensSync(access: String, refresh: String) {
        runBlocking {
            saveTokens(access, refresh)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAccess)
            prefs.remove(keyRefresh)
        }
    }

    fun clearSync() {
        runBlocking {
            clear()
        }
    }
}

