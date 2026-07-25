package com.tripmuse.repository

import com.tripmuse.domain.UserLocation
import org.springframework.data.jpa.repository.JpaRepository

interface UserLocationRepository : JpaRepository<UserLocation, Long> {
    fun findByUserId(userId: Long): UserLocation?
    fun deleteByUserId(userId: Long)
}
