package com.tripmuse.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.AlbumDetail
import com.tripmuse.data.model.Media
import com.tripmuse.data.model.MediaType
import com.tripmuse.data.repository.AlbumRepository
import com.tripmuse.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val album: AlbumDetail? = null,
    val mediaList: List<Media> = emptyList(),
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val page: Int = 0,
    val hasMore: Boolean = true,
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
    private var loadMediaJob: Job? = null

    fun loadAlbum(albumId: Long) {
        // Cancel any ongoing media loading jobs before starting new album load
        loadMediaJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                mediaList = emptyList(),
                page = 0,
                hasMore = true
            )

            albumRepository.getAlbumDetail(albumId)
                .onSuccess { album ->
                    _uiState.value = _uiState.value.copy(album = album)
                    loadMediaPage(albumId, page = 0, append = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load album"
                    )
                }
        }
    }

    private fun loadMediaPage(albumId: Long, page: Int, append: Boolean) {
        // Cancel previous media loading job to prevent concurrent access
        loadMediaJob?.cancel()

        loadMediaJob = viewModelScope.launch {
            val mediaType = when (_uiState.value.selectedFilter) {
                MediaFilter.ALL -> null
                MediaFilter.IMAGE -> MediaType.IMAGE
                MediaFilter.VIDEO -> MediaType.VIDEO
            }

            val isFirstPage = page == 0
            _uiState.value = _uiState.value.copy(
                isLoading = isFirstPage,
                isLoadingMore = !isFirstPage && append
            )

            try {
                mediaRepository.getMediaByAlbum(
                    albumId = albumId,
                    type = mediaType,
                    page = page,
                    size = 12
                )
                    .onSuccess { mediaList ->
                        val newList = if (append) {
                            _uiState.value.mediaList + mediaList
                        } else {
                            mediaList
                        }
                        val hasMore = mediaList.size == 12

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            mediaList = newList,
                            page = page,
                            hasMore = hasMore
                        )
                    }
                    .onFailure { e ->
                        // Ignore cancellation exceptions - they're expected when switching tabs
                        if (e is CancellationException) return@onFailure
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message ?: "Failed to load media"
                        )
                    }
            } catch (e: CancellationException) {
                // Silently ignore - expected when switching tabs
            }
        }
    }

    fun loadNextPage(albumId: Long) {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadMediaPage(albumId, page = state.page + 1, append = true)
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            mediaList = emptyList(),
            page = 0,
            hasMore = true
        )
        _uiState.value.album?.let { loadMediaPage(it.id, page = 0, append = false) }
    }

    fun resetFilter() {
        _uiState.value = _uiState.value.copy(
            selectedFilter = MediaFilter.ALL,
            mediaList = emptyList(),
            page = 0,
            hasMore = true
        )
        _uiState.value.album?.let { loadMediaPage(it.id, page = 0, append = false) }
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
