package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class MediaType {
    IMAGE, VIDEO
}

@Entity
@Table(name = "media")
class Media(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    val album: Album,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: MediaType,

    @Column(nullable = false, length = 500)
    val filePath: String,

    @Column(length = 500)
    var thumbnailPath: String? = null,

    @Column(nullable = false)
    var isCover: Boolean = false,

    @Column(length = 255)
    val originalFilename: String? = null,

    val fileSize: Long? = null,

    @Column(precision = 10)
    val latitude: Double? = null,

    @Column(precision = 11)
    val longitude: Double? = null,

    val takenAt: LocalDateTime? = null
) : BaseEntity() {

    @OneToOne(mappedBy = "media", cascade = [CascadeType.ALL], orphanRemoval = true)
    var memo: Memo? = null

    @OneToMany(mappedBy = "media", cascade = [CascadeType.ALL], orphanRemoval = true)
    val comments: MutableList<Comment> = mutableListOf()

    fun updateThumbnail(thumbnailPath: String) {
        this.thumbnailPath = thumbnailPath
    }

    fun setAsCover(isCover: Boolean) {
        this.isCover = isCover
    }
}
