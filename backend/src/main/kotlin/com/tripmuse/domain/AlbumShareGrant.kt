package com.tripmuse.domain

import jakarta.persistence.*

/**
 * 공유 링크를 통해 앨범 열람 권한을 획득한 사용자 기록.
 * 공유 링크 해제 시 함께 삭제되어 접근 권한도 회수된다.
 */
@Entity
@Table(
    name = "album_share_grants",
    uniqueConstraints = [UniqueConstraint(name = "uk_share_grant_album_user", columnNames = ["album_id", "user_id"])],
    indexes = [Index(name = "idx_share_grants_user_id", columnList = "user_id")]
)
class AlbumShareGrant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    val album: Album,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User
) : BaseEntity()
