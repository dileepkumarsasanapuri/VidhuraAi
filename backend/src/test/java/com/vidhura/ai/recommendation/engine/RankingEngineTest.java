package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankingEngineTest {

    private RankingEngine rankingEngine;

    @BeforeEach
    void setUp() {
        rankingEngine = new RankingEngine();
    }

    private StructuredPolicy createSamplePolicy(String id, String name, double basePremium, boolean roomRentCapped, double copay) {
        List<StructuredPolicy.SumInsuredTier> tiers = new ArrayList<>();
        tiers.add(StructuredPolicy.SumInsuredTier.builder()
                .sumInsured(1000000L)
                .formattedSumInsured("₹10 Lakh")
                .premiumAge18To35(basePremium)
                .premiumAge36To45(basePremium * 1.2)
                .spouseLoadingFactor(0.70)
                .childLoadingFactor(0.30)
                .build());

        List<StructuredPolicy.PolicyEvidenceItem> evidence = new ArrayList<>();
        evidence.add(StructuredPolicy.PolicyEvidenceItem.builder()
                .clauseName("In-patient Hospitalization")
                .sectionReference("Section 4.1")
                .documentName(name + "_Wordings.pdf")
                .verbatimText("Covered up to sum insured with no room rent capping.")
                .verifiedFact("Single private AC room or higher without proportionate deduction.")
                .tag("ROOM_RENT")
                .build());

        return StructuredPolicy.builder()
                .id(id)
                .policyCode("CODE_" + id)
                .policyName(name)
                .provider("Test Insurer")
                .irdaUin("UIN12345")
                .planName("Plus")
                .category("Comprehensive")
                .policyWordingDocument(name + "_Wordings.pdf")
                .active(true)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit(roomRentCapped ? "1% of Sum Insured" : "No Limit (Single Private AC Room)")
                        .roomRentCapped(roomRentCapped)
                        .proportionateDeductionApplies(roomRentCapped)
                        .roomRentRiskExplanation("Test explanation")
                        .copayPercentage(copay)
                        .copayNote(copay == 0 ? "No copay" : "10% copay")
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodSpecificMonths(24)
                        .waitingPeriodPedMonths(36)
                        .restorationBenefit("100% recharge")
                        .noClaimBonus("50% up to 100%")
                        .maternityCovered(false)
                        .opdCovered(false)
                        .dayCareProceduresCount(540)
                        .cashlessEverywhere(true)
                        .build())
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(98.5)
                        .incurredClaimsRatio(82.0)
                        .cashlessHospitalsCount(12000)
                        .avgGrievanceResolutionDays(1.9)
                        .claimPaymentSpeed("45 mins")
                        .build())
                .documentEvidence(evidence)
                .sumInsuredTiers(tiers)
                .exclusions(List.of("Cosmetic surgery", "Self-inflicted injury"))
                .build();
    }

    @Test
    void testRankPoliciesGeneratesOrderedRecommendationsWithAnswers() {
        StructuredPolicy p1 = createSamplePolicy("p1", "Premium Shield", 16000.0, false, 0.0);
        StructuredPolicy p2 = createSamplePolicy("p2", "Budget Basic", 11000.0, true, 10.0);

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(29)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Mumbai")
                .annualBudget(20000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .copayTolerance("FLEXIBLE")
                .preExistingConditions(new ArrayList<>())
                .build();

        List<EligibilityEngine.EligibilityResult> eligibleResults = List.of(
                EligibilityEngine.EligibilityResult.builder().policy(p1).eligible(true).build(),
                EligibilityEngine.EligibilityResult.builder().policy(p2).eligible(true).build()
        );

        List<RecommendationResult.RankedPolicyItem> ranked =
                rankingEngine.rankPolicies(profile, eligibleResults);

        assertNotNull(ranked);
        assertEquals(2, ranked.size());

        // Verify Rank #1 has highest score
        RecommendationResult.RankedPolicyItem top = ranked.get(0);
        assertEquals(1, top.getRank());
        assertTrue(top.getOverallMatchScore() > 0 && top.getOverallMatchScore() <= 100);

        // Core Q1: 4 dimensional scores exist
        assertTrue(top.getCostScore() > 0);
        assertTrue(top.getCoverageScore() > 0);
        assertTrue(top.getOutOfPocketProtectionScore() > 0);
        assertTrue(top.getInsurerReliabilityScore() > 0);
        assertNotNull(top.getFormattedAnnualPremium());

        // Core Q2: Limitations and Out-of-pocket risk analysis
        assertNotNull(top.getLimitations());
        assertNotNull(top.getOutOfPocketRisk());
        assertNotNull(top.getOutOfPocketRisk().getRiskLevel());
        assertTrue(top.getOutOfPocketRisk().getEstimatedCoveredAmount() > 0);
        assertFalse(top.getKeyExclusions().isEmpty());

        // Core Q3: Verifiable evidence
        assertNotNull(top.getSupportingEvidence());
        assertFalse(top.getSupportingEvidence().isEmpty());
        assertEquals("ROOM_RENT", top.getSupportingEvidence().get(0).getTag());
    }

    @Test
    void testLowerOutOfPocketPriorityFavorsZeroRoomRentAndZeroCopay() {
        StructuredPolicy safePolicy = createSamplePolicy("safe", "Safe Secure", 18000.0, false, 0.0);
        StructuredPolicy trapPolicy = createSamplePolicy("trap", "Restrictive Plus", 12000.0, true, 10.0);

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Bengaluru")
                .annualBudget(22000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .copayTolerance("FLEXIBLE")
                .preExistingConditions(new ArrayList<>())
                .build();

        List<EligibilityEngine.EligibilityResult> eligibleResults = List.of(
                EligibilityEngine.EligibilityResult.builder().policy(trapPolicy).eligible(true).build(),
                EligibilityEngine.EligibilityResult.builder().policy(safePolicy).eligible(true).build()
        );

        List<RecommendationResult.RankedPolicyItem> ranked =
                rankingEngine.rankPolicies(profile, eligibleResults);

        // Under LOWER_OUT_OF_POCKET priority, the policy with 0 copay and 0 room-rent capping should score higher OOP protection
        RecommendationResult.RankedPolicyItem rank1 = ranked.get(0);
        assertEquals("Safe Secure", rank1.getPolicyName(), "Safe policy should rank #1 when user prioritizes lower out-of-pocket risk");
        assertTrue(rank1.getOutOfPocketProtectionScore() > ranked.get(1).getOutOfPocketProtectionScore());
    }

    @Test
    void testZone1KeepsPricedTierAtOrAboveMinimumAndExcludesInadequatePolicies() {
        // Zone 1 user with automatic target of ₹1,000,000
        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(28)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .detectedZone("Zone 1")
                .helpMeDecideSumInsured(true)
                .annualBudget(50000.0)
                .budgetType("FLEXIBLE")
                .primaryPriority("BALANCED")
                .build();

        // Policy A has tiers 500k and 2500k
        StructuredPolicy policyWithAdequateTier = createSamplePolicy("pA", "Adequate Plan", 15000.0, false, 0.0);
        policyWithAdequateTier.setSumInsuredTiers(List.of(
                StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(500000L)
                        .formattedSumInsured("₹5 Lakh")
                        .premiumAge18To35(8000.0)
                        .build(),
                StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(2500000L)
                        .formattedSumInsured("₹25 Lakh")
                        .premiumAge18To35(18000.0)
                        .build()
        ));

        // Policy B has only tiers 300k and 500k (none satisfies Zone 1 target of 1,000,000)
        StructuredPolicy policyWithoutAdequateTier = createSamplePolicy("pB", "Under-insured Plan", 10000.0, false, 0.0);
        policyWithoutAdequateTier.setSumInsuredTiers(List.of(
                StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(300000L)
                        .formattedSumInsured("₹3 Lakh")
                        .premiumAge18To35(5000.0)
                        .build(),
                StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(500000L)
                        .formattedSumInsured("₹5 Lakh")
                        .premiumAge18To35(7500.0)
                        .build()
        ));

        List<EligibilityEngine.EligibilityResult> eligibleResults = List.of(
                EligibilityEngine.EligibilityResult.builder().policy(policyWithAdequateTier).eligible(true).build(),
                EligibilityEngine.EligibilityResult.builder().policy(policyWithoutAdequateTier).eligible(true).build()
        );

        List<RecommendationResult.RankedPolicyItem> ranked = rankingEngine.rankPolicies(profile, eligibleResults);

        assertEquals(2, ranked.size(), "Both policies should be evaluated; policy with adequate tier ranks top, while policy B falls back to max tier with shortfall");
        RecommendationResult.RankedPolicyItem topPlan = ranked.get(0);
        assertEquals("Adequate Plan", topPlan.getPolicyName());
        assertEquals(2500000L, topPlan.getSelectedSumInsured(),
                "Must choose tier at or above the Zone 1 minimum (₹25 Lakh), not the sub-minimum ₹5 Lakh tier");
        assertFalse(topPlan.isCoverageShortfall());

        // Policy B is priced at largest available tier (500,000) and flagged with shortfall
        RecommendationResult.RankedPolicyItem shortfallPlan = ranked.get(1);
        assertEquals("Under-insured Plan", shortfallPlan.getPolicyName());
        assertEquals(500000L, shortfallPlan.getSelectedSumInsured());
        assertTrue(shortfallPlan.isCoverageShortfall());
        assertEquals(500000L, shortfallPlan.getCoverageShortfallAmount());
        assertTrue(shortfallPlan.getMatchBadges().contains("Capped at ₹5 Lakh"));
        assertNotNull(shortfallPlan.getLimitations().getCoverageShortfallWarning());
    }

    @Test
    void testLargeUserSelectedSumInsuredPricesLargestAvailableTierAndFlagsShortfall() {
        // User explicitly requests ₹1.5 Cr without help me decide
        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(35)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Bengaluru")
                .detectedZone("Zone 1")
                .helpMeDecideSumInsured(false)
                .desiredSumInsured(15000000L) // ₹1.5 Cr
                .annualBudget(100000.0)
                .budgetType("FLEXIBLE")
                .primaryPriority("BALANCED")
                .build();

        StructuredPolicy policy = createSamplePolicy("p1", "Elevate Health", 25000.0, false, 0.0);
        policy.setSumInsuredTiers(List.of(
                StructuredPolicy.SumInsuredTier.builder().sumInsured(1000000L).formattedSumInsured("₹10 Lakh").premiumAge18To35(12000.0).build(),
                StructuredPolicy.SumInsuredTier.builder().sumInsured(2500000L).formattedSumInsured("₹25 Lakh").premiumAge18To35(22000.0).build()
        ));

        List<EligibilityEngine.EligibilityResult> eligibleResults = List.of(
                EligibilityEngine.EligibilityResult.builder().policy(policy).eligible(true).build()
        );

        List<RecommendationResult.RankedPolicyItem> ranked = rankingEngine.rankPolicies(profile, eligibleResults);

        // Policy must NOT be silently dropped
        assertEquals(1, ranked.size(), "Policy must not be silently excluded when requested sum insured exceeds maximum catalog tier");
        RecommendationResult.RankedPolicyItem item = ranked.get(0);
        assertEquals(2500000L, item.getSelectedSumInsured(), "Should fallback to largest available tier (₹25 Lakh)");
        assertTrue(item.isCoverageShortfall());
        assertEquals(15000000L, item.getRequestedSumInsured());
        assertEquals(12500000L, item.getCoverageShortfallAmount());
        assertTrue(item.getMatchReasons().stream().anyMatch(r -> r.contains("Coverage Shortfall Advisory: Policy provides maximum coverage of ₹25 Lakh, leaving a ₹1.25 Cr shortfall against your ₹1.5 Cr target")));
        assertEquals("Policy maximum sum insured is ₹25 Lakh (shortfall of ₹1.25 Cr from your ₹1.5 Cr target). Hospitalization expenses beyond ₹25 Lakh will be out-of-pocket.",
                item.getLimitations().getCoverageShortfallWarning());
    }
}
