package com.tripmuse.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmuse.data.model.Friend
import com.tripmuse.data.model.Invitation
import com.tripmuse.data.model.UserSearchResult
import com.tripmuse.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendUiState(
    val isLoading: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val searchResults: List<UserSearchResult> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadFriends()
        loadInvitations()
    }

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            friendRepository.getFriends()
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        friends = response.friends
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun loadInvitations() {
        viewModelScope.launch {
            friendRepository.getInvitations()
                .onSuccess { res ->
                    _uiState.value = _uiState.value.copy(invitations = res.invitations)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, error = null)

        searchJob?.cancel()

        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            searchUsers(query)
        }
    }

    private suspend fun searchUsers(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)

        friendRepository.searchUsers(query)
            .onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = response.users
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message
                )
            }
    }

    fun sendInvitation(userId: Long) {
        viewModelScope.launch {
            friendRepository.addFriend(userId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "초대 요청을 보냈습니다",
                        searchResults = _uiState.value.searchResults.map { user ->
                            if (user.id == userId) user.copy(invitedByMe = true) else user
                        }
                    )
                    loadInvitations()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun removeFriend(friendId: Long) {
        viewModelScope.launch {
            friendRepository.removeFriend(friendId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "친구가 삭제되었습니다",
                        friends = _uiState.value.friends.filter { it.id != friendId },
                        searchResults = _uiState.value.searchResults.map { user ->
                            if (user.id == friendId) user.copy(isFriend = false) else user
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun acceptInvitation(invitationId: Long) {
        viewModelScope.launch {
            friendRepository.acceptInvitation(invitationId)
                .onSuccess {
                    // 초대 수락 후 친구 목록과 초대 목록을 순차적으로 새로고침
                    val friendsResult = friendRepository.getFriends()
                    val invitationsResult = friendRepository.getInvitations()

                    friendsResult.onSuccess { friendsResponse ->
                        invitationsResult.onSuccess { invitationsResponse ->
                            _uiState.value = _uiState.value.copy(
                                successMessage = "초대를 수락했습니다",
                                friends = friendsResponse.friends,
                                invitations = invitationsResponse.invitations
                            )
                        }.onFailure {
                            _uiState.value = _uiState.value.copy(
                                successMessage = "초대를 수락했습니다",
                                friends = friendsResponse.friends
                            )
                        }
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            successMessage = "초대를 수락했습니다",
                            error = "친구 목록 새로고침 실패: ${e.message}"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun rejectInvitation(invitationId: Long) {
        viewModelScope.launch {
            friendRepository.rejectInvitation(invitationId)
                .onSuccess {
                    loadInvitations()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }
}
