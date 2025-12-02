package com.tripmuse.repository

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import org.springframework.data.jpa.repository.JpaRepository

interface MediaRepository : JpaRepository<Media, Long> {
    fun findByAlbumIdOrderByTakenAtDesc(albumId: Long): List<Media>
    fun findByAlbumIdAndTypeOrderByTakenAtDesc(albumId: Long, type: MediaType): List<Media>
    fun countByAlbumId(albumId: Long): Long
    fun findByAlbumIdAndIsCoverTrue(albumId: Long): Media?
}
