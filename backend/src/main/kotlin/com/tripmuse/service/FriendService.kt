package com.tripmuse.service

import com.tripmuse.domain.Friendship
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.dto.AddFriendRequest
import com.tripmuse.dto.FriendListResponse
import com.tripmuse.dto.FriendResponse
import com.tripmuse.dto.UserSearchListResponse
import com.tripmuse.dto.UserSearchResponse
import com.tripmuse.dto.InvitationListResponse
import com.tripmuse.dto.InvitationResponse
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun getFriends(userId: Long): FriendListResponse {
        val friendships = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
        val friends = friendships.map { FriendResponse.from(it) }
        return FriendListResponse(friends, friends.size)
    }

    @Transactional(readOnly = true)
    fun searchUsers(userId: Long, query: String): UserSearchListResponse {
        if (query.isBlank() || query.length < 2) {
            return UserSearchListResponse(emptyList(), 0)
        }

        val users = userRepository.findByEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(query, query)
            .filter { it.id != userId }
            .take(20)

        val acceptedIds = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
            .map { it.friend.id }.toSet()
        val outgoingPending = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.PENDING)
            .map { it.friend.id }.toSet()
        val incomingPending = friendshipRepository.findByFriendIdAndStatus(userId, FriendshipStatus.PENDING)
            .associateBy({ it.user.id }, { it.id })

        val results = users.map { user ->
            val isFriend = acceptedIds.contains(user.id)
            val invitedByMe = outgoingPending.contains(user.id)
            val invitedMe = incomingPending.containsKey(user.id)
            val invitationId = incomingPending[user.id]
            UserSearchResponse.from(
                user = user,
                isFriend = isFriend,
                invitedByMe = invitedByMe,
                invitedMe = invitedMe,
                invitationId = invitationId
            )
        }

        return UserSearchListResponse(results, results.size)
    }

    @Transactional
    fun addFriend(userId: Long, request: AddFriendRequest): FriendResponse {
        if (userId == request.friendId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 친구로 추가할 수 없습니다")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }

        val friend = userRepository.findById(request.friendId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "친구로 추가할 사용자를 찾을 수 없습니다") }

        // 이미 친구면 거절
        if (friendshipRepository.findByUserIdAndFriendIdAndStatus(userId, request.friendId, FriendshipStatus.ACCEPTED).isPresent) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 친구로 등록된 사용자입니다")
        }

        // 내가 보낸 PENDING 있으면 재요청 방지
        if (friendshipRepository.findByUserIdAndFriendIdAndStatus(userId, request.friendId, FriendshipStatus.PENDING).isPresent) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 초대 요청을 보냈습니다")
        }

        // 상대가 보낸 PENDING이 있으면 그것을 ACCEPT 처리
        val incomingOpt = friendshipRepository.findByUserIdAndFriendIdAndStatus(request.friendId, userId, FriendshipStatus.PENDING)
        if (incomingOpt.isPresent) {
            val incoming = incomingOpt.get()
            acceptInternal(incoming)
            return FriendResponse.from(incoming)
        }

        // 초대(PENDING) 생성
        val invitation = Friendship(
            user = user,
            friend = friend,
            status = FriendshipStatus.PENDING
        )
        val saved = friendshipRepository.save(invitation)
        return FriendResponse(
            id = saved.friend.id,
            email = saved.friend.email,
            nickname = saved.friend.nickname,
            profileImageUrl = saved.friend.profileImageUrl,
            addedAt = saved.createdAt
        )
    }

    @Transactional
    fun removeFriend(userId: Long, friendId: Long) {
        val friendship = friendshipRepository.findByUserIdAndFriendId(userId, friendId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다") }

        // 정방향 친구 관계 삭제 (user -> friend)
        friendshipRepository.delete(friendship)

        // 역방향 친구 관계도 삭제 (friend -> user) - 양방향 친구 관계
        friendshipRepository.findByUserIdAndFriendId(friendId, userId)
            .ifPresent { reverseFriendship ->
                friendshipRepository.delete(reverseFriendship)
            }
    }

    @Transactional(readOnly = true)
    fun getInvitations(userId: Long): InvitationListResponse {
        val invitations = friendshipRepository.findByFriendIdAndStatus(userId, FriendshipStatus.PENDING)
        return InvitationListResponse(invitations.map { InvitationResponse.from(it) })
    }

    @Transactional
    fun acceptInvitation(userId: Long, invitationId: Long) {
        val invitation = friendshipRepository.findById(invitationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "초대 요청을 찾을 수 없습니다") }
        if (invitation.friend.id != userId || invitation.status != FriendshipStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 초대 요청을 수락할 수 없습니다")
        }
        acceptInternal(invitation)
    }

    @Transactional
    fun rejectInvitation(userId: Long, invitationId: Long) {
        val invitation = friendshipRepository.findById(invitationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "초대 요청을 찾을 수 없습니다") }
        if (invitation.friend.id != userId || invitation.status != FriendshipStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 초대 요청을 거절할 수 없습니다")
        }
        invitation.status = FriendshipStatus.REJECTED
        friendshipRepository.save(invitation)
    }

    private fun acceptInternal(invitation: Friendship) {
        val requester = invitation.user
        val receiver = invitation.friend

        invitation.status = FriendshipStatus.ACCEPTED
        friendshipRepository.save(invitation)

        // 역방향 친구 관계 보장 (없으면 생성, 있으면 ACCEPTED로 승격)
        val reverseOpt = friendshipRepository.findByUserIdAndFriendId(receiver.id, requester.id)
        if (reverseOpt.isPresent) {
            val reverse = reverseOpt.get()
            if (reverse.status != FriendshipStatus.ACCEPTED) {
                reverse.status = FriendshipStatus.ACCEPTED
                friendshipRepository.save(reverse)
            }
        } else {
            friendshipRepository.save(
                Friendship(
                    user = receiver,
                    friend = requester,
                    status = FriendshipStatus.ACCEPTED
                )
            )
        }
    }
}
