package com.tripmuse.ui.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.data.model.Album
import com.tripmuse.data.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val albums: List<Album> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedTab: AlbumTab = AlbumTab.MINE,
    val error: String? = null
)

enum class AlbumTab {
    MINE, SHARED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val authEventManager: AuthEventManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 스크롤 위치 유지를 위한 LazyGridState
    val gridState = LazyGridState()

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            albumRepository.getAlbums()
                .onSuccess { albums ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        albums = albums,
                        filteredAlbums = applyFilters(
                            albums = albums,
                            query = _uiState.value.searchQuery,
                            tab = _uiState.value.selectedTab
                        )
                    )
                }
                .onFailure { e ->
                    // 인증 에러인 경우 에러 표시하지 않음 (로그인 화면으로 전환됨)
                    if (!authEventManager.isAuthError.value) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load albums"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
        }
    }

    fun toggleSearch() {
        val currentState = _uiState.value
        if (currentState.isSearching) {
            // 검색 모드 종료 - 검색어 초기화
            _uiState.value = currentState.copy(
                isSearching = false,
                searchQuery = "",
                filteredAlbums = applyFilters(
                    albums = currentState.albums,
                    query = "",
                    tab = currentState.selectedTab
                )
            )
        } else {
            // 검색 모드 시작
            _uiState.value = currentState.copy(isSearching = true)
        }
    }

    fun onSearchQueryChange(query: String) {
        val filtered = applyFilters(_uiState.value.albums, query, _uiState.value.selectedTab)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredAlbums = filtered
        )
    }

    private fun applyFilters(albums: List<Album>, query: String, tab: AlbumTab): List<Album> {
        val tabFiltered = when (tab) {
            AlbumTab.MINE -> albums.filter { it.isOwner }
            AlbumTab.SHARED -> albums.filter { !it.isOwner }
        }
        if (query.isBlank()) return tabFiltered
        val lowerQuery = query.lowercase()
        return tabFiltered.filter { album ->
            album.title.lowercase().contains(lowerQuery) ||
                    album.location?.lowercase()?.contains(lowerQuery) == true
        }
    }

    fun selectTab(tab: AlbumTab) {
        val filtered = applyFilters(_uiState.value.albums, _uiState.value.searchQuery, tab)
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            filteredAlbums = filtered
        )
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(albumId)
                .onSuccess {
                    loadAlbums()
                }
                .onFailure { e ->
                    if (!authEventManager.isAuthError.value) {
                        _uiState.value = _uiState.value.copy(
                            error = e.message ?: "Failed to delete album"
                        )
                    }
                }
        }
    }

    fun moveAlbum(fromIndex: Int, toIndex: Int) {
        // 내 앨범 탭에서만 순서 변경 가능
        if (_uiState.value.selectedTab != AlbumTab.MINE) return

        val currentFiltered = _uiState.value.filteredAlbums.toMutableList()
        if (fromIndex !in currentFiltered.indices || toIndex !in currentFiltered.indices) return

        // filteredAlbums에서 순서 변경
        val item = currentFiltered.removeAt(fromIndex)
        currentFiltered.add(toIndex, item)

        // 전체 albums 리스트도 업데이트 (내 앨범만 새 순서로 교체)
        val sharedAlbums = _uiState.value.albums.filter { !it.isOwner }
        val newAlbums = currentFiltered + sharedAlbums

        _uiState.value = _uiState.value.copy(
            albums = newAlbums,
            filteredAlbums = currentFiltered
        )

        // 서버에 내 앨범 순서만 저장
        viewModelScope.launch {
            val myAlbumIds = currentFiltered.map { it.id }
            albumRepository.reorderAlbums(myAlbumIds)
                .onFailure { e ->
                    if (!authEventManager.isAuthError.value) {
                        _uiState.value = _uiState.value.copy(
                            error = e.message ?: "Failed to save album order"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
