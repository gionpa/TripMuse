package com.tripmuse.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.tripmuse.data.presence.AppForegroundTracker
import com.tripmuse.data.presence.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatSoundPlayer"

/** 메시지가 한꺼번에 여러 개 도착해도 소리는 한 번만 낸다 */
private const val MIN_GAP_MS = 1_200L

private const val VOLUME = 0.85f

/**
 * 채팅 수신음 재생기.
 *
 * 짧은 효과음이라 MediaPlayer 대신 SoundPool을 쓴다. 미리 디코딩해두고 바로 재생해서
 * 메시지가 뜨는 순간과 소리가 어긋나지 않는다.
 */
@Singleton
class ChatSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationPreferences: NotificationPreferences,
    private val appForegroundTracker: AppForegroundTracker
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sampleIds = ConcurrentHashMap<ChatSound, Int>()
    private val readySamples = ConcurrentHashMap.newKeySet<Int>()
    private val pendingSamples = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var lastPlayedAt = 0L

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) {
                Log.w(TAG, "수신음 디코딩 실패 (sampleId=$sampleId, status=$status)")
                pendingSamples.remove(sampleId)
                return@setOnLoadCompleteListener
            }
            readySamples.add(sampleId)
            // 디코딩이 끝나기 전에 재생 요청이 들어왔다면 지금 낸다
            if (pendingSamples.remove(sampleId)) fire(sampleId)
        }
    }

    /** 새 메시지가 도착했을 때 호출한다. 설정·무음모드·연타를 모두 걸러낸다. */
    suspend fun playIncoming() {
        if (!appForegroundTracker.isForeground) return
        if (!notificationPreferences.isChatSoundEnabled()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayedAt < MIN_GAP_MS) return
        lastPlayedAt = now

        play(notificationPreferences.getChatSound())
    }

    /** 설정 화면에서 소리를 고를 때 들려준다 (수신음을 꺼둔 상태에서도 미리듣기는 된다) */
    fun preview(sound: ChatSound) {
        lastPlayedAt = SystemClock.elapsedRealtime()
        play(sound)
    }

    /** 휴대폰이 무음/진동이면 소리를 내지 않는다 */
    fun isDeviceSilent(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun play(sound: ChatSound) {
        if (isDeviceSilent()) return
        val sampleId = sampleIds.computeIfAbsent(sound) { soundPool.load(context, it.resId, 1) }
        if (readySamples.contains(sampleId)) fire(sampleId) else pendingSamples.add(sampleId)
    }

    private fun fire(sampleId: Int) {
        soundPool.play(sampleId, VOLUME, VOLUME, 1, 0, 1f)
    }
}
