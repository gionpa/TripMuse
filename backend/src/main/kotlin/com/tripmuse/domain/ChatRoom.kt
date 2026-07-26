package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class ChatRoomType {
    DIRECT,
    GROUP
}

/**
 * 채팅방. 참여자는 ChatRoomMember로 관리하므로 1:1과 그룹을 같은 구조로 다룬다.
 *
 * 1:1 방에 친구를 초대하면 이 방이 GROUP으로 전환된다(대화 이력은 그대로 남는다).
 * 그 뒤 같은 두 사람이 1:1 채팅을 시작하면 새 DIRECT 방이 따로 생긴다.
 *
 * lastMessage* 는 방 목록 정렬/미리보기용 비정규화 필드.
 */
@Entity
@Table(name = "chat_rooms")
class ChatRoom(
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    @org.hibernate.annotations.ColumnDefault("'DIRECT'")
    var type: ChatRoomType? = ChatRoomType.DIRECT,

    /** 사용자가 정한 방 이름. null이면 참여자 닉네임을 조합해 보여준다 */
    @Column(length = 200)
    var title: String? = null,

    var lastMessageAt: LocalDateTime? = null,

    @Column(length = 300)
    var lastMessagePreview: String? = null
) : BaseEntity() {

    val isGroup: Boolean get() = type == ChatRoomType.GROUP

    fun convertToGroup() {
        type = ChatRoomType.GROUP
    }
}
