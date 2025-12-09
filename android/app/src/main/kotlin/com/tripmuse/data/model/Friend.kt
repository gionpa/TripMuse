package com.tripmuse.data.model

data class Friend(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val addedAt: String
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
