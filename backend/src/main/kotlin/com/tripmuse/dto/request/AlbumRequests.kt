package com.tripmuse.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateAlbumRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title must be less than 200 characters")
    val title: String,

    @field:Size(max = 300, message = "Location must be less than 300 characters")
    val location: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val coverImageUrl: String? = null,
    val isPublic: Boolean = false
)

data class UpdateAlbumRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title must be less than 200 characters")
    val title: String,

    @field:Size(max = 300, message = "Location must be less than 300 characters")
    val location: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val coverImageUrl: String? = null,
    val isPublic: Boolean = false
)

data class ReorderAlbumsRequest(
    val albumIds: List<Long>
)
