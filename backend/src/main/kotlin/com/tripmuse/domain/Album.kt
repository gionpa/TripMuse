package com.tripmuse.domain

import jakarta.persistence.*
import org.hibernate.annotations.Formula
import java.time.LocalDate

@Entity
@Table(
    name = "albums",
    indexes = [
        Index(name = "idx_albums_user_id", columnList = "user_id"),
        Index(name = "idx_albums_created_at", columnList = "created_at DESC")
    ]
)
class Album(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(length = 300)
    var location: String? = null,

    @Column(precision = 10)
    var latitude: Double? = null,

    @Column(precision = 11)
    var longitude: Double? = null,

    var startDate: LocalDate? = null,

    var endDate: LocalDate? = null,

    @Column(length = 500)
    var coverImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    @org.hibernate.annotations.ColumnDefault("'PRIVATE'")
    var visibility: AlbumVisibility? = AlbumVisibility.PRIVATE,

    @Column(nullable = false)
    var displayOrder: Int = 0
) : BaseEntity() {

    @OneToMany(mappedBy = "album", cascade = [CascadeType.ALL], orphanRemoval = true)
    val mediaList: MutableList<Media> = mutableListOf()

    @Formula("(SELECT COUNT(*) FROM media m WHERE m.album_id = id)")
    val mediaCount: Long = 0

    fun update(
        title: String,
        location: String?,
        latitude: Double?,
        longitude: Double?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        coverImageUrl: String?,
        visibility: AlbumVisibility
    ) {
        this.title = title
        this.location = location
        this.latitude = latitude
        this.longitude = longitude
        this.startDate = startDate
        this.endDate = endDate
        this.coverImageUrl = coverImageUrl
        this.visibility = visibility
    }

    fun addMedia(media: Media) {
        mediaList.add(media)
    }

    fun removeMedia(media: Media) {
        mediaList.remove(media)
    }

    fun updateCoverImage(coverImageUrl: String?) {
        this.coverImageUrl = coverImageUrl
    }
}
