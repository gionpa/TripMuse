package com.tripmuse.dto

import com.tripmuse.domain.Friendship
import com.tripmuse.domain.User
import java.time.LocalDateTime

data class FriendResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val addedAt: LocalDateTime
) {
    companion object {
        fun from(friendship: Friendship): FriendResponse {
            return FriendResponse(
                id = friendship.friend.id,
                email = friendship.friend.email,
                nickname = friendship.friend.nickname,
                profileImageUrl = friendship.friend.profileImageUrl,
                addedAt = friendship.createdAt
            )
        }
    }
}

data class UserSearchResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val isFriend: Boolean
) {
    companion object {
        fun from(user: User, isFriend: Boolean): UserSearchResponse {
            return UserSearchResponse(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                isFriend = isFriend
            )
        }
    }
}

data class FriendListResponse(
    val friends: List<FriendResponse>,
    val totalCount: Int
)

data class UserSearchListResponse(
    val users: List<UserSearchResponse>,
    val totalCount: Int
)

data class AddFriendRequest(
    val friendId: Long
)
