package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityEngineTest {

    private EligibilityEngine eligibilityEngine;

    @BeforeEach
    void setUp() {
        eligibilityEngine = new EligibilityEngine();
    }

    private StructuredPolicy createSamplePolicy(int minAge, int maxAge, List<String> familyTypes) {
        List<StructuredPolicy.SumInsuredTier> tiers = new ArrayList<>();
        tiers.add(StructuredPolicy.SumInsuredTier.builder()
                .sumInsured(1000000L)
                .formattedSumInsured("₹10 Lakh")
                .premiumAge18To35(12000.0)
                .premiumAge36To45(15000.0)
                .spouseLoadingFactor(0.70)
                .childLoadingFactor(0.30)
                .build());

        return StructuredPolicy.builder()
                .id("test-policy-1")
                .policyCode("TEST_01")
                .policyName("Test Comprehensive Health")
                .provider("Test Insurance Co")
                .active(true)
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(minAge)
                        .maxAdultAge(maxAge)
                        .allowedFamilyTypes(familyTypes)
                        .minSumInsured(500000L)
                        .maxSumInsured(5000000L)
                        .build())
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .copayPercentage(0.0)
                        .roomRentCapped(false)
                        .build())
                .sumInsuredTiers(tiers)
                .build();
    }

    @Test
    void testEligibleIndividualWithinAge() {
        StructuredPolicy policy = createSamplePolicy(18, 65, List.of("INDIVIDUAL", "SELF_AND_SPOUSE"));

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(28)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Mumbai")
                .annualBudget(20000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .build();

        List<EligibilityEngine.EligibilityResult> results =
                eligibilityEngine.filterEligible(profile, List.of(policy));

        assertEquals(1, results.size());
        assertTrue(results.get(0).isEligible(), "Individual within age should be eligible");
        assertTrue(results.get(0).getFailReasons().isEmpty());
    }

    @Test
    void testDisqualifiedWhenAgeExceedsMaxAdultAge() {
        StructuredPolicy policy = createSamplePolicy(18, 60, List.of("INDIVIDUAL"));

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(68)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Delhi")
                .annualBudget(50000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .build();

        List<EligibilityEngine.EligibilityResult> results =
                eligibilityEngine.filterEligible(profile, List.of(policy));

        assertEquals(1, results.size());
        assertFalse(results.get(0).isEligible(), "Age above maxAdultAge must be marked ineligible");
        assertFalse(results.get(0).getFailReasons().isEmpty());

        List<EligibilityEngine.EligibilityResult> eligibleOnly =
                eligibilityEngine.getEligibleOnly(profile, List.of(policy));
        assertTrue(eligibleOnly.isEmpty(), "getEligibleOnly should filter out ineligible policies");
    }

    @Test
    void testDisqualifiedWhenAgeBelowMinAdultAge() {
        StructuredPolicy policy = createSamplePolicy(18, 65, List.of("INDIVIDUAL"));

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(16) // below 18
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Bengaluru")
                .annualBudget(15000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .build();

        List<EligibilityEngine.EligibilityResult> results =
                eligibilityEngine.filterEligible(profile, List.of(policy));

        assertEquals(1, results.size());
        assertFalse(results.get(0).isEligible(), "Age below minAdultAge must be marked ineligible");
    }

    @Test
    void testDisqualifiedWhenFamilyTypeNotAllowed() {
        StructuredPolicy policy = createSamplePolicy(18, 65, List.of("INDIVIDUAL")); // only individual

        RecommendationProfile profile = RecommendationProfile.builder()
                .primaryAge(35)
                .insuranceType("FAMILY_WITH_CHILDREN") // not supported by this policy
                .members(List.of(
                        RecommendationProfile.InsuredMember.builder().relation("SPOUSE").age(32).build()
                ))
                .cityOrPincode("Chennai")
                .annualBudget(30000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .build();

        List<EligibilityEngine.EligibilityResult> results =
                eligibilityEngine.filterEligible(profile, List.of(policy));

        assertEquals(1, results.size());
        assertFalse(results.get(0).isEligible(), "Disallowed family type should be ineligible");
    }
}
