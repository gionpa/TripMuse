package com.tripmuse.service

import com.tripmuse.domain.Friendship
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.dto.AddFriendRequest
import com.tripmuse.dto.FriendListResponse
import com.tripmuse.dto.FriendResponse
import com.tripmuse.dto.UserSearchListResponse
import com.tripmuse.dto.UserSearchResponse
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

        val friendIds = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
            .map { it.friend.id }
            .toSet()

        val results = users.map { user ->
            UserSearchResponse.from(user, friendIds.contains(user.id))
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

        if (friendshipRepository.existsByUserIdAndFriendId(userId, request.friendId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 친구로 등록된 사용자입니다")
        }

        // 정방향 친구 관계 생성 (user -> friend)
        val friendship = Friendship(
            user = user,
            friend = friend,
            status = FriendshipStatus.ACCEPTED
        )
        val saved = friendshipRepository.save(friendship)

        // 역방향 친구 관계 생성 (friend -> user) - 양방향 친구 관계
        if (!friendshipRepository.existsByUserIdAndFriendId(request.friendId, userId)) {
            val reverseFriendship = Friendship(
                user = friend,
                friend = user,
                status = FriendshipStatus.ACCEPTED
            )
            friendshipRepository.save(reverseFriendship)
        }

        return FriendResponse.from(saved)
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
}
