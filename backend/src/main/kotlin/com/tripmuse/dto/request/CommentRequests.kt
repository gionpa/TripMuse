package com.tripmuse.dto.request

import jakarta.validation.constraints.NotBlank

data class CreateCommentRequest(
    @field:NotBlank(message = "Content is required")
    val content: String
)

data class UpdateCommentRequest(
    @field:NotBlank(message = "Content is required")
    val content: String
)
