package com.tripmuse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load albums"
                    )
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
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete album"
                    )
                }
        }
    }

    fun moveAlbum(fromIndex: Int, toIndex: Int) {
        val currentAlbums = _uiState.value.albums.toMutableList()
        if (fromIndex in currentAlbums.indices && toIndex in currentAlbums.indices) {
            val item = currentAlbums.removeAt(fromIndex)
            currentAlbums.add(toIndex, item)
            _uiState.value = _uiState.value.copy(
                albums = currentAlbums,
                filteredAlbums = applyFilters(
                    currentAlbums,
                    _uiState.value.searchQuery,
                    _uiState.value.selectedTab
                )
            )

            // Save new order to server
            viewModelScope.launch {
                val albumIds = currentAlbums.map { it.id }
                albumRepository.reorderAlbums(albumIds)
                    .onFailure { e ->
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
