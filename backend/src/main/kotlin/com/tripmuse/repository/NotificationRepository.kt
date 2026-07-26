package com.tripmuse.repository

import com.tripmuse.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findTop50ByRecipientUserIdOrderByCreatedAtDesc(recipientUserId: Long): List<Notification>

    fun countByRecipientUserIdAndReadFalse(recipientUserId: Long): Long

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientUserId = :userId AND n.read = false")
    fun markAllRead(@Param("userId") userId: Long): Int
}
