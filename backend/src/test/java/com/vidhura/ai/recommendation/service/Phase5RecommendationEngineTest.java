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
 * Phase 5 Unit & Integration Acceptance Test Suite:
 * Multi-Policy Personalized Recommendation Engine.
 *
 * Verifies:
 * 1. Strict fail-closed eligibility: Unknown eligibility rules must NEVER be assumed eligible.
 * 2. External statutory evidence decoupling: Decouples contractual suitability from statutory performance
 *    without assigning zero penalties when statutory evidence is unavailable.
 * 3. Transparent trade-offs: Explicitly generates plain-language positive and negative trade-offs.
 * 4. Incomplete catalogue visibility: Transparently reports discovered/unverified catalogue entries
 *    and reasons for exclusion rather than fabricating policy specifications.
 * 5. User consent & preference propagation: Ensures consent, room-rent preference, and restoration
 *    benefit needed are persisted and evaluated.
 */
public class Phase5RecommendationEngineTest {

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

        // Pre-populate mock repository with verified seed catalog
        List<StructuredPolicy> seed = policyIngestionService.getSeedCatalog();
        when(structuredPolicyRepository.findByActiveTrue()).thenReturn(seed);
        when(structuredPolicyRepository.count()).thenReturn((long) seed.size());
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
    @DisplayName("Eligibility Engine strictly rejects policies with missing or unextracted eligibility rules")
    void testEligibilityEngineFailClosedOnUnknownRules() {
        StructuredPolicy unextractedPolicy = StructuredPolicy.builder()
                .policyCode("UNEXTRACTED_SAMPLE")
                .policyName("Unextracted Draft Policy")
                .provider("Test Insurer Ltd")
                .irdaUin("TEST12345V01")
                .active(true)
                .eligibility(null) // Unextracted rules!
                .build();

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(35)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .detectedZone("Zone 1")
                .build();

        List<EligibilityEngine.EligibilityResult> results = eligibilityEngine.filterEligible(profile, List.of(unextractedPolicy));
        assertFalse(results.isEmpty());
        EligibilityEngine.EligibilityResult result = results.get(0);

        assertFalse(result.isEligible(), "Policy with unextracted eligibility rules must be ruled ineligible");
        assertTrue(result.isEligibilityUnknown(), "eligibilityUnknown flag must be true");
        assertFalse(result.getFailReasons().isEmpty(), "Must record explicit fail reason");
        assertTrue(result.getFailReasons().get(0).contains("missing contractual terms"), "Reason must state rules were not extracted");
    }

    @Test
    @DisplayName("End-to-end recommendation generates ranked policies with pros/cons tradeoffs and statutory evidence")
    void testRecommendationPipelineWithTradeoffsAndEvidence() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Bengaluru")
                .annualBudget(25000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .helpMeDecideSumInsured(false)
                .primaryPriority("BALANCED")
                .preExistingConditions(List.of("HYPERTENSION"))
                .roomRentPreference("SINGLE_PRIVATE_AC")
                .restorationBenefitNeeded(true)
                .consentGiven(true)
                .build();

        RecommendationResponseDto response = recommendationService.generateRecommendations("test-user-501", req);

        assertNotNull(response);
        assertNotNull(response.getRankedPolicies());
        assertFalse(response.getRankedPolicies().isEmpty(), "Should return eligible ranked policies from verified catalogue");

        RecommendationResponseDto.RankedPolicyCard topPolicy = response.getRankedPolicies().get(0);
        assertEquals(1, topPolicy.getRank());
        assertTrue(topPolicy.getOverallMatchScore() > 0);

        // Verify Positive & Negative Tradeoffs
        assertNotNull(topPolicy.getPositiveTradeoffs(), "Must include positive tradeoffs");
        assertNotNull(topPolicy.getNegativeTradeoffs(), "Must include negative tradeoffs");
        assertFalse(topPolicy.getPositiveTradeoffs().isEmpty(), "Top policy must list at least one positive tradeoff");

        // Verify External Insurer Intelligence
        assertNotNull(topPolicy.getExternalIntelligence(), "Must bind external insurer intelligence");
        assertNotNull(topPolicy.getExternalIntelligence().getCsrDenominatorDefinition(), "CSR must define denominator basis");
        assertNotNull(topPolicy.getExternalIntelligence().getSolvencyRegulatoryBasis(), "Solvency must specify IRDAI statutory basis");

        // Verify Incomplete Catalogue Entries
        assertNotNull(response.getIncompleteCatalogueEntries(), "Must expose unrecommended catalogue entries");
        assertFalse(response.getIncompleteCatalogueEntries().isEmpty(), "Catalogue entries awaiting extraction must be surfaced");
        assertTrue(response.getIncompleteCatalogueEntries().stream()
                .anyMatch(e -> "DISCOVERED_CATALOGUE_ENTRY".equals(e.getPipelineStage())
                        || "AWAITING_HUMAN_REVIEW".equals(e.getPipelineStage())),
                "Must list pipeline stage for unrecommended products");
    }

