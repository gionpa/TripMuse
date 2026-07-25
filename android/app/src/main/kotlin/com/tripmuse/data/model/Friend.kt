package com.tripmuse.data.model

data class Friend(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val addedAt: String,
    // Gson은 필드 누락 시 null을 넣으므로 nullable로 두고 사용처에서 NONE 처리
    val locationShareStatus: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null
)

data class FriendPresence(
    val friendId: Long,
    val isOnline: Boolean,
    val lastSeenAt: String?
)

data class FriendPresenceListResponse(
    val presences: List<FriendPresence>
)

data class FriendListResponse(
    val friends: List<Friend>,
    val totalCount: Int
)

data class UserSearchResult(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val isFriend: Boolean,
    val invitedByMe: Boolean = false,
    val invitedMe: Boolean = false,
    val invitationId: Long? = null
)

data class UserSearchListResponse(
    val users: List<UserSearchResult>,
    val totalCount: Int
)

data class AddFriendRequest(
    val friendId: Long
)

data class Invitation(
    val invitationId: Long,
    val fromUserId: Long,
    val fromEmail: String,
    val fromNickname: String,
    val fromProfileImageUrl: String?
)

data class InvitationListResponse(
    val invitations: List<Invitation>
)
