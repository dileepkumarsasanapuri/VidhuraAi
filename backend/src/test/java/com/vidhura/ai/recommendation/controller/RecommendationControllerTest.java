package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.GuidanceDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    private RecommendationController recommendationController;

    @BeforeEach
    void setUp() {
        recommendationController = new RecommendationController(recommendationService);
    }

    @Test
    void testGenerateRecommendationsEndpoint() {
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(30)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .annualBudget(25000.0)
                .budgetType("PREFERRED")
                .primaryPriority("BALANCED")
                .build();

        RecommendationResponseDto expectedResponse = RecommendationResponseDto.builder()
                .recommendationId("rec-101")
                .rankedPolicies(new ArrayList<>())
                .totalPoliciesEvaluated(7)
                .eligiblePoliciesCount(5)
                .build();

        when(recommendationService.generateRecommendations(anyString(), any(RecommendationRequestDto.class)))
                .thenReturn(expectedResponse);

        User testUser = User.builder().id("usr-1").email("test@example.com").build();
        ResponseEntity<ApiResponse<RecommendationResponseDto>> responseEntity =
                recommendationController.generateRecommendations(request, testUser);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(responseEntity.getBody().getSuccess()));
        assertEquals("rec-101", responseEntity.getBody().getData().getRecommendationId());
    }

    @Test
    void testGetGuidanceEndpoint() {
        GuidanceDto.GuidanceRequest gr = GuidanceDto.GuidanceRequest.builder()
                .primaryAge(28)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Bengaluru")
                .totalMembers(1)
                .build();

        GuidanceDto.GuidanceResponse expected = GuidanceDto.GuidanceResponse.builder()
                .recommendedSumInsured(1000000L)
                .formattedRecommendedSumInsured("₹10 Lakh")
                .pricingZone("Zone 1")
                .zone("Zone 1")
                .build();

        when(recommendationService.getGuidance(any(GuidanceDto.GuidanceRequest.class)))
                .thenReturn(expected);

        ResponseEntity<ApiResponse<GuidanceDto.GuidanceResponse>> responseEntity =
                recommendationController.getGuidance(gr);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertEquals("Zone 1", responseEntity.getBody().getData().getZone());
    }

    @Test
    void testGetHistoryEndpoint() {
        RecommendationResult res = RecommendationResult.builder().id("run-1").userId("usr-1").build();
        when(recommendationService.getUserRecommendations(eq("usr-1")))
                .thenReturn(List.of(res));

        User testUser = User.builder().id("usr-1").build();
        ResponseEntity<ApiResponse<List<RecommendationResult>>> historyResp =
                recommendationController.getHistory(testUser);

        assertNotNull(historyResp);
        assertEquals(200, historyResp.getStatusCode().value());
        assertEquals(1, historyResp.getBody().getData().size());
    }

    @Test
    void testGetRecommendationByIdFound() {
        RecommendationResult res = RecommendationResult.builder().id("rec-99").userId("usr-1").build();
        when(recommendationService.getRecommendationById(eq("rec-99"), eq("usr-1")))
                .thenReturn(res);

        User testUser = User.builder().id("usr-1").build();
        ResponseEntity<ApiResponse<RecommendationResult>> resp =
                recommendationController.getRecommendation("rec-99", testUser);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("rec-99", resp.getBody().getData().getId());
    }

    @Test
    void testGetRecommendationByIdNotFound() {
        when(recommendationService.getRecommendationById(eq("unknown"), anyString()))
                .thenReturn(null);

        User testUser = User.builder().id("usr-1").build();
        ResponseEntity<ApiResponse<RecommendationResult>> resp =
                recommendationController.getRecommendation("unknown", testUser);

        assertEquals(404, resp.getStatusCode().value());
        assertFalse(Boolean.TRUE.equals(resp.getBody().getSuccess()));
    }

    @Test
    void testGetCatalogEndpoint() {
        StructuredPolicy p = StructuredPolicy.builder().policyCode("HDFC_OPTIMA").build();
        when(recommendationService.getCatalog()).thenReturn(List.of(p));

        ResponseEntity<ApiResponse<List<StructuredPolicy>>> resp =
                recommendationController.getCatalog();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().getData().size());
    }
}
