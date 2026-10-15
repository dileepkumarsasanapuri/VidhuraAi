package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.GuidanceDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the recommendation pipeline:
 * 1. Validates and persists the user profile
 * 2. Resolves city/zone for pricing
 * 3. Runs EligibilityEngine to filter catalog
 * 4. Runs RankingEngine to score and rank
 * 5. Assembles the full recommendation response
 *
 * Core principle: separates policy facts, recommendation calculations,
 * and any future AI explanations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final StructuredPolicyRepository structuredPolicyRepository;
    private final RecommendationProfileRepository profileRepository;
    private final RecommendationResultRepository resultRepository;
    private final EligibilityEngine eligibilityEngine;
    private final RankingEngine rankingEngine;

    // Simple city-to-zone mapping (MVP; would use a proper database in production)
    private static final Map<String, String> CITY_ZONE_MAP = new HashMap<>() {{
        put("mumbai", "Zone 1"); put("delhi", "Zone 1"); put("bengaluru", "Zone 1");
        put("bangalore", "Zone 1"); put("hyderabad", "Zone 1"); put("chennai", "Zone 1");
        put("kolkata", "Zone 1"); put("pune", "Zone 1"); put("gurgaon", "Zone 1");
        put("noida", "Zone 1"); put("ahmedabad", "Zone 1"); put("goa", "Zone 1");
        put("chandigarh", "Zone 2"); put("jaipur", "Zone 2"); put("lucknow", "Zone 2");
        put("indore", "Zone 2"); put("bhopal", "Zone 2"); put("nagpur", "Zone 2");
        put("kochi", "Zone 2"); put("coimbatore", "Zone 2"); put("visakhapatnam", "Zone 2");
        put("vizag", "Zone 2"); put("thiruvananthapuram", "Zone 2");
    }};

    /**
     * Full recommendation pipeline.
     */
    public RecommendationResponseDto generateRecommendations(
            String userId, RecommendationRequestDto request) {

        log.info("[RecommendationService] Generating recommendations for user={} (age={}, type={}, budget={})",
                userId, request.getPrimaryAge(), request.getInsuranceType(), request.getAnnualBudget());

        // 1. Build and persist profile
        RecommendationProfile profile = buildProfile(userId, request);
        profile = profileRepository.save(profile);

        // 2. Load full catalog
        List<StructuredPolicy> catalog = structuredPolicyRepository.findByActiveTrue();
        log.info("[RecommendationService] Loaded {} active policies from catalog", catalog.size());

        // 3. Eligibility filter
        List<EligibilityEngine.EligibilityResult> eligibleResults =
                eligibilityEngine.getEligibleOnly(profile, catalog);

        if (eligibleResults.isEmpty()) {
            log.warn("[RecommendationService] No eligible policies found for profile {}", profile.getId());
            return buildEmptyResponse(profile, catalog.size());
        }

        // 4. Rank eligible policies
        List<RecommendationResult.RankedPolicyItem> rankedItems =
                rankingEngine.rankPolicies(profile, eligibleResults);

        // 5. Build guidance
        RecommendationResult.ProfileGuidance guidance = buildGuidance(profile);

        // 6. Persist result
        RecommendationResult result = RecommendationResult.builder()
                .userId(userId)
                .profileId(profile.getId())
                .userRequirements(profile)
                .guidance(guidance)
                .rankedPolicies(rankedItems)
                .build();
        result = resultRepository.save(result);

        // 7. Assemble response DTO
        return assembleResponse(result, profile, catalog.size(), eligibleResults.size());
    }

    /**
     * Dynamic "Help Me Decide" sum-insured guidance.
     */
    public GuidanceDto.GuidanceResponse getGuidance(GuidanceDto.GuidanceRequest request) {
        int age = request.getPrimaryAge();
        int members = request.getTotalMembers() != null ? request.getTotalMembers() : 1;
        String zone = resolveZone(request.getCityOrPincode());

        long recommendedSI;
        String rationale;
        List<String> lifeStageAdvice = new ArrayList<>();

        if (age <= 30 && members <= 2) {
            recommendedSI = 500000;
            rationale = "For young individuals/couples, ₹5 Lakh is a reasonable starting point. Consider ₹10 Lakh if you live in a metro city.";
            lifeStageAdvice.add("You're at a great age for low premiums — lock in now.");
            lifeStageAdvice.add("Consider a higher sum insured if planning a family soon.");
        } else if (age <= 40) {
            recommendedSI = members >= 3 ? 1500000 : 1000000;
            rationale = "For ages 31-40 with family responsibilities, ₹10-15 Lakh provides adequate cover for most hospitalizations.";
            lifeStageAdvice.add("Average metro hospitalization costs ₹3-5 Lakh. Include buffer for ICU stays.");
            lifeStageAdvice.add("Check maternity cover if planning children.");
        } else if (age <= 50) {
            recommendedSI = members >= 3 ? 2500000 : 1500000;
            rationale = "Ages 41-50: higher disease risk starts. ₹15-25 Lakh recommended to cover cardiac, joint replacement, and cancer treatments.";
            lifeStageAdvice.add("Cancer treatment in India costs ₹5-20 Lakh. Ensure adequate cover.");
            lifeStageAdvice.add("Review pre-existing condition waiting periods carefully.");
        } else {
            recommendedSI = 2500000;
            rationale = "Above 50: comprehensive cover of ₹25 Lakh+ is strongly recommended. Medical costs escalate significantly.";
            lifeStageAdvice.add("Joint replacement: ₹3-6 Lakh. Cardiac bypass: ₹3-8 Lakh.");
            lifeStageAdvice.add("Check co-payment clauses for senior citizens carefully.");
        }

        // Zone adjustments
        if ("Zone 1".equals(zone)) {
            recommendedSI = Math.max(recommendedSI, 1000000);
            lifeStageAdvice.add("Metro city detected — hospital costs are 30-50% higher. Higher cover recommended.");
        }

        double premMin = recommendedSI * 0.012;
        double premMax = recommendedSI * 0.025;

        return GuidanceDto.GuidanceResponse.builder()
                .recommendedSumInsured(recommendedSI)
                .formattedRecommendedSumInsured(formatSumInsured(recommendedSI))
                .rationale(rationale)
                .pricingZone(zone)
                .estimatedAnnualPremiumMin(premMin)
                .estimatedAnnualPremiumMax(premMax)
                .formattedEstimatedPremiumRange(String.format("₹%,.0f — ₹%,.0f per year", premMin, premMax))
                .cityHealthcareInsights(List.of())
                .recommendationsByLifeStage(lifeStageAdvice)
                .build();
    }

    /**
     * Retrieve past recommendations for a user.
     */
    public List<RecommendationResult> getUserRecommendations(String userId) {
        return resultRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Retrieve a specific recommendation by ID.
     */
    public RecommendationResult getRecommendationById(String id, String userId) {
        return resultRepository.findByIdAndUserId(id, userId).orElse(null);
    }

    /**
     * List all available policies in catalog (for admin/debug).
     */
    public List<StructuredPolicy> getCatalog() {
        return structuredPolicyRepository.findByActiveTrue();
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private RecommendationProfile buildProfile(String userId, RecommendationRequestDto req) {
        String zone = resolveZone(req.getCityOrPincode());
        long desiredSI = Boolean.TRUE.equals(req.getHelpMeDecideSumInsured()) || req.getDesiredSumInsured() == null
                ? 0
                : req.getDesiredSumInsured();

        List<RecommendationProfile.InsuredMember> members = req.getMembers().stream()
                .map(m -> RecommendationProfile.InsuredMember.builder()
                        .relation(m.getRelation())
                        .displayName(m.getDisplayName())
                        .age(m.getAge())
                        .preExistingConditions(m.getPreExistingConditions() != null
                                ? m.getPreExistingConditions() : List.of())
                        .build())
                .collect(Collectors.toList());

        return RecommendationProfile.builder()
                .userId(userId)
                .primaryAge(req.getPrimaryAge())
                .insuranceType(req.getInsuranceType())
                .members(members)
                .cityOrPincode(req.getCityOrPincode())
                .detectedZone(zone)
                .annualBudget(req.getAnnualBudget())
                .budgetType(req.getBudgetType())
                .desiredSumInsured(desiredSI)
                .helpMeDecideSumInsured(Boolean.TRUE.equals(req.getHelpMeDecideSumInsured()))
                .primaryPriority(req.getPrimaryPriority())
                .preferredHospitals(req.getPreferredHospitals() != null ? req.getPreferredHospitals() : List.of())
                .preExistingConditions(req.getPreExistingConditions() != null ? req.getPreExistingConditions() : List.of())
                .hasExistingInsurance(Boolean.TRUE.equals(req.getHasExistingInsurance()))
                .existingSumInsured(req.getExistingSumInsured())
                .portingPolicy(Boolean.TRUE.equals(req.getPortingPolicy()))
                .policyRenewalDate(req.getPolicyRenewalDate())
                .maternityBenefitNeeded(Boolean.TRUE.equals(req.getMaternityBenefitNeeded()))
                .opdBenefitNeeded(Boolean.TRUE.equals(req.getOpdBenefitNeeded()))
                .copayTolerance(req.getCopayTolerance() != null ? req.getCopayTolerance() : "NONE")
                .build();
    }

    private String resolveZone(String cityOrPincode) {
        if (cityOrPincode == null || cityOrPincode.isBlank()) return "Zone 2";
        String lower = cityOrPincode.trim().toLowerCase();
        // Check city name map
        String zone = CITY_ZONE_MAP.get(lower);
        if (zone != null) return zone;
        // PIN code heuristic: 1xxxxx, 11xxxx = Zone 1; else Zone 2/3
        if (lower.matches("\\d{6}")) {
            String prefix = lower.substring(0, 2);
            if (List.of("11", "12", "40", "41", "50", "56", "60", "70").contains(prefix)) return "Zone 1";
            return "Zone 2";
        }
        return "Zone 3"; // default for smaller towns
    }

    private RecommendationResult.ProfileGuidance buildGuidance(RecommendationProfile profile) {
        GuidanceDto.GuidanceRequest gr = GuidanceDto.GuidanceRequest.builder()
                .primaryAge(profile.getPrimaryAge())
                .insuranceType(profile.getInsuranceType())
                .totalMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                .cityOrPincode(profile.getCityOrPincode())
                .currentBudget(profile.getAnnualBudget())
                .build();
        GuidanceDto.GuidanceResponse g = getGuidance(gr);

        return RecommendationResult.ProfileGuidance.builder()
                .recommendedSumInsured(g.getRecommendedSumInsured())
                .formattedRecommendedSumInsured(g.getFormattedRecommendedSumInsured())
                .sumInsuredRationale(g.getRationale())
                .zoneClassification(g.getPricingZone())
                .medicalInflationInsight("Medical inflation in India averages 14% annually — review sum insured adequacy every 2-3 years.")
                .budgetAdequacyNote(profile.getAnnualBudget() >= g.getEstimatedAnnualPremiumMin()
                        ? "Your budget is sufficient for the recommended cover level."
                        : "Your budget may limit available options at the recommended sum insured level.")
                .build();
    }

    private RecommendationResponseDto assembleResponse(
            RecommendationResult result,
            RecommendationProfile profile,
            int totalCatalogSize,
            int eligibleCount) {

        String budgetLabel = "STRICT".equals(profile.getBudgetType()) ? "Strict" : "Preferred";
        String priorityLabel = switch (profile.getPrimaryPriority()) {
            case "AFFORDABLE_PREMIUMS" -> "Affordable Premiums";
            case "BROADER_COVERAGE" -> "Broader Coverage";
            case "LOWER_OUT_OF_POCKET" -> "Lower Out-of-Pocket Costs";
            default -> "Balanced Overall Suitability";
        };

        long selectedSI = profile.getDesiredSumInsured() > 0
                ? profile.getDesiredSumInsured()
                : result.getGuidance().getRecommendedSumInsured();

        RecommendationResponseDto.ProfileSummary summary = RecommendationResponseDto.ProfileSummary.builder()
                .primaryAge(profile.getPrimaryAge())
                .insuranceType(profile.getInsuranceType())
                .totalInsuredMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                .cityOrPincode(profile.getCityOrPincode())
                .pricingZone(profile.getDetectedZone())
                .annualBudget(profile.getAnnualBudget())
                .formattedBudget(String.format("₹%,.0f (%s)", profile.getAnnualBudget(), budgetLabel))
                .budgetType(profile.getBudgetType())
                .selectedSumInsured(selectedSI)
                .formattedSumInsured(formatSumInsured(selectedSI))
                .primaryPriorityLabel(priorityLabel)
                .declaredConditions(profile.getPreExistingConditions())
                .build();

        List<RecommendationResponseDto.RankedPolicyCard> cards = result.getRankedPolicies().stream()
                .map(rp -> RecommendationResponseDto.RankedPolicyCard.builder()
                        .rank(rp.getRank())
                        .policyId(rp.getPolicyId())
                        .policyCode(rp.getPolicyCode())
                        .policyName(rp.getPolicyName())
                        .provider(rp.getProvider())
                        .irdaUin(rp.getIrdaUin())
                        .planName(rp.getPlanName())
                        .category(rp.getCategory())
                        .policyWordingDocument(rp.getPolicyWordingDocument())
                        .overallMatchScore(rp.getOverallMatchScore())
                        .costScore(rp.getCostScore())
                        .coverageScore(rp.getCoverageScore())
                        .outOfPocketProtectionScore(rp.getOutOfPocketProtectionScore())
                        .insurerReliabilityScore(rp.getInsurerReliabilityScore())
                        .calculatedAnnualPremium(rp.getCalculatedAnnualPremium())
                        .formattedAnnualPremium(rp.getFormattedAnnualPremium())
                        .sumInsured(rp.getSelectedSumInsured())
                        .formattedSumInsured(rp.getFormattedSumInsured())
                        .withinBudget(rp.isWithinBudget())
                        .budgetDifference(rp.getBudgetDifference())
                        .budgetComparisonText(rp.isWithinBudget()
                                ? String.format("₹%,.0f under budget", rp.getBudgetDifference())
                                : String.format("₹%,.0f over budget", Math.abs(rp.getBudgetDifference())))
                        .matchBadges(rp.getMatchBadges())
                        .matchReasons(rp.getMatchReasons())
                        .limitations(rp.getLimitations())
                        .outOfPocketRisk(rp.getOutOfPocketRisk())
                        .keyExclusions(rp.getKeyExclusions())
                        .supportingEvidence(rp.getSupportingEvidence())
                        .insurerMetrics(rp.getInsurerMetrics())
                        .build())
                .collect(Collectors.toList());

        return RecommendationResponseDto.builder()
                .recommendationId(result.getId())
                .profileId(profile.getId())
                .generatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : Instant.now())
                .profileSummary(summary)
                .guidance(result.getGuidance())
                .rankedPolicies(cards)
                .totalPoliciesEvaluated(totalCatalogSize)
                .eligiblePoliciesCount(eligibleCount)
                .build();
    }

    private RecommendationResponseDto buildEmptyResponse(
            RecommendationProfile profile, int totalCatalogSize) {
        return RecommendationResponseDto.builder()
                .profileId(profile.getId())
                .generatedAt(Instant.now())
                .profileSummary(RecommendationResponseDto.ProfileSummary.builder()
                        .primaryAge(profile.getPrimaryAge())
                        .insuranceType(profile.getInsuranceType())
                        .cityOrPincode(profile.getCityOrPincode())
                        .annualBudget(profile.getAnnualBudget())
                        .build())
                .rankedPolicies(List.of())
                .totalPoliciesEvaluated(totalCatalogSize)
                .eligiblePoliciesCount(0)
                .build();
    }

    private String formatSumInsured(long amount) {
        if (amount >= 10000000) return String.format("₹%d Cr", amount / 10000000);
        if (amount >= 100000) return String.format("₹%d Lakh", amount / 100000);
        return String.format("₹%,d", amount);
    }
}
