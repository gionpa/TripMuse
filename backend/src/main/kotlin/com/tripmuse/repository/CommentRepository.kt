package com.tripmuse.repository

import com.tripmuse.domain.Comment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByMediaIdOrderByCreatedAtAsc(mediaId: Long): List<Comment>
    fun countByMediaId(mediaId: Long): Long

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.media.album.id = :albumId")
    fun countByAlbumId(albumId: Long): Long
}
