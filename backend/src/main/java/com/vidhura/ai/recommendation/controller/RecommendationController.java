package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.recommendation.dto.GuidanceDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.service.RecommendationService;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API endpoints for health insurance recommendation feature.
 *
 * Endpoints:
 * POST   /api/recommend           — Generate personalized recommendations from questionnaire
 * POST   /api/recommend/guidance  — Get dynamic sum-insured and budget guidance
 * GET    /api/recommend/history   — List past recommendation results
 * GET    /api/recommend/{id}      — Get specific recommendation detail
 * GET    /api/recommend/catalog   — List available structured policies (catalog)
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    private User getActiveUser(User user) {
        return (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
    }

    /**
     * Generate personalized health insurance recommendations.
     * Accepts the progressive questionnaire DTO and returns ranked policies
     * with limitations, exclusions, OOP analysis, and evidence citations.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationResponseDto>> generateRecommendations(
            @Valid @RequestBody RecommendationRequestDto request,
            @AuthenticationPrincipal User user) {

        User activeUser = getActiveUser(user);
        RecommendationResponseDto response =
                recommendationService.generateRecommendations(activeUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Dynamic guidance endpoint for "Help Me Decide" —
     * returns recommended sum insured, estimated premium range, and life-stage advice.
     */
    @PostMapping("/guidance")
    public ResponseEntity<ApiResponse<GuidanceDto.GuidanceResponse>> getGuidance(
            @Valid @RequestBody GuidanceDto.GuidanceRequest request) {

        GuidanceDto.GuidanceResponse guidance = recommendationService.getGuidance(request);
        return ResponseEntity.ok(ApiResponse.ok(guidance));
    }

    /**
     * List past recommendations for the authenticated user.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<RecommendationResult>>> getHistory(
            @AuthenticationPrincipal User user) {

        User activeUser = getActiveUser(user);
        List<RecommendationResult> history =
                recommendationService.getUserRecommendations(activeUser.getId());

        return ResponseEntity.ok(ApiResponse.ok(history, history.size()));
    }

    /**
     * Get a specific recommendation result by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecommendationResult>> getRecommendation(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {

        User activeUser = getActiveUser(user);
        RecommendationResult result =
                recommendationService.getRecommendationById(id, activeUser.getId());

        if (result == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("Recommendation not found"));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * List all active policies in the structured catalog (for transparency / browse).
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<StructuredPolicy>>> getCatalog() {
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        return ResponseEntity.ok(ApiResponse.ok(catalog, catalog.size()));
    }
}
