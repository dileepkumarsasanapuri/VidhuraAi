package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.GuidanceDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private StructuredPolicyRepository structuredPolicyRepository;

    @Mock
    private RecommendationProfileRepository profileRepository;

    @Mock
    private RecommendationResultRepository resultRepository;

    private EligibilityEngine eligibilityEngine;
    private RankingEngine rankingEngine;
    private PolicyIngestionService policyIngestionService;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        eligibilityEngine = new EligibilityEngine();
        rankingEngine = new RankingEngine();
        policyIngestionService = new PolicyIngestionService(structuredPolicyRepository);

        recommendationService = new RecommendationService(
                structuredPolicyRepository,
                profileRepository,
                resultRepository,
                eligibilityEngine,
                rankingEngine,
                policyIngestionService
        );
    }

    @Test
    void testGenerateRecommendationsForYoungProfessional() {
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(28)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Bengaluru")
                .annualBudget(20000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .helpMeDecideSumInsured(false)
                .primaryPriority("BALANCED")
                .preExistingConditions(new ArrayList<>())
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-123", request);

        assertNotNull(response);
        assertNotNull(response.getRecommendationId());
        assertTrue(response.getTotalPoliciesEvaluated() >= 5);
        assertTrue(response.getEligiblePoliciesCount() > 0);
        assertFalse(response.getRankedPolicies().isEmpty());

        // Check top recommendation
        RecommendationResponseDto.RankedPolicyCard topMatch = response.getRankedPolicies().get(0);
        assertEquals(1, topMatch.getRank());
        assertTrue(topMatch.getOverallMatchScore() > 0);
        assertNotNull(topMatch.getPolicyName());
        assertNotNull(topMatch.getLimitations());
        assertNotNull(topMatch.getOutOfPocketRisk());
        assertNotNull(topMatch.getSupportingEvidence());

        // Verify DataClassification explicit segregation
        assertNotNull(topMatch.getDataClassification());
        assertEquals("VERIFIED_CONTRACTUAL_SPECIFICATION", topMatch.getDataClassification().getContractualFactsStatus());
        assertEquals("MODELED_ACTUARIAL_ESTIMATE", topMatch.getDataClassification().getPricingClassification());
        assertFalse(topMatch.getDataClassification().isBindingQuote());
        assertNotNull(topMatch.getDataClassification().getPricingDisclaimer());
        assertEquals("HYPOTHETICAL_SCENARIO_ESTIMATE", topMatch.getDataClassification().getBillSimulationClassification());
        assertEquals("DATED_REGULATORY_DISCLOSURE", topMatch.getDataClassification().getRegulatoryMetricsClassification());

        // Verify The 3 Core Question Answers
        assertNotNull(topMatch.getThreeQuestionAnswers());
        assertNotNull(topMatch.getThreeQuestionAnswers().getWhyRecommended());
        assertTrue(topMatch.getThreeQuestionAnswers().getWhyRecommended().contains("Rank #1"));
        assertFalse(topMatch.getThreeQuestionAnswers().getVerifiedFactsSupporting().isEmpty());
        assertFalse(topMatch.getThreeQuestionAnswers().getEstimatesAndAssumptions().isEmpty());

        // Verify Insurer Metrics Dated Provenance
        assertNotNull(topMatch.getInsurerMetrics());
        assertNotNull(topMatch.getInsurerMetrics().getCsrReportingPeriod());
        assertNotNull(topMatch.getInsurerMetrics().getCsrCalculationMethod());
        assertNotNull(topMatch.getInsurerMetrics().getNetworkAsOfDate());
    }

    @Test
    void testGenerateRecommendationsForFamilyWithChild() {
        List<RecommendationRequestDto.MemberInputDto> members = new ArrayList<>();
        members.add(RecommendationRequestDto.MemberInputDto.builder()
                .relation("SPOUSE")
                .displayName("Spouse")
                .age(32)
                .build());
        members.add(RecommendationRequestDto.MemberInputDto.builder()
                .relation("CHILD")
                .displayName("Child 1")
                .age(4)
                .build());

        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(34)
                .insuranceType("FAMILY_WITH_CHILDREN")
                .members(members)
                .cityOrPincode("Mumbai")
                .annualBudget(35000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(2500000L)
                .helpMeDecideSumInsured(false)
                .primaryPriority("BROADER_COVERAGE")
                .maternityBenefitNeeded(true)
                .preExistingConditions(new ArrayList<>())
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-family", request);

        assertNotNull(response);
        assertEquals(3, response.getProfileSummary().getTotalInsuredMembers());
        assertEquals("Zone 1", response.getProfileSummary().getPricingZone());
        assertTrue(response.getEligiblePoliciesCount() >= 3);
        assertFalse(response.getRankedPolicies().isEmpty());
    }

    @Test
    void testGenerateRecommendationsForSeniorParents() {
        List<RecommendationRequestDto.MemberInputDto> members = new ArrayList<>();
        members.add(RecommendationRequestDto.MemberInputDto.builder()
                .relation("SPOUSE")
                .displayName("Mother")
                .age(61)
                .preExistingConditions(List.of("HYPERTENSION"))
                .build());

        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(65)
                .insuranceType("PARENTS")
                .members(members)
                .cityOrPincode("Delhi")
                .annualBudget(75000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .helpMeDecideSumInsured(false)
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .preExistingConditions(List.of("HYPERTENSION", "DIABETES"))
                .copayTolerance("LOW")
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-senior", request);

        assertNotNull(response);
        assertTrue(response.getEligiblePoliciesCount() > 0);
        // Should evaluate senior and parent-eligible policies
        assertFalse(response.getRankedPolicies().isEmpty());
    }

    @Test
    void testGetGuidanceForMetroCity() {
        GuidanceDto.GuidanceRequest gr = GuidanceDto.GuidanceRequest.builder()
                .primaryAge(29)
                .insuranceType("INDIVIDUAL")
                .totalMembers(1)
                .cityOrPincode("Mumbai")
                .currentBudget(25000.0)
                .build();

        GuidanceDto.GuidanceResponse guidance = recommendationService.getGuidance(gr);

        assertNotNull(guidance);
        assertEquals("Zone 1", guidance.getPricingZone());
        assertEquals("Zone 1", guidance.getZone());
        assertTrue(guidance.getRecommendedSumInsured() >= 1000000L);
        assertTrue(guidance.getEstimatedAnnualPremiumMin() > 0);
        assertTrue(guidance.getEstimatedAnnualPremiumMax() > guidance.getEstimatedAnnualPremiumMin());
        assertNotNull(guidance.getBudgetAdequacyNote());
        assertNotNull(guidance.getMedicalInflationInsight());
        assertFalse(guidance.getCityHealthcareInsights().isEmpty());
        assertFalse(guidance.getRecommendationsByLifeStage().isEmpty());
    }

    @Test
    void testCatalogRetrieval() {
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.size() >= 5);
        assertTrue(catalog.stream().anyMatch(p -> "HDFC_OPTIMA_SECURE".equals(p.getPolicyCode())));
    }

    @Test
    void testGetUserRecommendationsFromCacheAndDb() {
        // First generate to populate in-memory history
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(28)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Bengaluru")
                .annualBudget(20000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .primaryPriority("BALANCED")
                .build();

        recommendationService.generateRecommendations("user-cache-test", request);

        List<RecommendationResult> history =
                recommendationService.getUserRecommendations("user-cache-test");

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("user-cache-test", history.get(0).getUserId());
    }

    @Test
    void testCanonicalCatalogReconciliation() {
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        assertNotNull(catalog);
        assertEquals(9, catalog.size());

        for (StructuredPolicy policy : catalog) {
            // Verify canonical UIN and revision
            assertNotNull(policy.getIrdaUin(), "Policy UIN cannot be null: " + policy.getPolicyCode());
            assertFalse(policy.getIrdaUin().isBlank(), "Policy UIN cannot be blank: " + policy.getPolicyCode());
            assertTrue(List.of("VERIFIED_CONTRACTUAL_SPECIFICATION", "VERIFIED_OFFICIAL_CATALOG").contains(policy.getVerificationStatus()),
                    "Verification status must be verified: " + policy.getPolicyCode());
            assertNotNull(policy.getRegulatoryFilingSource());
            assertNotNull(policy.getOfficialDocumentUrl());

            // Verify Insurer Metrics provenance
            assertNotNull(policy.getInsurerMetrics(), "InsurerMetrics cannot be null: " + policy.getPolicyCode());
            assertTrue(policy.getInsurerMetrics().getCsrMetricName().startsWith("Claim Settlement Ratio"),
                    "CSR metric name must start with Claim Settlement Ratio: " + policy.getPolicyCode());
            assertTrue(policy.getInsurerMetrics().getCsrReportingPeriod().contains("2022-23"),
                    "Reporting period must be FY 2022-23: " + policy.getPolicyCode());
            assertNotNull(policy.getInsurerMetrics().getCsrCalculationMethod());
            assertNotNull(policy.getInsurerMetrics().getNetworkAsOfDate());

            // Verify supporting document evidence items
            assertNotNull(policy.getDocumentEvidence());
            assertFalse(policy.getDocumentEvidence().isEmpty(), "Evidence items must be present: " + policy.getPolicyCode());
            for (StructuredPolicy.PolicyEvidenceItem ev : policy.getDocumentEvidence()) {
                assertEquals("VERIFIED_CONTRACT_CLAUSE", ev.getEvidenceConfidence());
                assertNotNull(ev.getVerbatimText());
                assertNotNull(ev.getVerifiedFact());
                assertNotNull(ev.getSectionReference());
            }
        }
    }

    @Test
    void testEmptyResponseForElderlyOutsideZone1WithHelpMeDecide() {
        // Mock empty catalog
        when(structuredPolicyRepository.findByActiveTrue()).thenReturn(List.of());

        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(95) // User over 50; exceeds catalog maximum entry ages to produce empty eligible set
                .insuranceType("INDIVIDUAL")
                .members(List.of())
                .cityOrPincode("Jaipur") // Zone 2
                .annualBudget(40000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(0L)
                .helpMeDecideSumInsured(true)
                .primaryPriority("BALANCED")
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-elderly-empty", request);

        assertNotNull(response);
        assertEquals(0, response.getEligiblePoliciesCount());
        assertTrue(response.getRankedPolicies().isEmpty());

        // For user > 50 in Zone 2 with Help Me Decide, automatic guidance recommends ₹25 Lakh (not fallback ₹5 Lakh)
        assertEquals(2500000L, response.getProfileSummary().getSelectedSumInsured());
        assertEquals("₹25 Lakh", response.getProfileSummary().getFormattedSumInsured());

        // Advisory must state that no eligible policies were found, NOT "No policy meets the requested coverage"
        assertEquals("No eligible policies were found.", response.getCoverageAdvisory());
        assertNotNull(response.getGuidance());
        assertEquals("No eligible policies were found.", response.getGuidance().getCoverageAdvisory());
    }

    @Test
    void testEmptyResponseDoesNotAttributeAgeExclusionToInsufficientCoverage() {
        // Catalog with policy where max entry age is 65
        StructuredPolicy policy = StructuredPolicy.builder()
                .id("p-under-65")
                .policyCode("UNDER_65_PLAN")
                .policyName("Under 65 Plan")
                .provider("Care Health")
                .irdaUin("CARE12345V01")
                .verificationStatus("VERIFIED_CONTRACTUAL_SPECIFICATION")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .allowedFamilyTypes(List.of("INDIVIDUAL"))
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder().sumInsured(1000000L).formattedSumInsured("₹10 Lakh").build()
                ))
                .build();

        when(structuredPolicyRepository.findByActiveTrue()).thenReturn(List.of(policy));

        // User age 75 (exceeds max entry age 65)
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(75)
                .insuranceType("INDIVIDUAL")
                .members(List.of())
                .cityOrPincode("Bengaluru")
                .annualBudget(50000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .helpMeDecideSumInsured(false)
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-age-ineligible", request);

        assertNotNull(response);
        assertEquals(0, response.getEligiblePoliciesCount());
        // Must NOT falsely state "No policy meets the requested coverage of ₹10 Lakh."
        assertEquals("No eligible policies were found.", response.getCoverageAdvisory());
        assertEquals("No eligible policies were found.", response.getGuidance().getCoverageAdvisory());
    }

    @Test
    void testGuidanceCoverageAdvisorySetBeforePersistenceAndAvailableInHistory() {
        // Request higher sum insured than maximum catalog tier to induce shortfall advisory
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(30)
                .insuranceType("INDIVIDUAL")
                .members(List.of())
                .cityOrPincode("Bengaluru")
                .annualBudget(50000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(15000000L) // ₹1.5 Cr
                .helpMeDecideSumInsured(false)
                .primaryPriority("BALANCED")
                .build();

        RecommendationResponseDto response =
                recommendationService.generateRecommendations("user-shortfall-persistence", request);

        assertNotNull(response);
        assertNotNull(response.getCoverageAdvisory());
        assertTrue(response.getCoverageAdvisory().contains("₹1.5 Cr"));
        assertNotNull(response.getGuidance().getCoverageAdvisory());

        // In-memory history record (saved before return) must already contain the coverage advisory
        List<RecommendationResult> history =
                recommendationService.getUserRecommendations("user-shortfall-persistence");
        assertFalse(history.isEmpty());
        assertNotNull(history.get(0).getGuidance().getCoverageAdvisory());
        assertEquals(response.getCoverageAdvisory(), history.get(0).getGuidance().getCoverageAdvisory());
    }
}
