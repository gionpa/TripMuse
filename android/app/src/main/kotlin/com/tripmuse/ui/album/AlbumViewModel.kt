package com.tripmuse.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.*
import com.tripmuse.data.repository.AlbumRepository
import com.tripmuse.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val isLoading: Boolean = false,
    val album: AlbumDetail? = null,
    val mediaList: List<Media> = emptyList(),
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val error: String? = null
)

enum class MediaFilter {
    ALL, IMAGE, VIDEO
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun loadAlbum(albumId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            albumRepository.getAlbumDetail(albumId)
                .onSuccess { album ->
                    _uiState.value = _uiState.value.copy(album = album)
                    loadMedia(albumId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load album"
                    )
                }
        }
    }

    private fun loadMedia(albumId: Long) {
        viewModelScope.launch {
            loadMediaInternal(albumId, scheduleFollowUp = true)
        }
    }

    private suspend fun loadMediaInternal(albumId: Long, scheduleFollowUp: Boolean) {
        val mediaType = when (_uiState.value.selectedFilter) {
            MediaFilter.ALL -> null
            MediaFilter.IMAGE -> MediaType.IMAGE
            MediaFilter.VIDEO -> MediaType.VIDEO
        }

        mediaRepository.getMediaByAlbum(albumId, mediaType)
            .onSuccess { mediaList ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mediaList = mediaList
                )
                if (scheduleFollowUp) {
                    scheduleRefresh(albumId, mediaList)
                }
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load media"
                )
            }
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        _uiState.value.album?.let { loadMedia(it.id) }
    }

    fun resetFilter() {
        _uiState.value = _uiState.value.copy(selectedFilter = MediaFilter.ALL)
    }

    private fun scheduleRefresh(albumId: Long, mediaList: List<Media>) {
        refreshJob?.cancel()
        val hasProcessing = mediaList.any { it.uploadStatus == UploadStatus.PROCESSING }
        val attempts = if (hasProcessing) 30 else 5
        val interval = if (hasProcessing) 2000L else 1500L
        refreshJob = viewModelScope.launch {
            repeat(attempts) {
                delay(interval)
                loadMediaInternal(albumId, scheduleFollowUp = false)
                val current = _uiState.value.mediaList
                if (current.none { it.uploadStatus == UploadStatus.PROCESSING }) {
                    return@launch
                }
            }
        }
    }

    fun deleteMedia(mediaId: Long) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(mediaId)
                .onSuccess {
                    _uiState.value.album?.let { loadAlbum(it.id) }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete media"
                    )
                }
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(albumId)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete album"
                    )
                }
        }
    }

    fun setCoverImage(mediaId: Long) {
        viewModelScope.launch {
            mediaRepository.setCoverImage(mediaId)
                .onSuccess {
                    _uiState.value.album?.let { loadAlbum(it.id) }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to set cover image"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
