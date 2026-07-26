package com.tripmuse.data.presence

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱이 지금 화면에 떠 있는지 추적한다.
 *
 * 채팅 수신음은 사용자가 앱을 보고 있을 때만 낸다. 백그라운드에서 소리만 울리면
 * 화면에 아무것도 뜨지 않은 채 소리가 나는 셈이라 무슨 일인지 알 수 없다.
 */
@Singleton
class AppForegroundTracker @Inject constructor() {

    private val startedActivities = AtomicInteger(0)

    val isForeground: Boolean get() = startedActivities.get() > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities.incrementAndGet()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities.updateAndGet { if (it > 0) it - 1 else 0 }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
