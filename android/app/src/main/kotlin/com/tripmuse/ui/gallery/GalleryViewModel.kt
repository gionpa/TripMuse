package com.tripmuse.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val isVideo: Boolean
)

data class GalleryUiState(
    val isLoading: Boolean = false,
    val galleryItems: List<GalleryItem> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isUploading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadGalleryMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val items = withContext(Dispatchers.IO) {
                val mediaItems = mutableListOf<GalleryItem>()

                // Load images
                mediaItems.addAll(loadMediaFromUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    isVideo = false
                ))

                // Load videos
                mediaItems.addAll(loadMediaFromUri(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    isVideo = true
                ))

                // Sort by date descending
                mediaItems.sortedByDescending { it.dateAdded }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                galleryItems = items
            )
        }
    }

    private fun loadMediaFromUri(contentUri: Uri, isVideo: Boolean): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val date = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(contentUri, id)

                items.add(GalleryItem(
                    id = id,
                    uri = uri,
                    displayName = name,
                    dateAdded = date,
                    isVideo = isVideo
                ))
            }
        }

        return items
    }

    fun toggleSelection(uri: String) {
        val currentSelection = _uiState.value.selectedItems.toMutableSet()
        if (uri in currentSelection) {
            currentSelection.remove(uri)
        } else {
            currentSelection.add(uri)
        }
        _uiState.value = _uiState.value.copy(selectedItems = currentSelection)
    }

    fun uploadSelectedMedia(albumId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)

            val selectedUris = _uiState.value.selectedItems.map { Uri.parse(it) }

            for (uri in selectedUris) {
                mediaRepository.uploadMedia(albumId, uri)
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            error = e.message ?: "Upload failed"
                        )
                    }
            }

            _uiState.value = _uiState.value.copy(
                isUploading = false,
                selectedItems = emptySet()
            )
            onComplete()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
