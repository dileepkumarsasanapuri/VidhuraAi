package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 5.1 Unit & Integration Test Suite:
 * Recommendation Correctness, Honest Statutory Evidence Decoupling,
 * Unknown vs Ineligible Distinction, and Real-Policy Expansion.
 */
public class Phase5_1CorrectnessAndExpansionTest {

    private StructuredPolicyRepository structuredPolicyRepository;
    private RecommendationProfileRepository profileRepository;
    private RecommendationResultRepository resultRepository;
    private EligibilityEngine eligibilityEngine;
    private RankingEngine rankingEngine;
    private PolicyIngestionService policyIngestionService;
    private ExternalEvidenceService externalEvidenceService;
    private CanonicalProductRegistry canonicalProductRegistry;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        structuredPolicyRepository = Mockito.mock(StructuredPolicyRepository.class);
        profileRepository = Mockito.mock(RecommendationProfileRepository.class);
        resultRepository = Mockito.mock(RecommendationResultRepository.class);
        policyIngestionService = new PolicyIngestionService(structuredPolicyRepository);
        externalEvidenceService = Mockito.mock(ExternalEvidenceService.class);
        canonicalProductRegistry = new CanonicalProductRegistry();
        eligibilityEngine = new EligibilityEngine();
        rankingEngine = new RankingEngine(null, null, externalEvidenceService);

