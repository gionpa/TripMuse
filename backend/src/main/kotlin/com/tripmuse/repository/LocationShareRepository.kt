package com.tripmuse.repository

import com.tripmuse.domain.LocationShare
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LocationShareRepository : JpaRepository<LocationShare, Long> {

    @Query("SELECT ls FROM LocationShare ls WHERE ls.requester.id = :userId OR ls.recipient.id = :userId")
    fun findAllInvolvingUser(userId: Long): List<LocationShare>

    @Query("""
        SELECT ls FROM LocationShare ls
        WHERE (ls.requester.id = :userId AND ls.recipient.id = :otherId)
           OR (ls.requester.id = :otherId AND ls.recipient.id = :userId)
    """)
    fun findByPair(userId: Long, otherId: Long): LocationShare?

    fun findByRequesterIdAndRecipientId(requesterId: Long, recipientId: Long): LocationShare?
}
