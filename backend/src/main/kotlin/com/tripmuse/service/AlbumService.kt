package com.tripmuse.service

import com.tripmuse.domain.Album
import com.tripmuse.domain.AlbumVisibility
import com.tripmuse.dto.request.CreateAlbumRequest
import com.tripmuse.dto.request.UpdateAlbumRequest
import com.tripmuse.dto.response.AlbumDetailResponse
import com.tripmuse.dto.response.AlbumListResponse
import com.tripmuse.dto.response.AlbumResponse
import com.tripmuse.exception.ForbiddenException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.repository.AlbumRepository
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.MediaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlbumService(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val commentRepository: CommentRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userService: UserService
) {
    fun getAlbumsByUser(userId: Long): AlbumListResponse {
        // 내 앨범 조회
        val myAlbums = albumRepository.findByUserIdOrderByDisplayOrderAsc(userId)

        // 친구의 "친구에게 공개" 앨범 조회
        val friendIds = friendshipRepository.findFriendIdsByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
        val friendAlbums = if (friendIds.isNotEmpty()) {
            albumRepository.findVisibleAlbumsByFriendIds(friendIds)
        } else {
            emptyList()
        }

        // 내 앨범 + 친구 앨범 합치기 (내 앨범 우선, 친구 앨범은 최신순)
        val allAlbums = myAlbums + friendAlbums
        val albumResponses = allAlbums.map { album -> AlbumResponse.from(album, userId) }
        return AlbumListResponse(albumResponses)
    }

    fun getAlbumDetail(albumId: Long, userId: Long): AlbumDetailResponse {
        val album = findAlbumById(albumId)

        // Check access permission based on visibility
        if (!canAccessAlbum(album, userId)) {
            throw ForbiddenException("Access denied to this album")
        }

        // Use single query for comment count instead of N+1
        val commentCount = commentRepository.countByAlbumId(albumId)

        // Use @Formula calculated mediaCount
        return AlbumDetailResponse.from(album, commentCount, userId)
    }

    private fun canAccessAlbum(album: Album, userId: Long): Boolean {
        // Owner can always access
        if (album.user.id == userId) return true

        return when (album.visibility ?: AlbumVisibility.PRIVATE) {
            AlbumVisibility.PUBLIC -> true
            AlbumVisibility.FRIENDS_ONLY -> friendshipRepository.existsByUserIdAndFriendIdAndStatus(
                album.user.id,
                userId,
                FriendshipStatus.ACCEPTED
            )
            AlbumVisibility.PRIVATE -> false
        }
    }

    @Transactional
    fun createAlbum(userId: Long, request: CreateAlbumRequest): AlbumResponse {
        val user = userService.findUserById(userId)

        // Set displayOrder to next available order
        val nextOrder = albumRepository.findMaxDisplayOrderByUserId(userId) + 1

        val album = Album(
            user = user,
            title = request.title,
            location = request.location,
            latitude = request.latitude,
            longitude = request.longitude,
            startDate = request.startDate,
            endDate = request.endDate,
            coverImageUrl = request.coverImageUrl,
            visibility = request.visibility,
            displayOrder = nextOrder
        )

        val savedAlbum = albumRepository.save(album)
        return AlbumResponse.from(savedAlbum, 0, userId)
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
            visibility = request.visibility
        )

        // Use @Formula calculated mediaCount
        return AlbumResponse.from(album, userId)
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

    @Transactional
    fun reorderAlbums(userId: Long, albumIds: List<Long>) {
        // Verify all albums belong to the user and get them in one query
        val albums = albumRepository.findByUserIdOrderByDisplayOrderAsc(userId)
        val albumMap = albums.associateBy { it.id }

        // Verify all provided IDs are valid
        albumIds.forEachIndexed { index, albumId ->
            val album = albumMap[albumId]
                ?: throw NotFoundException("Album not found or access denied: $albumId")
            album.displayOrder = index
        }
    }
}
