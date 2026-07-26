package com.tripmuse.domain

import jakarta.persistence.*

enum class NotificationType {
    ALBUM_MEDIA_ADDED,  // 공유 앨범에 새 사진
    ALBUM_COMMENT       // 공유 앨범에 새 댓글
}

/**
 * 서버가 만들어 특정 사용자에게 쌓아두는 알림.
 * (친구 접속 알림은 클라이언트가 감지해 기기 로컬에만 남기므로 여기 저장되지 않는다)
 */
@Entity
@Table(
    name = "notifications",
    indexes = [Index(name = "idx_notifications_recipient", columnList = "recipient_user_id, created_at DESC")]
)
class Notification(
    @Column(name = "recipient_user_id", nullable = false)
    val recipientUserId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 500)
    val body: String,

    /** 눌렀을 때 이동할 앨범 (해당되면) */
    @Column(name = "album_id")
    val albumId: Long? = null,

    @Column(nullable = false)
    var read: Boolean = false
) : BaseEntity()
