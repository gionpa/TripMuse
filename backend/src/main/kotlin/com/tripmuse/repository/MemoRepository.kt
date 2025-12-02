package com.tripmuse.repository

import com.tripmuse.domain.Memo
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemoRepository : JpaRepository<Memo, Long> {
    fun findByMediaId(mediaId: Long): Optional<Memo>
}
