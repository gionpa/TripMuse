package com.tripmuse.ui.album

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.AlbumDetail
import com.tripmuse.data.model.Media
import com.tripmuse.data.model.MediaType
import com.tripmuse.data.model.UploadStatus
import com.tripmuse.data.repository.AlbumRepository
import com.tripmuse.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

// 임시 ID 생성을 위한 카운터 (음수로 시작하여 서버 ID와 충돌 방지)
private val pendingIdCounter = AtomicLong(-1L)

data class AlbumDetailUiState(
    val isLoading: Boolean = false,
    val album: AlbumDetail? = null,
    val mediaList: List<Media> = emptyList(),
    val pendingMedia: List<Media> = emptyList(), // 업로드 대기중인 로컬 미디어
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val error: String? = null
) {
    // 표시용: pending + 실제 미디어 합쳐서 반환
    val displayMediaList: List<Media>
        get() = pendingMedia + mediaList
}

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
    private var loadMediaJob: Job? = null

    fun loadAlbum(albumId: Long) {
        // Cancel any ongoing media loading jobs before starting new album load
        loadMediaJob?.cancel()
        refreshJob?.cancel()

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
        // Cancel previous media loading and refresh jobs to prevent concurrent access
        loadMediaJob?.cancel()
        refreshJob?.cancel()

        loadMediaJob = viewModelScope.launch {
            loadMediaInternal(albumId, scheduleFollowUp = true)
        }
    }

    private suspend fun loadMediaInternal(albumId: Long, scheduleFollowUp: Boolean) {
        val mediaType = when (_uiState.value.selectedFilter) {
            MediaFilter.ALL -> null
            MediaFilter.IMAGE -> MediaType.IMAGE
            MediaFilter.VIDEO -> MediaType.VIDEO
        }

        try {
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
                    // Ignore cancellation exceptions - they're expected when switching tabs
                    if (e is CancellationException) return@onFailure
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load media"
                    )
                }
        } catch (e: CancellationException) {
            // Silently ignore - expected when switching tabs or cancelling refresh
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
        // FAILED 상태가 있으면 무한 대기하지 않고 적은 횟수만 새로고침
        val hasFailed = mediaList.any { it.uploadStatus == UploadStatus.FAILED }

        val attempts = when {
            hasFailed -> 3  // 실패 상태가 있으면 빠르게 종료
            hasProcessing -> 30
            else -> 5
        }
        val interval = if (hasProcessing) 2000L else 1500L

        refreshJob = viewModelScope.launch {
            try {
                repeat(attempts) { attemptIndex ->
                    delay(interval)
                    loadMediaInternal(albumId, scheduleFollowUp = false)
                    val current = _uiState.value.mediaList

                    // PROCESSING 상태가 없으면 종료 (COMPLETED 또는 FAILED로 변경됨)
                    val stillProcessing = current.any { it.uploadStatus == UploadStatus.PROCESSING }
                    if (!stillProcessing) {
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                // Silently ignore - expected when switching tabs
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

    /**
     * 업로드 시작 시 pending 미디어를 추가합니다.
     * 로컬 URI에서 파일명과 타입을 추출하여 임시 Media 객체를 생성합니다.
     */
    data class PendingMediaInfo(
        val uri: Uri,
        val filename: String,
        val isVideo: Boolean
    )

    fun addPendingMedia(pendingItems: List<PendingMediaInfo>) {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val newPendingMedia = pendingItems.map { item ->
            Media(
                id = pendingIdCounter.getAndDecrement(),
                type = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE,
                uploadStatus = UploadStatus.PROCESSING,
                filePath = item.uri.toString(),
                fileUrl = item.uri.toString(), // 로컬 URI를 임시로 사용
                thumbnailPath = null,
                thumbnailUrl = item.uri.toString(), // 로컬 이미지 표시용
                originalFilename = item.filename,
                fileSize = null,
                latitude = null,
                longitude = null,
                locationName = null,
                takenAt = null,
                isCover = false,
                createdAt = currentTime
            )
        }

        _uiState.value = _uiState.value.copy(
            pendingMedia = _uiState.value.pendingMedia + newPendingMedia
        )
    }

    /**
     * 업로드 완료/실패 시 pending 미디어를 제거하고 서버에서 최신 데이터를 로드합니다.
     */
    fun removePendingMediaAndRefresh(count: Int = 1) {
        val currentPending = _uiState.value.pendingMedia
        if (currentPending.isNotEmpty()) {
            // 가장 오래된 pending 항목부터 제거 (FIFO)
            val remaining = if (count >= currentPending.size) {
                emptyList()
            } else {
                currentPending.drop(count)
            }
            _uiState.value = _uiState.value.copy(pendingMedia = remaining)
        }

        // 서버에서 최신 데이터 로드
        _uiState.value.album?.let { loadAlbum(it.id) }
    }

    /**
     * 모든 pending 미디어를 제거합니다.
     */
    fun clearAllPendingMedia() {
        _uiState.value = _uiState.value.copy(pendingMedia = emptyList())
    }
}
