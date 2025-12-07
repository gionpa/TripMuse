package com.tripmuse.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "friendships",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "friend_id"])
    ]
)
class Friendship(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    val friend: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FriendshipStatus = FriendshipStatus.ACCEPTED
) : BaseEntity()

enum class FriendshipStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
