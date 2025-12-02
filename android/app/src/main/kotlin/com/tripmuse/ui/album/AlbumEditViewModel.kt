package com.tripmuse.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.UpdateAlbumRequest
import com.tripmuse.data.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumEditUiState(
    val albumId: Long = 0,
    val title: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val isPublic: Boolean = false,
    val isInitialLoading: Boolean = true,
    val isLoading: Boolean = false,
    val isUpdated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumEditViewModel @Inject constructor(
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumEditUiState())
    val uiState: StateFlow<AlbumEditUiState> = _uiState.asStateFlow()

    fun loadAlbum(albumId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInitialLoading = true, albumId = albumId)

            albumRepository.getAlbumDetail(albumId)
                .onSuccess { album ->
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = false,
                        title = album.title,
                        location = album.location ?: "",
                        startDate = album.startDate ?: "",
                        endDate = album.endDate ?: "",
                        isPublic = album.isPublic
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = false,
                        error = e.message ?: "앨범을 불러올 수 없습니다"
                    )
                }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title, error = null)
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(location = location)
    }

    fun updateStartDate(date: String) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    fun updateEndDate(date: String) {
        _uiState.value = _uiState.value.copy(endDate = date)
    }

    fun updateIsPublic(isPublic: Boolean) {
        _uiState.value = _uiState.value.copy(isPublic = isPublic)
    }

    fun updateAlbum() {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = "앨범 제목을 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val request = UpdateAlbumRequest(
                title = state.title,
                location = state.location.ifBlank { null },
                startDate = state.startDate.ifBlank { null },
                endDate = state.endDate.ifBlank { null },
                isPublic = state.isPublic
            )

            albumRepository.updateAlbum(state.albumId, request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isUpdated = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "앨범 수정에 실패했습니다"
                    )
                }
        }
    }
}
