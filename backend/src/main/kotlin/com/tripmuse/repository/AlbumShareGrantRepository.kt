package com.tripmuse.repository

import com.tripmuse.domain.AlbumShareGrant
import org.springframework.data.jpa.repository.JpaRepository

interface AlbumShareGrantRepository : JpaRepository<AlbumShareGrant, Long> {
    fun existsByAlbumIdAndUserId(albumId: Long, userId: Long): Boolean
    fun countByAlbumId(albumId: Long): Long
    fun deleteByAlbumId(albumId: Long)
}
