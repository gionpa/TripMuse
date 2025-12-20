package com.tripmuse.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.Comment
import com.tripmuse.data.model.MediaDetail
import com.tripmuse.data.model.Memo
import com.tripmuse.data.repository.CommentRepository
import com.tripmuse.data.repository.MediaRepository
import com.tripmuse.data.repository.MemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaDetailUiState(
    val isLoading: Boolean = false,
    val media: MediaDetail? = null,
    val comments: List<Comment> = emptyList(),
    val isEditingMemo: Boolean = false,
    val memoContent: String = "",
    val isSavingMemo: Boolean = false,
    val commentInput: String = "",
    val isDeleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val memoRepository: MemoRepository,
    private val commentRepository: CommentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    fun loadMedia(mediaId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            mediaRepository.getMediaDetail(mediaId)
                .onSuccess { media ->
                    _uiState.value = _uiState.value.copy(
                        media = media,
                        memoContent = media.memo?.content ?: ""
                    )
                    loadComments(mediaId)
                    // 미디어 상세 진입 시 댓글을 읽음으로 표시
                    markCommentsAsRead(mediaId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load media"
                    )
                }
        }
    }

    private fun markCommentsAsRead(mediaId: Long) {
        viewModelScope.launch {
            commentRepository.markCommentsAsRead(mediaId)
        }
    }

    private fun loadComments(mediaId: Long) {
        viewModelScope.launch {
            commentRepository.getComments(mediaId)
                .onSuccess { comments ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        comments = comments
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun setEditingMemo(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingMemo = editing)
    }

    fun updateMemoContent(content: String) {
        _uiState.value = _uiState.value.copy(memoContent = content)
    }

    fun saveMemo() {
        val mediaId = _uiState.value.media?.id ?: return
        val content = _uiState.value.memoContent

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingMemo = true)

            memoRepository.updateMemo(mediaId, content)
                .onSuccess { savedMemo ->
                    // 즉각적인 UI 업데이트: media 객체의 memo를 직접 업데이트
                    val currentMedia = _uiState.value.media
                    if (currentMedia != null) {
                        val updatedMedia = currentMedia.copy(
                            memo = com.tripmuse.data.model.Memo(
                                id = savedMemo.id,
                                content = savedMemo.content,
                                createdAt = savedMemo.createdAt,
                                updatedAt = savedMemo.updatedAt
                            )
                        )
                        _uiState.value = _uiState.value.copy(
                            media = updatedMedia,
                            isEditingMemo = false,
                            isSavingMemo = false,
                            memoContent = savedMemo.content
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isEditingMemo = false,
                            isSavingMemo = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSavingMemo = false,
                        error = e.message ?: "메모 저장에 실패했습니다"
                    )
                }
        }
    }

    fun updateCommentInput(input: String) {
        _uiState.value = _uiState.value.copy(commentInput = input)
    }

    fun postComment() {
        val mediaId = _uiState.value.media?.id ?: return
        val content = _uiState.value.commentInput

        if (content.isBlank()) return

        viewModelScope.launch {
            commentRepository.createComment(mediaId, content)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(commentInput = "")
                    loadComments(mediaId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to post comment"
                    )
                }
        }
    }

    fun deleteComment(commentId: Long) {
        val mediaId = _uiState.value.media?.id ?: return

        viewModelScope.launch {
            commentRepository.deleteComment(commentId)
                .onSuccess {
                    loadComments(mediaId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete comment"
                    )
                }
        }
    }

    fun deleteMedia(mediaId: Long) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(mediaId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleted = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete media"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