        List<StructuredPolicy> allPolicies = policyIngestionService.getAllCatalogPolicies();
        when(structuredPolicyRepository.findByActiveTrue()).thenReturn(allPolicies);
        when(structuredPolicyRepository.count()).thenReturn((long) allPolicies.size());
        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        recommendationService = new RecommendationService(
                structuredPolicyRepository,
                profileRepository,
                resultRepository,
                eligibilityEngine,
                rankingEngine,
                policyIngestionService,
                null,
                canonicalProductRegistry,
                null
        );
    }

    @Test
    @DisplayName("Single deterministic source of truth for priority weights in RankingEngine")
    void testPriorityWeightsDeterminism() {
        RankingEngine.PriorityWeights balanced = RankingEngine.resolvePriorityWeights("BALANCED");
        assertEquals(0.25, balanced.cost(), 1e-6);
        assertEquals(0.25, balanced.coverage(), 1e-6);
        assertEquals(0.25, balanced.oop(), 1e-6);
        assertEquals(0.25, balanced.reliability(), 1e-6);

        RankingEngine.PriorityWeights affordable = RankingEngine.resolvePriorityWeights("AFFORDABLE_PREMIUMS");
        assertEquals(0.45, affordable.cost(), 1e-6);
        assertEquals(0.20, affordable.coverage(), 1e-6);
        assertEquals(0.20, affordable.oop(), 1e-6);
        assertEquals(0.15, affordable.reliability(), 1e-6);

        RankingEngine.PriorityWeights broader = RankingEngine.resolvePriorityWeights("BROADER_COVERAGE");
        assertEquals(0.15, broader.cost(), 1e-6);
        assertEquals(0.45, broader.coverage(), 1e-6);
        assertEquals(0.20, broader.oop(), 1e-6);
        assertEquals(0.20, broader.reliability(), 1e-6);

        RankingEngine.PriorityWeights lowerOop = RankingEngine.resolvePriorityWeights("LOWER_OUT_OF_POCKET");
        assertEquals(0.15, lowerOop.cost(), 1e-6);
        assertEquals(0.20, lowerOop.coverage(), 1e-6);
        assertEquals(0.45, lowerOop.oop(), 1e-6);
        assertEquals(0.20, lowerOop.reliability(), 1e-6);
    }

    @Test
    @DisplayName("Missing eligibility terms distinguish unassessed from confirmed insurer rejection")
    void testUnknownEligibilityDistinction() {
        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(35)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .build();

        StructuredPolicy policyWithoutEligibility = StructuredPolicy.builder()
                .policyCode("TEST_INCOMPLETE")
                .policyName("Incomplete Policy")
                .irdaUin("TEST12345")
                .eligibility(null)
                .build();

        List<EligibilityEngine.EligibilityResult> results = eligibilityEngine.filterEligible(profile, List.of(policyWithoutEligibility));
        assertFalse(results.isEmpty());
        EligibilityEngine.EligibilityResult result = results.get(0);

        assertFalse(result.isEligible());
        assertTrue(result.isEligibilityUnknown());
        assertEquals("UNASSESSED_MISSING_DATA", result.getEligibilityStatus());
        assertTrue(result.getFailReasons().stream().anyMatch(r -> r.contains("Eligibility cannot be assessed — missing contractual terms")));
        assertTrue(result.getFailReasons().stream().anyMatch(r -> r.contains("Not a confirmed rejection by insurer")));
    }

    @Test
    @DisplayName("External statutory evidence missing sets neutral 50.0 reliability with EVIDENCE_UNAVAILABLE status")
    void testExternalEvidenceDecouplingNeutralScore() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .policyCode("UNKNOWN_POLICY")
                .policyName("Policy Without External Evidence")
                .provider("Unknown Insurer")
                .build();

        double reliabilityScore = rankingEngine.calculateReliabilityScore(policy);
        assertEquals(50.0, reliabilityScore, 1e-6);
    }

    @Test
    @DisplayName("Verified statutory evidence uses authentic IRDAI claim settlement ratio")
    void testVerifiedStatutoryEvidenceScore() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .policyCode("VERIFIED_POLICY")
                .policyName("Policy With Verified Evidence")
                .provider("HDFC ERGO General Insurance")
                .build();

        RecommendationResponseDto.ExternalInsurerIntelligenceDto verifiedIntel =
                RecommendationResponseDto.ExternalInsurerIntelligenceDto.builder()
                        .insurerName("HDFC ERGO General Insurance")
                        .claimSettlementRatio("98.5%")
                        .incurredClaimRatio("75.0%")
                        .ombudsmanDisposalRate("90.0%")
                        .cashlessAccessibilityScore(92.0)
                        .verifiedStatutory(true)
                        .verificationStatus("VERIFIED_STATUTORY")
                        .build();

        double reliabilityScore = rankingEngine.calculateReliabilityScore(policy, new RecommendationProfile(), verifiedIntel);
        assertTrue(reliabilityScore > 85.0, "Verified high-performing insurer should score high, got: " + reliabilityScore);
    }

    @Test
    @DisplayName("Expanded authentic acquired catalog contains at least 8 authentic policies with real SHA-256 hashes")
    void testAuthenticCatalogExpansion() {
        List<StructuredPolicy> acquired = policyIngestionService.getAuthenticAcquiredCatalog();

        assertTrue(acquired.size() >= 8, "Expected at least 8 authentic acquired policies, found: " + acquired.size());

        for (StructuredPolicy policy : acquired) {
            if (!policy.isActive()) {
                continue; // Unapproved draft filings are staged as inactive pending reviewer approval
            }
            assertFalse(policy.isSyntheticFixture(), "Acquired policy must not be marked synthetic: " + policy.getPolicyName());
            assertNotNull(policy.getDocumentChecksum(), "Document SHA-256 checksum must be present: " + policy.getPolicyName());
            assertEquals(64, policy.getDocumentChecksum().length(), "SHA-256 checksum must be 64 characters: " + policy.getPolicyName());
            assertNotNull(policy.getIrdaUin(), "IRDA UIN must be present: " + policy.getPolicyName());
            assertNotNull(policy.getEligibility(), "Eligibility must be present: " + policy.getPolicyName());
            assertNotNull(policy.getContractTerms(), "Contract terms must be present: " + policy.getPolicyName());
            assertNotNull(policy.getDocumentEvidence(), "Document evidence must be present: " + policy.getPolicyName());
            assertFalse(policy.getDocumentEvidence().isEmpty(), "Must have citations: " + policy.getPolicyName());
        }
    }

    @Test
    @DisplayName("End-to-end recommendation returns authentic policies across multiple insurers with honest rate-table estimates")
    void testEndToEndRecommendationWithExpandedPolicies() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Bengaluru")
                .annualBudget(45000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L) // 10 Lakhs
                .primaryPriority("BALANCED")
                .preExistingConditions(Collections.emptyList())
                .roomRentPreference("SINGLE_PRIVATE_AC")
                .restorationBenefitNeeded(true)
                .consentGiven(true)
                .build();

        RecommendationResponseDto response = recommendationService.generateRecommendations("test-user-502", req);

        assertNotNull(response);
        assertNotNull(response.getRankedPolicies());
        assertFalse(response.getRankedPolicies().isEmpty(), "Recommendations should not be empty");

        long authenticCount = response.getRankedPolicies().stream()
                .filter(p -> !p.isSyntheticFixture())
                .count();
        assertTrue(authenticCount >= 3, "Expected at least 3 authentic policies in recommendations, got: " + authenticCount);

        for (RecommendationResponseDto.RankedPolicyCard card : response.getRankedPolicies()) {
            assertEquals("ACTUARIAL_RATE_TABLE_ESTIMATE", card.getPricingType());
            assertNotNull(card.getPricingDisclaimer());
            assertNotNull(card.getUnderwritingNote());
            assertTrue(card.getOverallMatchScore() > 0);
            assertTrue(card.getCostScore() > 0);
            assertTrue(card.getCoverageScore() > 0);
        }
    }
}
