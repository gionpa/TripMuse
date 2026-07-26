package com.tripmuse.service

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatMessageType
import com.tripmuse.domain.ChatRoom
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.dto.ChatMessageListResponse
import com.tripmuse.dto.ChatMessageResponse
import com.tripmuse.dto.ChatRoomListResponse
import com.tripmuse.dto.ChatRoomResponse
import com.tripmuse.repository.ChatMessageRepository
import com.tripmuse.repository.ChatRoomRepository
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

private const val MESSAGE_PAGE_SIZE = 50

@Service
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService
) {

    /**
     * 친구와의 1:1 채팅방을 가져오거나 생성한다. (쌍당 1개, user1.id < user2.id 정규화)
     */
    @Transactional
    fun getOrCreateRoom(userId: Long, friendId: Long): ChatRoomResponse {
        if (userId == friendId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신과는 채팅할 수 없습니다")
        }
        val isFriend = friendshipRepository.existsByUserIdAndFriendIdAndStatus(
            userId, friendId, FriendshipStatus.ACCEPTED
        )
        if (!isFriend) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "친구 관계가 아닌 사용자와는 채팅할 수 없습니다")
        }

        val (lowId, highId) = if (userId < friendId) userId to friendId else friendId to userId
        val existing = chatRoomRepository.findByUser1IdAndUser2Id(lowId, highId)
        val room = existing ?: run {
            val low = userRepository.findById(lowId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }
            val high = userRepository.findById(highId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }
            chatRoomRepository.save(ChatRoom(user1 = low, user2 = high))
        }
        return ChatRoomResponse.from(room, userId, unreadCountOf(room, userId))
    }

    @Transactional(readOnly = true)
    fun getRooms(userId: Long): ChatRoomListResponse {
        val rooms = chatRoomRepository.findAllByMember(userId)
        return ChatRoomListResponse(rooms.map { ChatRoomResponse.from(it, userId, unreadCountOf(it, userId)) })
    }

    /**
     * 모든 방의 안읽은 메시지 합계 (하단 탭 뱃지용)
     */
    @Transactional(readOnly = true)
    fun getTotalUnreadCount(userId: Long): Long {
        return chatRoomRepository.findAllByMember(userId).sumOf { unreadCountOf(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getRoom(roomId: Long, userId: Long): ChatRoomResponse {
        val room = findRoomForMember(roomId, userId)
        return ChatRoomResponse.from(room, userId, unreadCountOf(room, userId))
    }

    /**
     * 메시지 조회.
     * - afterId: 해당 ID 이후의 새 메시지 (폴링용, 오래된 것부터)
     * - beforeId: 해당 ID 이전 히스토리 한 페이지 (무한 스크롤용)
     * - 둘 다 없으면 최신 한 페이지
     * 응답 메시지는 항상 오래된 → 최신 순서.
     */
    @Transactional(readOnly = true)
    fun getMessages(roomId: Long, userId: Long, beforeId: Long?, afterId: Long?): ChatMessageListResponse {
        val room = findRoomForMember(roomId, userId)
        val otherId = room.otherUser(userId).id
        val otherLastRead = room.lastReadMessageIdOf(otherId)
        val otherTyping = room.isTyping(otherId)

        fun toResponse(message: ChatMessage) = ChatMessageResponse.from(
            message = message,
            requestUserId = userId,
            // 보낸 사람 자신은 제외하고, 아직 안 읽은 참여자 수를 센다
            unreadCount = if (message.sender.id == userId && message.id > otherLastRead) 1 else 0
        )

        if (afterId != null) {
            val newMessages = chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterId)
            return ChatMessageListResponse(
                messages = newMessages.map(::toResponse),
                hasMore = false,
                otherLastReadMessageId = otherLastRead,
                otherTyping = otherTyping
            )
        }

        val pageDesc = if (beforeId != null) {
            chatMessageRepository.findTop50ByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId)
        } else {
            chatMessageRepository.findTop50ByRoomIdOrderByIdDesc(roomId)
        }
        return ChatMessageListResponse(
            messages = pageDesc.asReversed().map(::toResponse),
            hasMore = pageDesc.size == MESSAGE_PAGE_SIZE,
            otherLastReadMessageId = otherLastRead,
            otherTyping = otherTyping
        )
    }

    /**
     * 입력 중 신호. 클라이언트가 타이핑하는 동안 주기적으로 호출한다.
     */
    @Transactional
    fun markTyping(roomId: Long, userId: Long) {
        val room = findRoomForMember(roomId, userId)
        room.markTyping(userId)
    }

    @Transactional
    fun sendMessage(roomId: Long, userId: Long, content: String): ChatMessageResponse {
        return saveMessage(roomId, userId, content.trim(), ChatMessageType.TEXT, null)
    }

    /**
     * 사진 메시지 전송. 앨범 업로드와 같은 압축 규칙(최대 2048px, JPEG 85%)을 쓴다.
     */
    @Transactional
    fun sendImageMessage(roomId: Long, userId: Long, file: MultipartFile): ChatMessageResponse {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 전송할 수 없습니다")
        }
        val contentType = file.contentType ?: ""
        if (!contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일만 전송할 수 있습니다")
        }
        findRoomForMember(roomId, userId)

        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val relativePath = "chat/${UUID.randomUUID()}.$extension"
        storageService.storeImageBytesAtPath(file.bytes, relativePath)

        return saveMessage(roomId, userId, "사진", ChatMessageType.IMAGE, relativePath)
    }

    private fun saveMessage(
        roomId: Long,
        userId: Long,
        content: String,
        type: ChatMessageType,
        imagePath: String?
    ): ChatMessageResponse {
        val room = findRoomForMember(roomId, userId)
        val sender = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }

        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = sender,
                content = content,
                type = type,
                imagePath = imagePath
            )
        )

        room.lastMessageAt = message.createdAt
        room.lastMessagePreview = message.content.take(300)
        // 내가 보낸 메시지는 내 기준으로 읽은 것으로 처리
        room.updateLastRead(userId, message.id)

        return ChatMessageResponse.from(message, userId)
    }

    /**
     * 방의 모든 메시지를 읽음 처리한다.
     */
    @Transactional
    fun markAsRead(roomId: Long, userId: Long) {
        val room = findRoomForMember(roomId, userId)
        val latestId = chatMessageRepository.findLatestMessageId(roomId)
        if (latestId > 0) {
            room.updateLastRead(userId, latestId)
        }
    }

    private fun findRoomForMember(roomId: Long, userId: Long): ChatRoom {
        val room = chatRoomRepository.findById(roomId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다") }
        if (!room.isMember(userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 채팅방에 접근할 수 없습니다")
        }
        return room
    }

    private fun unreadCountOf(room: ChatRoom, userId: Long): Long {
        return chatMessageRepository.countByRoomIdAndIdGreaterThanAndSenderIdNot(
            room.id, room.lastReadMessageIdOf(userId), userId
        )
    }
}
