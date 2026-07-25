package com.tripmuse.repository

import com.tripmuse.domain.UserPresence
import org.springframework.data.jpa.repository.JpaRepository

interface UserPresenceRepository : JpaRepository<UserPresence, Long> {
    fun findByUserId(userId: Long): UserPresence?
    fun findByUserIdIn(userIds: Collection<Long>): List<UserPresence>
}
