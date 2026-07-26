package com.tripmuse.repository

import com.tripmuse.domain.AlbumShareGrant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AlbumShareGrantRepository : JpaRepository<AlbumShareGrant, Long> {
    fun existsByAlbumIdAndUserId(albumId: Long, userId: Long): Boolean
    fun countByAlbumId(albumId: Long): Long
    fun deleteByAlbumId(albumId: Long)

    /** 앨범 공유 링크로 열람 권한을 받은 사용자 id들 (알림 대상 산정용) */
    @Query("SELECT g.user.id FROM AlbumShareGrant g WHERE g.album.id = :albumId")
    fun findGranteeUserIdsByAlbumId(albumId: Long): List<Long>
}
