package com.tripmuse.repository

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MediaRepository : JpaRepository<Media, Long> {
    fun findByAlbumIdOrderByTakenAtDesc(albumId: Long): List<Media>
    fun findByAlbumIdAndTypeOrderByTakenAtDesc(albumId: Long, type: MediaType): List<Media>
    fun countByAlbumId(albumId: Long): Long
    fun findByAlbumIdAndIsCoverTrue(albumId: Long): Media?

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.album.id = :albumId ORDER BY m.takenAt DESC")
    fun findByAlbumIdWithAlbumOrderByTakenAtDesc(albumId: Long): List<Media>

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.album.id = :albumId AND m.type = :type ORDER BY m.takenAt DESC")
    fun findByAlbumIdAndTypeWithAlbumOrderByTakenAtDesc(albumId: Long, type: MediaType): List<Media>

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.id = :mediaId")
    fun findByIdWithAlbum(mediaId: Long): Media?
}
