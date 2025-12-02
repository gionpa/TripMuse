package com.tripmuse.domain

import jakarta.persistence.*

@Entity
@Table(name = "memos")
class Memo(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false, unique = true)
    val media: Media,

    @Column(columnDefinition = "TEXT")
    var content: String
) : BaseEntity() {

    fun updateContent(content: String) {
        this.content = content
    }
}
