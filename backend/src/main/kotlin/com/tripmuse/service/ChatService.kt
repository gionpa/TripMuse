package com.tripmuse.service

import com.tripmuse.domain.ChatMessage
import com.tripmuse.domain.ChatMessageType
import com.tripmuse.domain.ChatRoom
import com.tripmuse.domain.ChatRoomMember
import com.tripmuse.domain.ChatRoomType
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.domain.User
import com.tripmuse.dto.ChatMessageListResponse
import com.tripmuse.dto.ChatMessageResponse
import com.tripmuse.dto.ChatReadCursor
import com.tripmuse.dto.ChatRoomListResponse
import com.tripmuse.dto.ChatRoomResponse
import com.tripmuse.dto.ChatUserResponse
import com.tripmuse.dto.InviteMembersRequest
import com.tripmuse.repository.ChatMessageRepository
import com.tripmuse.repository.ChatRoomMemberRepository
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
private const val MAX_MEMBERS = 30

@Service
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService
) {

    /**
     * 친구와의 1:1 방을 가져오거나 만든다.
     * 그룹으로 전환된 방은 후보에서 빠지므로, 전환 뒤에는 새 1:1 방이 생긴다.
     */
    @Transactional
    fun getOrCreateRoom(userId: Long, friendId: Long): ChatRoomResponse {
        if (userId == friendId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신과는 채팅할 수 없습니다")
        }
        requireFriendship(userId, friendId)

        val existing = chatRoomRepository.findDirectRooms(userId, friendId).firstOrNull()
        if (existing != null) {
            return toRoomResponse(existing, userId)
        }

        val me = findUser(userId)
        val friend = findUser(friendId)
        val room = chatRoomRepository.save(ChatRoom(type = ChatRoomType.DIRECT))
        chatRoomMemberRepository.save(ChatRoomMember(room = room, user = me))
        chatRoomMemberRepository.save(ChatRoomMember(room = room, user = friend))
        return toRoomResponse(room, userId)
    }

    @Transactional(readOnly = true)
    fun getRooms(userId: Long): ChatRoomListResponse {
        val rooms = chatRoomRepository.findAllByMember(userId)
        if (rooms.isEmpty()) return ChatRoomListResponse(emptyList())

        val membersByRoom = chatRoomMemberRepository
            .findByRoomIdInAndActiveTrue(rooms.map { it.id })
            .groupBy { it.room.id }

        return ChatRoomListResponse(
            rooms.map { room ->
                toRoomResponse(room, userId, membersByRoom[room.id] ?: emptyList())
            }
        )
    }

    @Transactional(readOnly = true)
    fun getTotalUnreadCount(userId: Long): Long {
        return chatRoomRepository.findAllByMember(userId).sumOf { room ->
            val member = chatRoomMemberRepository.findByRoomIdAndUserId(room.id, userId)
            if (member == null) 0L else unreadCountOf(room.id, member)
        }
    }

    @Transactional(readOnly = true)
    fun getRoom(roomId: Long, userId: Long): ChatRoomResponse {
        val (room, _) = findRoomAndMember(roomId, userId)
        return toRoomResponse(room, userId)
    }

    /**
     * 메시지 조회. 참여자의 이력 공개 범위(visibleFromMessageId) 밖의 메시지는 내려주지 않는다.
     */
    @Transactional(readOnly = true)
    fun getMessages(roomId: Long, userId: Long, beforeId: Long?, afterId: Long?): ChatMessageListResponse {
        val (room, myMember) = findRoomAndMember(roomId, userId)
        val members = chatRoomMemberRepository.findByRoomIdAndActiveTrue(roomId)
        val others = members.filter { it.user.id != userId }
        val visibleFrom = myMember.visibleFromMessageId

        fun toResponse(message: ChatMessage) = ChatMessageResponse.from(
            message = message,
            requestUserId = userId,
            unreadCount = unreadMemberCount(message, members)
        )

        val typingMember = others.firstOrNull { it.isTyping() }
        val cursors = members.map {
            ChatReadCursor(it.user.id, it.lastReadMessageId, it.visibleFromMessageId)
        }
        // 1:1 호환 필드
        val otherLastRead = if (room.isGroup) 0 else (others.firstOrNull()?.lastReadMessageId ?: 0)

        val messages = when {
            afterId != null -> chatMessageRepository
                .findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, maxOf(afterId, visibleFrom))
            beforeId != null -> chatMessageRepository
                .findTop50ByRoomIdAndIdLessThanAndIdGreaterThanOrderByIdDesc(roomId, beforeId, visibleFrom)
                .asReversed()
            else -> chatMessageRepository
                .findTop50ByRoomIdAndIdGreaterThanOrderByIdDesc(roomId, visibleFrom)
                .asReversed()
        }

        return ChatMessageListResponse(
            messages = messages.map(::toResponse),
            hasMore = afterId == null && messages.size == MESSAGE_PAGE_SIZE,
            otherLastReadMessageId = otherLastRead,
            otherTyping = typingMember != null,
            typingNickname = typingMember?.user?.nickname,
            readCursors = cursors
        )
    }

    @Transactional
    fun markTyping(roomId: Long, userId: Long) {
        val (_, member) = findRoomAndMember(roomId, userId)
        member.markTyping()
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
        findRoomAndMember(roomId, userId)

        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val relativePath = "chat/${UUID.randomUUID()}.$extension"
        storageService.storeImageBytesAtPath(file.bytes, relativePath)

        return saveMessage(roomId, userId, "사진", ChatMessageType.IMAGE, relativePath)
    }

    /**
     * 친구를 방에 초대한다. 1:1 방이면 그룹방으로 전환되고 대화 이력은 그대로 남는다.
     *
     * @param request.shareHistory true면 이전 대화까지 보이고, false면 초대 이후 메시지만 보인다
     */
    @Transactional
    fun inviteMembers(roomId: Long, userId: Long, request: InviteMembersRequest): ChatRoomResponse {
        val (room, _) = findRoomAndMember(roomId, userId)
        val activeMembers = chatRoomMemberRepository.findByRoomIdAndActiveTrue(roomId)
        val activeMemberIds = activeMembers.map { it.user.id }.toSet()

        val inviteeIds = request.friendIds.distinct().filter { it != userId && it !in activeMemberIds }
        if (inviteeIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "초대할 수 있는 친구가 없습니다")
        }
        if (activeMemberIds.size + inviteeIds.size > MAX_MEMBERS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "한 방에는 최대 ${MAX_MEMBERS}명까지 참여할 수 있습니다")
        }
        inviteeIds.forEach { requireFriendship(userId, it) }

        // 공개하지 않으면 초대 시점 이후 메시지만 보이도록 기준선을 잡는다
        val latestMessageId = chatMessageRepository.findLatestMessageId(roomId)
        val visibleFrom = if (request.shareHistory) 0L else latestMessageId

        val invitees = inviteeIds.map { findUser(it) }
        invitees.forEach { invitee ->
            val existing = chatRoomMemberRepository.findByRoomIdAndUserId(roomId, invitee.id)
            if (existing != null) {
                // 나갔던 사람이 다시 초대된 경우 재활성화
                existing.active = true
                existing.visibleFromMessageId = visibleFrom
                existing.lastReadMessageId = maxOf(existing.lastReadMessageId, visibleFrom)
            } else {
                chatRoomMemberRepository.save(
                    ChatRoomMember(
                        room = room,
                        user = invitee,
                        visibleFromMessageId = visibleFrom,
                        lastReadMessageId = visibleFrom
                    )
                )
            }
        }

        if (!room.isGroup) {
            room.convertToGroup()
        }

        val names = invitees.joinToString(", ") { it.nickname }
        val historyNote = if (request.shareHistory) "이전 대화가 공개되었습니다" else "초대 이후 대화만 보입니다"
        saveSystemMessage(room, findUser(userId), "${names}님이 들어왔어요 · $historyNote")

        return toRoomResponse(room, userId)
    }

    /**
     * 그룹방 나가기. 이후 메시지는 받지 않고 목록에서도 사라진다.
     */
    @Transactional
    fun leaveRoom(roomId: Long, userId: Long) {
        val (room, member) = findRoomAndMember(roomId, userId)
        if (!room.isGroup) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "1:1 채팅방은 나갈 수 없습니다")
        }
        member.active = false
        saveSystemMessage(room, member.user, "${member.user.nickname}님이 나갔어요")
    }

    /**
     * 방의 모든 메시지를 읽음 처리한다.
     */
    @Transactional
    fun markAsRead(roomId: Long, userId: Long) {
        val (_, member) = findRoomAndMember(roomId, userId)
        val latestId = chatMessageRepository.findLatestMessageId(roomId)
        if (latestId > 0) {
            member.updateLastRead(latestId)
        }
    }

    private fun saveMessage(
        roomId: Long,
        userId: Long,
        content: String,
        type: ChatMessageType,
        imagePath: String?
    ): ChatMessageResponse {
        val (room, member) = findRoomAndMember(roomId, userId)
        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = member.user,
                content = content,
                type = type,
                imagePath = imagePath
            )
        )

        room.lastMessageAt = message.createdAt
        room.lastMessagePreview = message.content.take(300)
        // 내가 보낸 메시지는 내 기준으로 읽은 것으로 처리
        member.updateLastRead(message.id)

        val members = chatRoomMemberRepository.findByRoomIdAndActiveTrue(roomId)
        return ChatMessageResponse.from(message, userId, unreadMemberCount(message, members))
    }

    private fun saveSystemMessage(room: ChatRoom, actor: User, content: String) {
        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = actor,
                content = content,
                type = ChatMessageType.SYSTEM
            )
        )
        room.lastMessageAt = message.createdAt
        room.lastMessagePreview = content.take(300)
    }

    private fun findRoomAndMember(roomId: Long, userId: Long): Pair<ChatRoom, ChatRoomMember> {
        val room = chatRoomRepository.findById(roomId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다") }
        val member = chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId)
        if (member == null || !member.active) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "이 채팅방에 접근할 수 없습니다")
        }
        return room to member
    }

    private fun requireFriendship(userId: Long, otherId: Long) {
        val isFriend = friendshipRepository.existsByUserIdAndFriendIdAndStatus(
            userId, otherId, FriendshipStatus.ACCEPTED
        )
        if (!isFriend) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "친구 관계가 아닌 사용자는 초대할 수 없습니다")
        }
    }

    private fun findUser(userId: Long): User = userRepository.findById(userId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다") }

    /** 해당 메시지를 아직 읽지 않은 참여자 수 (보낸 사람과 볼 수 없는 사람은 제외) */
    private fun unreadMemberCount(message: ChatMessage, members: List<ChatRoomMember>): Int {
        if (message.type == ChatMessageType.SYSTEM) return 0
        return members.count { member ->
            member.user.id != message.sender.id &&
                member.canSee(message.id) &&
                member.lastReadMessageId < message.id
        }
    }

    private fun unreadCountOf(roomId: Long, member: ChatRoomMember): Long {
        val from = maxOf(member.lastReadMessageId, member.visibleFromMessageId)
        return chatMessageRepository.countUnread(roomId, from, member.user.id)
    }

    private fun toRoomResponse(
        room: ChatRoom,
        userId: Long,
        preloadedMembers: List<ChatRoomMember>? = null
    ): ChatRoomResponse {
        val members = preloadedMembers ?: chatRoomMemberRepository.findByRoomIdAndActiveTrue(room.id)
        val me = members.firstOrNull { it.user.id == userId }
        val others = members.filter { it.user.id != userId }

        val title = when {
            !room.title.isNullOrBlank() -> room.title!!
            room.isGroup -> others.joinToString(", ") { it.user.nickname }.ifBlank { "그룹 채팅" }
            else -> others.firstOrNull()?.user?.nickname ?: "알 수 없는 사용자"
        }

        return ChatRoomResponse(
            roomId = room.id,
            type = room.type ?: ChatRoomType.DIRECT,
            title = title,
            otherUser = if (room.isGroup) null else others.firstOrNull()?.let { ChatUserResponse.from(it.user) },
            members = members.map { ChatUserResponse.from(it.user) },
            memberCount = members.size,
            lastMessage = room.lastMessagePreview,
            lastMessageAt = room.lastMessageAt,
            unreadCount = me?.let { unreadCountOf(room.id, it) } ?: 0
        )
    }
}
