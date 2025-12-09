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
    val isFriend: Boolean,
    val invitedByMe: Boolean = false,
    val invitedMe: Boolean = false,
    val invitationId: Long? = null
) {
    companion object {
        fun from(
            user: User,
            isFriend: Boolean,
            invitedByMe: Boolean,
            invitedMe: Boolean,
            invitationId: Long?
        ): UserSearchResponse {
            return UserSearchResponse(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                isFriend = isFriend,
                invitedByMe = invitedByMe,
                invitedMe = invitedMe,
                invitationId = invitationId
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

data class InvitationResponse(
    val invitationId: Long,
    val fromUserId: Long,
    val fromEmail: String,
    val fromNickname: String,
    val fromProfileImageUrl: String?
) {
    companion object {
        fun from(friendship: Friendship): InvitationResponse {
            return InvitationResponse(
                invitationId = friendship.id,
                fromUserId = friendship.user.id,
                fromEmail = friendship.user.email,
                fromNickname = friendship.user.nickname,
                fromProfileImageUrl = friendship.user.profileImageUrl
            )
        }
    }
}

data class InvitationListResponse(
    val invitations: List<InvitationResponse>
)
