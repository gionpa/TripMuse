package com.tripmuse.dto.response

import com.tripmuse.domain.Memo
import java.time.LocalDateTime

data class MemoResponse(
    val id: Long,
    val content: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(memo: Memo): MemoResponse {
            return MemoResponse(
                id = memo.id,
                content = memo.content,
                createdAt = memo.createdAt,
                updatedAt = memo.updatedAt
            )
        }
    }
}
