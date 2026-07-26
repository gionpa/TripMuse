package com.tripmuse.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

/** 진동 길이. 알아챌 만큼은 되면서 손목을 때리지는 않는 정도. */
private const val VIBRATION_MS = 60L

/** 휴대폰 수신 모드에 따라 채팅 도착을 어떻게 알릴지 */
enum class ChatAlertMode { SOUND, VIBRATE, NONE }

/**
 * 채팅 수신 알림 재생기.
 *
 * 벨소리 모드면 소리를, 진동 모드면 짧은 진동을 낸다. 무음이면 아무것도 하지 않는다.
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

    /** 새 메시지가 도착했을 때 호출한다. 설정·수신 모드·연타를 모두 걸러낸다. */
    suspend fun playIncoming() {
        if (!appForegroundTracker.isForeground) return
        if (!notificationPreferences.isChatSoundEnabled()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayedAt < MIN_GAP_MS) return
        lastPlayedAt = now

        alert(notificationPreferences.getChatSound())
    }

    /** 설정 화면에서 소리를 고를 때 들려준다 (수신음을 꺼둔 상태에서도 미리듣기는 된다) */
    fun preview(sound: ChatSound) {
        lastPlayedAt = SystemClock.elapsedRealtime()
        alert(sound)
    }

    /** 지금 휴대폰 수신 모드에서 어떻게 알리게 되는지 */
    fun currentAlertMode(): ChatAlertMode {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ChatAlertMode.SOUND
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> ChatAlertMode.SOUND
            AudioManager.RINGER_MODE_VIBRATE -> ChatAlertMode.VIBRATE
            else -> ChatAlertMode.NONE
        }
    }

    private fun alert(sound: ChatSound) {
        when (currentAlertMode()) {
            ChatAlertMode.SOUND -> play(sound)
            ChatAlertMode.VIBRATE -> vibrate()
            ChatAlertMode.NONE -> Unit
        }
    }

    private fun play(sound: ChatSound) {
        val sampleId = sampleIds.computeIfAbsent(sound) { soundPool.load(context, it.resId, 1) }
        if (readySamples.contains(sampleId)) fire(sampleId) else pendingSamples.add(sampleId)
    }

    private fun fire(sampleId: Int) {
        soundPool.play(sampleId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun vibrate() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE),
            // 알림용이라고 알려줘야 방해금지 모드 같은 시스템 설정을 따른다
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
