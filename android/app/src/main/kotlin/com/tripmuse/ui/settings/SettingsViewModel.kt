package com.tripmuse.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.api.TripMuseApi
import com.tripmuse.data.model.StorageUsage
import com.tripmuse.data.presence.NotificationPreferences
import com.tripmuse.data.sound.ChatSound
import com.tripmuse.data.sound.ChatSoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = false,
    val storageUsage: StorageUsage? = null,
    val error: String? = null,
    val friendOnlineAlertEnabled: Boolean = true,
    val chatSoundEnabled: Boolean = true,
    val chatSound: ChatSound = ChatSound.DEFAULT,
    /** 무음 모드면 소리를 골라도 들리지 않으므로 화면에서 알려준다 */
    val deviceSilent: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: TripMuseApi,
    private val notificationPreferences: NotificationPreferences,
    private val chatSoundPlayer: ChatSoundPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // collect는 돌아오지 않으므로 설정마다 따로 띄운다
        viewModelScope.launch {
            notificationPreferences.friendOnlineAlertEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(friendOnlineAlertEnabled = enabled)
            }
        }
        viewModelScope.launch {
            notificationPreferences.chatSoundEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(chatSoundEnabled = enabled)
            }
        }
        viewModelScope.launch {
            notificationPreferences.chatSound.collect { sound ->
                _uiState.value = _uiState.value.copy(chatSound = sound)
            }
        }
    }

    fun setFriendOnlineAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setFriendOnlineAlertEnabled(enabled)
        }
    }

    fun setChatSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setChatSoundEnabled(enabled)
            if (enabled) chatSoundPlayer.preview(_uiState.value.chatSound)
        }
    }

    /** 고르는 즉시 들려준다 — 이름만 보고는 어떤 소리인지 알 수 없다 */
    fun selectChatSound(sound: ChatSound) {
        viewModelScope.launch {
            notificationPreferences.setChatSound(sound)
            chatSoundPlayer.preview(sound)
        }
    }

    fun refreshSilentMode() {
        _uiState.value = _uiState.value.copy(deviceSilent = chatSoundPlayer.isDeviceSilent())
    }

    fun loadStorageUsage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.getStorageUsage()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        storageUsage = response.body()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "저장공간 정보를 불러오는데 실패했습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "오류가 발생했습니다"
                )
            }
        }
    }
}
