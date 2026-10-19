package com.vidhura.ai.recommendation.benchmark;

import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RecommendationQualityBenchmarkTest {

    @Mock
    private RecommendationService recommendationService;

    private RecommendationQualityBenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        benchmarkService = new RecommendationQualityBenchmarkService(recommendationService);
    }

    @Test
    void testBenchmarkSuiteRunsAndVerifiesConstraints() {
        StructuredPolicy p1 = StructuredPolicy.builder()
                .id("p1")
                .policyCode("HDFC_OPTIMA_SECURE")
                .policyName("HDFC ERGO Optima Secure")
                .provider("HDFC ERGO")
                .active(true)
                .build();

        when(recommendationService.getCatalog()).thenReturn(List.of(p1));

        // Normal compliant card
        RecommendationResponseDto.RankedPolicyCard card = RecommendationResponseDto.RankedPolicyCard.builder()
                .policyId("p1")
                .policyName("HDFC ERGO Optima Secure")
                .provider("HDFC ERGO")
                .overallMatchScore(94.5)
                .calculatedAnnualPremium(14500.0)
                .outOfPocketRisk(RecommendationResult.OutOfPocketRiskAnalysis.builder()
                        .estimatedExposurePercentage(5.0)
                        .riskLevel("LOW")
                        .build())
                .supportingEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .verbatimText("Single private room")
                                .build()
                ))
                .build();

        RecommendationResponseDto standardResponse = RecommendationResponseDto.builder()
                .eligiblePoliciesCount(1)
                .rankedPolicies(List.of(card))
                .build();

        // Empty response for Scenario 7 (Impossible Constraints)
        RecommendationResponseDto emptyResponse = RecommendationResponseDto.builder()
                .eligiblePoliciesCount(0)
                .rankedPolicies(List.of())
                .build();

        when(recommendationService.generateRecommendations(any(), any()))
                .thenAnswer(inv -> {
                    com.vidhura.ai.recommendation.dto.RecommendationRequestDto req = inv.getArgument(1);
                    if (req.getPrimaryAge() != null && req.getPrimaryAge() > 80) {
                        return emptyResponse;
                    }
                    return standardResponse;
                });

        RecommendationQualityReport report = benchmarkService.runBenchmarkSuite();

        assertNotNull(report);
        assertEquals(12, report.getTotalBenchmarkScenarios());
        assertEquals(12, report.getPassedScenarios());
        assertEquals(0.0, report.getMandatoryConstraintViolationRate(), "Mandatory constraint violation rate must be 0%");
        assertEquals(100.0, report.getClaimMathAccuracyRate(), "Claim mathematical precision must be 100%");
        assertTrue(report.isRegressionPassed(), "Quality regression benchmark must pass");
        assertTrue(report.getOverallQualityScore() >= 90.0, "Overall quality score should be >= 90%");
    }

    @Test
    void testBenchmarkCatchesBudgetConstraintViolation() {
        // Return a policy with premium 50,000 for a strict budget of 18,000
        RecommendationResponseDto.RankedPolicyCard overBudgetCard = RecommendationResponseDto.RankedPolicyCard.builder()
                .policyId("p_expensive")
                .policyName("Ultra Luxury Cover")
                .calculatedAnnualPremium(50000.0)
                .build();

        RecommendationResponseDto violationResponse = RecommendationResponseDto.builder()
                .eligiblePoliciesCount(1)
                .rankedPolicies(List.of(overBudgetCard))
                .build();

        when(recommendationService.generateRecommendations(any(), any()))
                .thenReturn(violationResponse);

        RecommendationQualityReport report = benchmarkService.runBenchmarkSuite();

        assertNotNull(report);
        // Scenario 4 has a strict budget of 18,000, so it must be flagged
        assertTrue(report.getMandatoryConstraintViolationRate() > 0.0, "Must detect budget constraint violation");
        assertFalse(report.isRegressionPassed(), "Must fail regression if mandatory constraints are violated");
    }
}
