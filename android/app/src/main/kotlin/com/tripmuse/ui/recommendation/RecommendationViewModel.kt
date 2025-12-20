package com.tripmuse.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.DetectedTrip
import com.tripmuse.data.repository.TripDetectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val detectedTrips: List<DetectedTrip> = emptyList(),
    val error: String? = null,
    val permissionRequired: Boolean = false,

    // 앨범 생성 관련 상태
    val selectedTrip: DetectedTrip? = null,
    val showCreateSheet: Boolean = false,
    val albumTitle: String = "",
    val isCreating: Boolean = false,
    val uploadProgress: Int = 0,
    val uploadTotal: Int = 0,

    // 완료 상태
    val createdAlbumId: Long? = null
)

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val tripDetectionRepository: TripDetectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    init {
        scanForTrips()
    }

    /**
     * 여행 스캔 시작
     */
    fun scanForTrips(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                error = null
            )

            tripDetectionRepository.detectTrips(forceRefresh)
                .onSuccess { trips ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        detectedTrips = trips
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = e.message ?: "여행 감지 중 오류가 발생했습니다"
                    )
                }
        }
    }

    /**
     * 추천 무시 (나중에)
     */
    fun dismissTrip(tripId: String) {
        viewModelScope.launch {
            tripDetectionRepository.dismissTrip(tripId)
            _uiState.value = _uiState.value.copy(
                detectedTrips = _uiState.value.detectedTrips.filter { it.id != tripId }
            )
        }
    }

    /**
     * 앨범 생성을 위해 여행 선택
     */
    fun selectTripForAlbum(trip: DetectedTrip) {
        _uiState.value = _uiState.value.copy(
            selectedTrip = trip,
            showCreateSheet = true,
            albumTitle = trip.suggestedTitle
        )
    }

    /**
     * 앨범 제목 변경
     */
    fun updateAlbumTitle(title: String) {
        _uiState.value = _uiState.value.copy(albumTitle = title)
    }

    /**
     * 앨범 생성 바텀시트 닫기
     */
    fun dismissCreateSheet() {
        _uiState.value = _uiState.value.copy(
            selectedTrip = null,
            showCreateSheet = false,
            albumTitle = "",
            uploadProgress = 0,
            uploadTotal = 0
        )
    }

    /**
     * 앨범 생성 및 미디어 업로드
     */
    fun createAlbum() {
        val trip = _uiState.value.selectedTrip ?: return
        val title = _uiState.value.albumTitle.ifBlank { trip.suggestedTitle }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCreating = true,
                uploadProgress = 0,
                uploadTotal = trip.mediaCount,
                error = null
            )

            tripDetectionRepository.createAlbumFromTrip(
                trip = trip,
                title = title,
                onProgress = { uploaded, total ->
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = uploaded,
                        uploadTotal = total
                    )
                }
            ).onSuccess { albumId ->
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showCreateSheet = false,
                    selectedTrip = null,
                    createdAlbumId = albumId,
                    detectedTrips = _uiState.value.detectedTrips.filter { it.id != trip.id }
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.message ?: "앨범 생성 중 오류가 발생했습니다"
                )
            }
        }
    }

    /**
     * 앨범 생성 완료 후 이동 처리됨
     */
    fun onNavigatedToAlbum() {
        _uiState.value = _uiState.value.copy(createdAlbumId = null)
    }

    /**
     * 에러 메시지 초기화
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 권한 필요 상태 설정
     */
    fun setPermissionRequired(required: Boolean) {
        _uiState.value = _uiState.value.copy(permissionRequired = required)
    }
}