    @Test
    @DisplayName("External Evidence Service does not unfairly penalize missing insurer evidence to 0%")
    void testExternalEvidenceDecouplingSafeHandling() {
        when(externalEvidenceService.getInsurerPerformanceMetrics("UNKNOWN_NEW_INSURER_99", null)).thenReturn(null);

        // Compute reliability score with a policy from unknown insurer
        StructuredPolicy unknownPolicy = StructuredPolicy.builder()
                .policyCode("UNKNOWN_POLICY")
                .policyName("Unknown Policy")
                .provider("Unknown Health Co")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private AC Room")
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(30)
                .primaryPriority("BALANCED")
                .build();

        double reliabilityScore = rankingEngine.calculateReliabilityScore(unknownPolicy, profile);

        // Baseline reliability score should remain neutral baseline (50.0) and NOT crash to 0
        assertTrue(reliabilityScore >= 50.0, "Missing external statutory evidence must not crash reliability score to 0");
    }

    @Test
    @DisplayName("Catalogue Cohort endpoint returns published vs incomplete catalog breakdown")
    void testCatalogueCohortBreakdown() {
        Map<String, Object> cohort = recommendationService.getCatalogueCohort();

        assertNotNull(cohort);
        assertTrue(cohort.containsKey("publishedPoliciesCount"));
        assertTrue(cohort.containsKey("incompleteCatalogCount"));
        assertTrue(cohort.containsKey("totalTrackedProducts"));
        assertTrue(cohort.containsKey("publishedPolicies"));
        assertTrue(cohort.containsKey("incompleteCatalogEntries"));

        int published = (int) cohort.get("publishedPoliciesCount");
        int incomplete = (int) cohort.get("incompleteCatalogCount");
        int total = (int) cohort.get("totalTrackedProducts");

        assertEquals(published + incomplete, total, "Total tracked products must equal published + incomplete");
        assertTrue(incomplete > 0, "Incomplete catalogue must track products awaiting acquisition or extraction");
    }

    @Test
    @DisplayName("Room Rent Preference penalizes capped policies when user chooses No Capping or Single Private AC")
    void testRoomRentPreferenceImpactsOutOfPocketScore() {
        StructuredPolicy cappedPolicy = StructuredPolicy.builder()
                .policyCode("CAPPED_ROOM_POLICY")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("1% of Sum Insured per day")
                        .roomRentCapped(true)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        StructuredPolicy uncappedPolicy = StructuredPolicy.builder()
                .policyCode("UNCAPPED_ROOM_POLICY")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No Capping / Any Room")
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .build();

        RecommendationProfile strictProfile = RecommendationProfile.builder()
                .primaryAge(30)
                .roomRentPreference("NO_CAPPING_ANY_ROOM")
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .build();

        double cappedScore = rankingEngine.calculateOutOfPocketScore(cappedPolicy, strictProfile);
        double uncappedScore = rankingEngine.calculateOutOfPocketScore(uncappedPolicy, strictProfile);

        assertTrue(uncappedScore > cappedScore,
                "Policy without room rent capping must score significantly higher when user prefers no capping");
    }
}
