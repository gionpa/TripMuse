package com.tripmuse.data.update

import android.util.Log
import com.tripmuse.BuildConfig
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.AppVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppUpdateChecker"

/**
 * 앱이 켜질 때 서버에 최신 버전을 물어보고, 안내가 필요하면 [pendingUpdate]에 담는다.
 *
 * 어떤 버전부터 업데이트를 권할지·강제할지는 서버가 판단한다. 정책을 바꾸려고
 * 앱을 다시 배포하지 않아도 되게 하기 위해서다.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val api: TripMuseApi,
    private val preferences: UpdatePreferences
) {
    private val _pendingUpdate = MutableStateFlow<AppVersionInfo?>(null)
    val pendingUpdate: StateFlow<AppVersionInfo?> = _pendingUpdate.asStateFlow()

    suspend fun check() {
        val info = runCatching {
            api.getAppVersion(versionCode = BuildConfig.VERSION_CODE)
        }.onFailure {
            // 버전 확인이 안 된다고 앱을 못 쓰게 할 이유는 없다. 다음 실행에 다시 물어본다.
            Log.d(TAG, "버전 확인 실패: ${it.message}")
        }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return

        if (!info.updateAvailable) {
            _pendingUpdate.value = null
            return
        }
        // 강제 업데이트는 미룰 수 없다
        if (!info.updateRequired &&
            preferences.isSnoozed(info.latestVersionCode, System.currentTimeMillis())
        ) {
            return
        }
        _pendingUpdate.value = info
    }

    /** "나중에" — 같은 버전은 하루 동안 다시 묻지 않는다 */
    suspend fun snoozeCurrent() {
        val info = _pendingUpdate.value ?: return
        preferences.snooze(info.latestVersionCode, System.currentTimeMillis())
        _pendingUpdate.value = null
    }
}
