package com.tripmuse.repository

import com.tripmuse.domain.Media
import com.tripmuse.domain.MediaType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface MediaRepository : JpaRepository<Media, Long> {
    fun findByAlbumIdOrderByTakenAtDesc(albumId: Long): List<Media>
    fun findByAlbumIdAndTypeOrderByTakenAtDesc(albumId: Long, type: MediaType): List<Media>
    fun countByAlbumId(albumId: Long): Long
    fun findByAlbumIdAndIsCoverTrue(albumId: Long): Media?

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.album.id = :albumId ORDER BY m.takenAt DESC")
    fun findByAlbumIdWithAlbumOrderByTakenAtDesc(albumId: Long): List<Media>

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.album.id = :albumId AND m.type = :type ORDER BY m.takenAt DESC")
    fun findByAlbumIdAndTypeWithAlbumOrderByTakenAtDesc(albumId: Long, type: MediaType): List<Media>

    // memo는 OneToOne이라 기본 EAGER — 목록에서 미디어마다 개별 조회되던 것을
    // EntityGraph로 함께 로딩해 N+1을 없앤다 (OneToOne이라 페이징에 안전)
    @EntityGraph(attributePaths = ["memo"])
    fun findByAlbumIdOrderByTakenAtDesc(albumId: Long, pageable: Pageable): Page<Media>

    @EntityGraph(attributePaths = ["memo"])
    fun findByAlbumIdAndTypeOrderByTakenAtDesc(albumId: Long, type: MediaType, pageable: Pageable): Page<Media>

    @Query("SELECT m FROM Media m JOIN FETCH m.album WHERE m.id = :mediaId")
    fun findByIdWithAlbum(mediaId: Long): Media?

    @Query("SELECT COUNT(m) FROM Media m WHERE m.album.user.id = :userId AND m.type = :type")
    fun countByUserIdAndType(userId: Long, type: MediaType): Long

    @Query("SELECT COUNT(m) FROM Media m WHERE m.album.user.id = :userId")
    fun countByUserId(userId: Long): Long

    // 중복 체크: 파일명 + 파일크기 + 촬영시간으로 같은 사진인지 판단
    @Query("""
        SELECT COUNT(m) > 0 FROM Media m
        WHERE m.album.id = :albumId
        AND m.originalFilename = :originalFilename
        AND m.fileSize = :fileSize
    """)
    fun existsByAlbumIdAndOriginalFilenameAndFileSize(
        albumId: Long,
        originalFilename: String,
        fileSize: Long
    ): Boolean

    // 촬영시간까지 포함한 더 정확한 중복 체크
    @Query("""
        SELECT COUNT(m) > 0 FROM Media m
        WHERE m.album.id = :albumId
        AND m.fileSize = :fileSize
        AND m.takenAt = :takenAt
    """)
    fun existsByAlbumIdAndFileSizeAndTakenAt(
        albumId: Long,
        fileSize: Long,
        takenAt: java.time.LocalDateTime
    ): Boolean

    // 사용자별 파일 크기 합계 (타입별)
    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM Media m WHERE m.album.user.id = :userId AND m.type = :type")
    fun sumFileSizeByUserIdAndType(userId: Long, type: MediaType): Long

    // 사용자별 전체 파일 크기 합계
    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM Media m WHERE m.album.user.id = :userId")
    fun sumFileSizeByUserId(userId: Long): Long
}
