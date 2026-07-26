package com.tripmuse.domain

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false, length = 100)
    var nickname: String,

    @Column(length = 500)
    var profileImageUrl: String? = null,

    @Column(length = 255)
    var password: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: Provider = Provider.LOCAL,

    @Column(length = 255)
    var providerId: String? = null,

    // 메타버스 스테이지에서 쓰는 캐릭터 스타일 키 (null이면 앱이 기본 스타일 사용)
    @Column(length = 30)
    var characterStyle: String? = null
) : BaseEntity() {

    fun updateProfile(nickname: String, profileImageUrl: String?) {
        this.nickname = nickname
        this.profileImageUrl = profileImageUrl
    }
}
