package com.tripmuse.repository

import com.tripmuse.domain.Comment
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByMediaIdOrderByCreatedAtAsc(mediaId: Long): List<Comment>
    fun countByMediaId(mediaId: Long): Long
}
