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
    val isFriend: Boolean
)

data class UserSearchListResponse(
    val users: List<UserSearchResult>,
    val totalCount: Int
)

data class AddFriendRequest(
    val friendId: Long
)
