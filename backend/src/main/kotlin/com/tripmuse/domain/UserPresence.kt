package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 사용자 접속 상태. 앱이 화면에 보이는 동안 주기적으로 heartbeat를 받아 갱신한다.
 * lastSeenAt이 ONLINE_THRESHOLD 이내면 온라인으로 본다.
 */
@Entity
@Table(
    name = "user_presences",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_presence_user", columnNames = ["user_id"])],
    indexes = [Index(name = "idx_user_presences_last_seen", columnList = "lastSeenAt")]
)
class UserPresence(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var lastSeenAt: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    fun touch() {
        lastSeenAt = LocalDateTime.now()
    }

    companion object {
        /** heartbeat 주기(45초)의 3배 — 한두 번 놓쳐도 바로 오프라인이 되지 않게 한다 */
        val ONLINE_THRESHOLD: java.time.Duration = java.time.Duration.ofMinutes(2)

        fun isOnline(lastSeenAt: LocalDateTime?): Boolean {
            if (lastSeenAt == null) return false
            return lastSeenAt.isAfter(LocalDateTime.now().minus(ONLINE_THRESHOLD))
        }
    }
}
