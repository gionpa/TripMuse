package com.tripmuse.repository

import com.tripmuse.domain.Friendship
import com.tripmuse.domain.FriendshipStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface FriendshipRepository : JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE f.user.id = :userId AND f.status = :status")
    fun findByUserIdAndStatus(
        @Param("userId") userId: Long,
        @Param("status") status: FriendshipStatus
    ): List<Friendship>

    @Query("SELECT f FROM Friendship f WHERE f.user.id = :userId AND f.friend.id = :friendId")
    fun findByUserIdAndFriendId(
        @Param("userId") userId: Long,
        @Param("friendId") friendId: Long
    ): Optional<Friendship>

    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE f.user.id = :userId AND f.friend.id = :friendId")
    fun existsByUserIdAndFriendId(
        @Param("userId") userId: Long,
        @Param("friendId") friendId: Long
    ): Boolean
}
