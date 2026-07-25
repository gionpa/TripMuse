package com.tripmuse.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 사용자가 마지막으로 공유한 현재 위치. 사용자당 한 행만 유지된다.
 */
@Entity
@Table(
    name = "user_locations",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_location_user", columnNames = ["user_id"])]
)
class UserLocation(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    /** 위치 정확도(미터). 기기가 제공하지 않으면 null */
    var accuracy: Float? = null,

    @Column(nullable = false)
    var recordedAt: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    fun update(latitude: Double, longitude: Double, accuracy: Float?) {
        this.latitude = latitude
        this.longitude = longitude
        this.accuracy = accuracy
        this.recordedAt = LocalDateTime.now()
    }
}
