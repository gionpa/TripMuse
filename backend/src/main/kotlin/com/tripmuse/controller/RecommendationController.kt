package com.tripmuse.controller

import com.tripmuse.dto.request.AnalyzeMediaRequest
import com.tripmuse.dto.response.RecommendationResponse
import com.tripmuse.service.RecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController(
    private val recommendationService: RecommendationService
) {
    @PostMapping("/analyze")
    fun analyzeMedia(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: AnalyzeMediaRequest
    ): ResponseEntity<RecommendationResponse> {
        val recommendations = recommendationService.analyzeAndRecommend(userId, request)
        return ResponseEntity.ok(recommendations)
    }
}
