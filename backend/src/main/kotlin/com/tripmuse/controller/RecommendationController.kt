package com.tripmuse.controller

import com.tripmuse.dto.request.AnalyzeMediaRequest
import com.tripmuse.dto.response.RecommendationResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.RecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(
    private val recommendationService: RecommendationService
) {
    @PostMapping("/analyze")
    fun analyzeMedia(
        @AuthenticationPrincipal user: CustomUserDetails,
        @RequestBody request: AnalyzeMediaRequest
    ): ResponseEntity<RecommendationResponse> {
        val recommendations = recommendationService.analyzeAndRecommend(user.id, request)
        return ResponseEntity.ok(recommendations)
    }
}
