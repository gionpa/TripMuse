package com.tripmuse.service

import com.tripmuse.domain.Album
import com.tripmuse.dto.request.CreateAlbumRequest
import com.tripmuse.dto.request.UpdateAlbumRequest
import com.tripmuse.dto.response.AlbumDetailResponse
import com.tripmuse.dto.response.AlbumListResponse
import com.tripmuse.dto.response.AlbumResponse
import com.tripmuse.exception.ForbiddenException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.repository.AlbumRepository
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.MediaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlbumService(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val commentRepository: CommentRepository,
    private val userService: UserService
) {
    fun getAlbumsByUser(userId: Long): AlbumListResponse {
        val albums = albumRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val albumResponses = albums.map { album ->
            val mediaCount = mediaRepository.countByAlbumId(album.id)
            AlbumResponse.from(album, mediaCount)
        }
        return AlbumListResponse(albumResponses)
    }

    fun getAlbumDetail(albumId: Long, userId: Long): AlbumDetailResponse {
        val album = findAlbumById(albumId)

        // Check access permission
        if (!album.isPublic && album.user.id != userId) {
            throw ForbiddenException("Access denied to this album")
        }

        val mediaCount = mediaRepository.countByAlbumId(albumId)
        val commentCount = album.mediaList.sumOf { commentRepository.countByMediaId(it.id) }

        return AlbumDetailResponse.from(album, mediaCount, commentCount)
    }

    @Transactional
    fun createAlbum(userId: Long, request: CreateAlbumRequest): AlbumResponse {
        val user = userService.findUserById(userId)

        val album = Album(
            user = user,
            title = request.title,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            startDate = request.startDate,
            endDate = request.endDate,
            coverImageUrl = request.coverImageUrl,
            isPublic = request.isPublic
        )

        val savedAlbum = albumRepository.save(album)
        return AlbumResponse.from(savedAlbum, 0)
    }

    @Transactional
    fun updateAlbum(albumId: Long, userId: Long, request: UpdateAlbumRequest): AlbumResponse {
        val album = findAlbumByIdAndUserId(albumId, userId)

        // 요청에 값이 있으면 업데이트, 없으면 기존 값 유지
        album.update(
            title = request.title,
            location = request.location ?: album.location,
            latitude = request.latitude ?: album.latitude,
            longitude = request.longitude ?: album.longitude,
            startDate = request.startDate ?: album.startDate,
            endDate = request.endDate ?: album.endDate,
            coverImageUrl = request.coverImageUrl ?: album.coverImageUrl,
            isPublic = request.isPublic
        )

        val mediaCount = mediaRepository.countByAlbumId(albumId)
        return AlbumResponse.from(album, mediaCount)
    }

    @Transactional
    fun deleteAlbum(albumId: Long, userId: Long) {
        val album = findAlbumByIdAndUserId(albumId, userId)
        albumRepository.delete(album)
    }

    fun findAlbumById(albumId: Long): Album {
        return albumRepository.findById(albumId)
            .orElseThrow { NotFoundException("Album not found: $albumId") }
    }

    fun findAlbumByIdAndUserId(albumId: Long, userId: Long): Album {
        return albumRepository.findByUserIdAndId(userId, albumId)
            ?: throw NotFoundException("Album not found or access denied: $albumId")
    }

    @Transactional
    fun updateCoverImageIfEmpty(albumId: Long, thumbnailUrl: String?) {
        if (thumbnailUrl == null) return

        val album = findAlbumById(albumId)
        if (album.coverImageUrl == null) {
            album.updateCoverImage(thumbnailUrl)
        }
    }
}
