package com.tripmuse.service

import com.tripmuse.domain.Memo
import com.tripmuse.dto.request.UpdateMemoRequest
import com.tripmuse.dto.response.MemoResponse
import com.tripmuse.repository.MemoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemoService(
    private val memoRepository: MemoRepository,
    private val mediaService: MediaService,
    private val albumService: AlbumService
) {
    fun getMemo(mediaId: Long, userId: Long): MemoResponse? {
        val media = mediaService.findMediaById(mediaId)
        albumService.getAlbumDetail(media.album.id, userId)

        return memoRepository.findByMediaId(mediaId)
            .map { MemoResponse.from(it) }
            .orElse(null)
    }

    @Transactional
    fun createOrUpdateMemo(mediaId: Long, userId: Long, request: UpdateMemoRequest): MemoResponse {
        val media = mediaService.findMediaById(mediaId)
        albumService.findAlbumByIdAndUserId(media.album.id, userId)

        val memo = memoRepository.findByMediaId(mediaId)
            .orElseGet {
                val newMemo = Memo(media = media, content = request.content)
                media.memo = newMemo
                newMemo
            }

        memo.updateContent(request.content)
        val savedMemo = memoRepository.save(memo)

        return MemoResponse.from(savedMemo)
    }

    @Transactional
    fun deleteMemo(mediaId: Long, userId: Long) {
        val media = mediaService.findMediaById(mediaId)
        albumService.findAlbumByIdAndUserId(media.album.id, userId)

        memoRepository.findByMediaId(mediaId).ifPresent {
            memoRepository.delete(it)
        }
    }
}
