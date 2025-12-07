package com.tripmuse.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.AlbumVisibility
import com.tripmuse.data.model.CreateAlbumRequest
import com.tripmuse.data.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumCreateUiState(
    val title: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val visibility: AlbumVisibility = AlbumVisibility.PRIVATE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdAlbumId: Long? = null
)

@HiltViewModel
class AlbumCreateViewModel @Inject constructor(
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumCreateUiState())
    val uiState: StateFlow<AlbumCreateUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title, error = null)
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(location = location)
    }

    fun updateStartDate(startDate: String) {
        _uiState.value = _uiState.value.copy(startDate = startDate)
    }

    fun updateEndDate(endDate: String) {
        _uiState.value = _uiState.value.copy(endDate = endDate)
    }

    fun updateVisibility(visibility: AlbumVisibility) {
        _uiState.value = _uiState.value.copy(visibility = visibility)
    }

    fun createAlbum() {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = "앨범 제목을 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val request = CreateAlbumRequest(
                title = state.title,
                location = state.location.ifBlank { null },
                startDate = state.startDate.ifBlank { null },
                endDate = state.endDate.ifBlank { null },
                visibility = state.visibility
            )

            albumRepository.createAlbum(request)
                .onSuccess { album ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        createdAlbumId = album.id
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "앨범 생성에 실패했습니다"
                    )
                }
        }
    }
}
