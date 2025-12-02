package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "albums")
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

    @Column(nullable = false)
    var isPublic: Boolean = false
) : BaseEntity() {

    @OneToMany(mappedBy = "album", cascade = [CascadeType.ALL], orphanRemoval = true)
    val mediaList: MutableList<Media> = mutableListOf()

    fun update(
        title: String,
        location: String?,
        latitude: Double?,
        longitude: Double?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        coverImageUrl: String?,
        isPublic: Boolean
    ) {
        this.title = title
        this.location = location
        this.latitude = latitude
        this.longitude = longitude
        this.startDate = startDate
        this.endDate = endDate
        this.coverImageUrl = coverImageUrl
        this.isPublic = isPublic
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
