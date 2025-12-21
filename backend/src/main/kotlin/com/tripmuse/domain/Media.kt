package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class MediaType {
    IMAGE, VIDEO
}

enum class UploadStatus {
    PROCESSING, COMPLETED, FAILED
}

@Entity
@Table(
    name = "media",
    indexes = [
        Index(name = "idx_media_album_id", columnList = "album_id"),
        Index(name = "idx_media_taken_at", columnList = "taken_at"),
        Index(name = "idx_media_album_taken", columnList = "album_id, taken_at DESC")
    ]
)
class Media(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    val album: Album,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: MediaType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'COMPLETED'")
    var uploadStatus: UploadStatus = UploadStatus.PROCESSING,

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

    @OneToMany(mappedBy = "media", cascade = [CascadeType.ALL], orphanRemoval = true)
    val commentReads: MutableList<CommentRead> = mutableListOf()

    fun updateThumbnail(thumbnailPath: String) {
        this.thumbnailPath = thumbnailPath
    }

    fun setAsCover(isCover: Boolean) {
        this.isCover = isCover
    }

    fun markUploadCompleted(thumbnailPath: String?) {
        this.uploadStatus = UploadStatus.COMPLETED
        thumbnailPath?.let { this.thumbnailPath = it }
    }

    fun markUploadFailed() {
        this.uploadStatus = UploadStatus.FAILED
    }
}
