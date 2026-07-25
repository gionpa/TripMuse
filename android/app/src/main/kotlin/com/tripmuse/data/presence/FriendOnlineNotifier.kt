package com.tripmuse.data.presence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tripmuse.MainActivity
import com.tripmuse.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "friend_online"

@Singleton
class FriendOnlineNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "친구 접속 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "친구가 TripMuse에 접속하면 알려줍니다"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyFriendOnline(friendId: Long, nickname: String) {
        if (!canNotify()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            friendId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${nickname}님이 접속했어요")
            .setContentText("지금 위치를 확인하거나 대화를 시작해보세요")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 친구별로 알림을 덮어써 같은 친구가 여러 줄로 쌓이지 않게 한다
        NotificationManagerCompat.from(context).notify(friendId.toInt(), notification)
    }
}
