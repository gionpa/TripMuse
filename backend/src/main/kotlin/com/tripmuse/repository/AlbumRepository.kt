package com.tripmuse.repository

import com.tripmuse.domain.Album
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AlbumRepository : JpaRepository<Album, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Album>

    @Query("SELECT a FROM Album a WHERE a.isPublic = true ORDER BY a.createdAt DESC")
    fun findAllPublicAlbums(): List<Album>

    fun findByUserIdAndId(userId: Long, albumId: Long): Album?

    fun countByUserId(userId: Long): Long
}
