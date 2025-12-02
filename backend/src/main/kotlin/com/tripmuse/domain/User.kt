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
    var profileImageUrl: String? = null
) : BaseEntity() {

    fun updateProfile(nickname: String, profileImageUrl: String?) {
        this.nickname = nickname
        this.profileImageUrl = profileImageUrl
    }
}
