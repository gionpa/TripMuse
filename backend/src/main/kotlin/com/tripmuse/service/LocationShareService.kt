package com.tripmuse.service

import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.domain.LocationShare
import com.tripmuse.domain.LocationShareStatus
import com.tripmuse.domain.LocationShareUiStatus
import com.tripmuse.dto.LocationShareStatusResponse
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.LocationShareRepository
import com.tripmuse.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class LocationShareService(
    private val locationShareRepository: LocationShareRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository
) {

    /**
     * 위치 공유 요청: 상대 친구 화면에 승인 버튼이 노출되는 REQUESTED 상태를 만든다.
     */
    @Transactional
    fun requestLocationShare(userId: Long, friendId: Long): LocationShareStatusResponse {
        validateFriendship(userId, friendId)

        val existing = locationShareRepository.findByPair(userId, friendId)
        if (existing != null) {
            val message = when {
                existing.status == LocationShareStatus.APPROVED -> "이미 위치 공유가 승인된 친구입니다"
                existing.requester.id == userId -> "이미 위치 공유를 요청했습니다"
                else -> "상대방이 이미 위치 공유를 요청했습니다. 승인해주세요"
            }
            throw ResponseStatusException(HttpStatus.CONFLICT, message)
        }

        val requester = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }
        val recipient = userRepository.findById(friendId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "친구를 찾을 수 없습니다") }

        locationShareRepository.save(LocationShare(requester = requester, recipient = recipient))
        return LocationShareStatusResponse(friendId, LocationShareUiStatus.REQUESTED_BY_ME)
    }

    /**
     * 위치 공유 승인: 상대가 보낸 REQUESTED를 APPROVED로 전환한다.
     * 이후 양쪽 모두 '현재 위치보기' 상태가 된다.
     */
    @Transactional
    fun approveLocationShare(userId: Long, friendId: Long): LocationShareStatusResponse {
        val request = locationShareRepository.findByRequesterIdAndRecipientId(friendId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "승인할 위치 공유 요청이 없습니다")

        if (request.status == LocationShareStatus.APPROVED) {
            return LocationShareStatusResponse(friendId, LocationShareUiStatus.APPROVED)
        }

        request.status = LocationShareStatus.APPROVED
        return LocationShareStatusResponse(friendId, LocationShareUiStatus.APPROVED)
    }

    /**
     * 친구 목록용: userId 기준으로 각 친구의 위치 공유 UI 상태 맵을 만든다.
     */
    @Transactional(readOnly = true)
    fun getStatusMapFor(userId: Long): Map<Long, LocationShareUiStatus> {
        return locationShareRepository.findAllInvolvingUser(userId).associate { share ->
            val otherId = if (share.requester.id == userId) share.recipient.id else share.requester.id
            val uiStatus = when {
                share.status == LocationShareStatus.APPROVED -> LocationShareUiStatus.APPROVED
                share.requester.id == userId -> LocationShareUiStatus.REQUESTED_BY_ME
                else -> LocationShareUiStatus.PENDING_MY_APPROVAL
            }
            otherId to uiStatus
        }
    }

    /**
     * 친구 삭제 시 위치 공유 상태도 함께 제거한다.
     */
    @Transactional
    fun removeLocationShare(userId: Long, friendId: Long) {
        locationShareRepository.findByPair(userId, friendId)?.let {
            locationShareRepository.delete(it)
        }
    }

    private fun validateFriendship(userId: Long, friendId: Long) {
        val isFriend = friendshipRepository.existsByUserIdAndFriendIdAndStatus(
            userId, friendId, FriendshipStatus.ACCEPTED
        )
        if (!isFriend) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "친구 관계가 아닌 사용자입니다")
        }
    }
}
