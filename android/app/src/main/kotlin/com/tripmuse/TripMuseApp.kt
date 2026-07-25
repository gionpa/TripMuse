package com.tripmuse

import android.app.Application
import com.tripmuse.data.presence.PresenceMonitor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class TripMuseApp : Application() {

    @Inject
    lateinit var presenceMonitor: PresenceMonitor

    // 프로세스가 살아 있는 동안 유지되는 스코프. 앱을 백그라운드로 내려도
    // 친구 접속 감지가 이어지도록 화면 생명주기와 분리한다.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        presenceMonitor.startWatching(appScope)
    }
}
