package com.tripmuse.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 사용자가 미디어의 댓글을 읽은 시점을 추적하는 엔티티
 * 미디어별로 마지막으로 읽은 시간을 저장하여, 그 이후에 작성된 댓글을 "읽지 않은 댓글"로 판단
 */
@Entity
@Table(
    name = "comment_reads",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "media_id"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
class CommentRead(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    val media: Media,

    @Column(nullable = false)
    var lastReadAt: LocalDateTime = LocalDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun updateLastReadAt() {
        this.lastReadAt = LocalDateTime.now()
    }
}
