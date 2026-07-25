package com.tripmuse.service

import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.domain.UserPresence
import com.tripmuse.dto.FriendPresenceListResponse
import com.tripmuse.dto.FriendPresenceResponse
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.UserPresenceRepository
import com.tripmuse.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class PresenceService(
    private val userPresenceRepository: UserPresenceRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository
) {

    /**
     * 접속 신호 갱신. 앱이 보이는 동안 주기적으로 호출된다.
     */
    @Transactional
    fun heartbeat(userId: Long) {
        val existing = userPresenceRepository.findByUserId(userId)
        if (existing != null) {
            existing.touch()
            return
        }
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }
        userPresenceRepository.save(UserPresence(user = user))
    }

    /**
     * 친구들의 접속 상태만 가볍게 조회한다 (폴링용).
     */
    @Transactional(readOnly = true)
    fun getFriendPresences(userId: Long): FriendPresenceListResponse {
        val friendIds = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
            .map { it.friend.id }
        if (friendIds.isEmpty()) return FriendPresenceListResponse(emptyList())

        val lastSeenMap = lastSeenMapOf(friendIds)
        return FriendPresenceListResponse(
            friendIds.map { friendId ->
                val lastSeenAt = lastSeenMap[friendId]
                FriendPresenceResponse(
                    friendId = friendId,
                    isOnline = UserPresence.isOnline(lastSeenAt),
                    lastSeenAt = lastSeenAt
                )
            }
        )
    }

    /**
     * 친구 목록 응답에 접속 상태를 합칠 때 사용한다.
     */
    @Transactional(readOnly = true)
    fun lastSeenMapOf(userIds: Collection<Long>): Map<Long, LocalDateTime> {
        if (userIds.isEmpty()) return emptyMap()
        return userPresenceRepository.findByUserIdIn(userIds)
            .associate { it.user.id to it.lastSeenAt }
    }
}
