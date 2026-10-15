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
}
