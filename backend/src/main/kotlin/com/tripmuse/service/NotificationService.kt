package com.tripmuse.service

import com.tripmuse.domain.Album
import com.tripmuse.domain.Notification
import com.tripmuse.domain.NotificationType
import com.tripmuse.repository.AlbumShareGrantRepository
import com.tripmuse.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 공유 앨범(공유 링크를 발급한 앨범)에서 일어난 일을, 그 링크로 함께 보는 사람들에게 알린다.
 *
 * 대상은 "소유자 + 링크로 들어온 열람자"이고, 이벤트를 일으킨 본인은 뺀다.
 * 링크를 발급하지 않은(=협업 그룹이 없는) 앨범에서는 아무 알림도 만들지 않는다.
 */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val shareGrantRepository: AlbumShareGrantRepository
) {

    /** 소유자가 공유 앨범에 사진을 올렸을 때 — 열람자들에게 알린다 */
    @Transactional
    fun notifyAlbumMediaAdded(album: Album, actorUserId: Long) {
        if (album.shareToken == null) return
        // 사진은 소유자만 올리므로 대상은 열람자들 (actor=소유자는 자연히 빠진다)
        val recipients = shareGrantRepository.findGranteeUserIdsByAlbumId(album.id).toSet() - actorUserId
        save(recipients, NotificationType.ALBUM_MEDIA_ADDED, "새 사진", "'${album.title}' 앨범에 새 사진이 추가되었어요", album.id)
    }

    /** 공유 앨범에 댓글이 달렸을 때 — 소유자와 다른 열람자들에게 알린다 */
    @Transactional
    fun notifyAlbumComment(album: Album, actorUserId: Long) {
        if (album.shareToken == null) return
        val grantees = shareGrantRepository.findGranteeUserIdsByAlbumId(album.id)
        val recipients = (grantees + album.user.id).toSet() - actorUserId
        save(recipients, NotificationType.ALBUM_COMMENT, "새 댓글", "'${album.title}' 앨범에 새 댓글이 달렸어요", album.id)
    }

    private fun save(recipients: Set<Long>, type: NotificationType, title: String, body: String, albumId: Long) {
        if (recipients.isEmpty()) return
        notificationRepository.saveAll(
            recipients.map { Notification(recipientUserId = it, type = type, title = title, body = body, albumId = albumId) }
        )
    }
}
