package com.tripmuse.service

import com.tripmuse.domain.Album
import com.tripmuse.domain.AlbumShareGrant
import com.tripmuse.domain.AlbumVisibility
import com.tripmuse.dto.request.CreateAlbumRequest
import com.tripmuse.dto.request.UpdateAlbumRequest
import com.tripmuse.dto.response.AlbumDetailResponse
import com.tripmuse.dto.response.AlbumListResponse
import com.tripmuse.dto.response.AlbumResponse
import com.tripmuse.dto.response.ShareLinkResponse
import com.tripmuse.dto.response.ShareRevokeResponse
import com.tripmuse.dto.response.ShareResolveResponse
import com.tripmuse.exception.ForbiddenException
import com.tripmuse.exception.NotFoundException
import com.tripmuse.domain.FriendshipStatus
import com.tripmuse.repository.AlbumRepository
import com.tripmuse.repository.AlbumShareGrantRepository
import com.tripmuse.repository.CommentRepository
import com.tripmuse.repository.FriendshipRepository
import com.tripmuse.repository.MediaRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AlbumService(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val commentRepository: CommentRepository,
    private val friendshipRepository: FriendshipRepository,
    private val shareGrantRepository: AlbumShareGrantRepository,
    private val userService: UserService,
    @Value("\${tripmuse.public-base-url}") private val publicBaseUrl: String
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

        // 소유자에게 공유 상태를 보여주기 위해 링크로 들어온 사람 수를 함께 센다
        val sharedViewerCount = if (album.user.id == userId && album.shareToken != null) {
            shareGrantRepository.countByAlbumId(albumId)
        } else {
            0
        }

        // Use @Formula calculated mediaCount
        return AlbumDetailResponse.from(album, commentCount, userId, sharedViewerCount)
    }

    private fun canAccessAlbum(album: Album, userId: Long): Boolean {
        // Owner can always access
        if (album.user.id == userId) return true

        // 공유 링크로 열람 권한을 부여받은 사용자
        if (shareGrantRepository.existsByAlbumIdAndUserId(album.id, userId)) return true

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

    /**
     * 공유 링크 발급 (이미 있으면 기존 링크 반환 — 멱등)
     */
    @Transactional
    fun createShareLink(albumId: Long, userId: Long): ShareLinkResponse {
        val album = findAlbumByIdAndUserId(albumId, userId)
        val token = album.shareToken ?: UUID.randomUUID().toString().replace("-", "").also {
            album.shareToken = it
        }
        return ShareLinkResponse(shareToken = token, shareUrl = "$publicBaseUrl/share/$token")
    }

    /**
     * 공유 링크 해제 — 링크로 부여된 열람 권한도 함께 회수한다.
     * 해제할 링크가 없었으면 revoked=false로 알려, 앱이 "해제됨"이라고 잘못 말하지 않게 한다.
     */
    @Transactional
    fun revokeShareLink(albumId: Long, userId: Long): ShareRevokeResponse {
        val album = findAlbumByIdAndUserId(albumId, userId)
        if (album.shareToken == null) {
            return ShareRevokeResponse(revoked = false, revokedViewerCount = 0)
        }
        val revokedViewerCount = shareGrantRepository.countByAlbumId(albumId)
        album.shareToken = null
        shareGrantRepository.deleteByAlbumId(albumId)
        return ShareRevokeResponse(revoked = true, revokedViewerCount = revokedViewerCount)
    }

    /**
     * 공유 토큰 리졸브: 앨범을 찾고, 요청 사용자에게 열람 권한을 부여한다.
     */
    @Transactional
    fun resolveShareLink(token: String, userId: Long): ShareResolveResponse {
        val album = albumRepository.findByShareToken(token)
            ?: throw NotFoundException("유효하지 않거나 만료된 공유 링크입니다")

        if (album.user.id != userId && !shareGrantRepository.existsByAlbumIdAndUserId(album.id, userId)) {
            val user = userService.findUserById(userId)
            shareGrantRepository.save(AlbumShareGrant(album = album, user = user))
        }

        return ShareResolveResponse(albumId = album.id, title = album.title)
    }

    /**
     * 랜딩 페이지용 (인증 없음) — 토큰으로 앨범 조회, 없으면 null
     */
    fun findAlbumByShareToken(token: String): Album? = albumRepository.findByShareToken(token)

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
        shareGrantRepository.deleteByAlbumId(albumId)
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
