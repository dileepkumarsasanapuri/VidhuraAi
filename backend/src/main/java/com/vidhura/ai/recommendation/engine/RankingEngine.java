package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ranking Engine — personalized scoring of eligible policies
 * against user requirements and priorities.
 *
 * Core design principle: the ranking is calculated from verified policy facts,
 * not independently invented by an LLM.
 *
 * Scoring dimensions (out of 100 each):
 * 1. Cost Score — how affordable vs budget
 * 2. Coverage Score — breadth and quality of cover
 * 3. Out-of-Pocket Protection Score — copay, room rent, sublimit exposure
 * 4. Insurer Reliability Score — CSR, cashless network, grievance speed
 *
 * The overall score is a weighted combination based on the user's stated priority.
 */
@Slf4j
@Component
public class RankingEngine {

    /**
     * Scores and ranks a list of eligible policies for the given user profile.
     */
    public List<RecommendationResult.RankedPolicyItem> rankPolicies(
            RecommendationProfile profile,
            List<EligibilityEngine.EligibilityResult> eligibleResults) {

        log.info("[RankingEngine] Scoring {} eligible policies for user (priority={})",
                eligibleResults.size(), profile.getPrimaryPriority());

        List<RecommendationResult.RankedPolicyItem> scored = new ArrayList<>();

        for (EligibilityEngine.EligibilityResult er : eligibleResults) {
            StructuredPolicy policy = er.getPolicy();

            double estimatedPremium = estimatePremium(policy, profile);
            long selectedSI = selectSumInsured(policy, profile);

            double costScore = calculateCostScore(estimatedPremium, profile.getAnnualBudget());
            double coverageScore = calculateCoverageScore(policy);
            double oopScore = calculateOutOfPocketScore(policy);
            double reliabilityScore = calculateReliabilityScore(policy);

            double overallScore = calculateWeightedScore(
                    costScore, coverageScore, oopScore, reliabilityScore,
                    profile.getPrimaryPriority());

            boolean withinBudget = estimatedPremium <= profile.getAnnualBudget();
            double budgetDiff = profile.getAnnualBudget() - estimatedPremium;

            // Build limitations assessment (Q2)
            RecommendationResult.LimitationAssessment limitations = buildLimitations(policy, profile);

            // Build out-of-pocket risk analysis (Q2)
            RecommendationResult.OutOfPocketRiskAnalysis oopRisk = buildOutOfPocketRisk(policy, profile, selectedSI);

            // Key exclusions
            List<String> keyExclusions = policy.getExclusions() != null
                    ? policy.getExclusions().stream().limit(8).collect(Collectors.toList())
                    : List.of();

            // Match badges
            List<String> badges = new ArrayList<>();
            if (overallScore >= 85) badges.add("Top Match");
            if (costScore >= 90) badges.add("Best Value");
            if (oopScore >= 90) badges.add("Lowest Out-of-Pocket");
            if (coverageScore >= 90) badges.add("Broadest Coverage");
            if (withinBudget && budgetDiff > 0) badges.add("Within Budget");

            // Match reasons
            List<String> matchReasons = buildMatchReasons(policy, profile, costScore, coverageScore, oopScore);

            // Evidence citations (Q3)
            List<StructuredPolicy.PolicyEvidenceItem> evidence = policy.getDocumentEvidence() != null
                    ? policy.getDocumentEvidence()
                    : List.of();

            RecommendationResult.RankedPolicyItem item = RecommendationResult.RankedPolicyItem.builder()
                    .policyId(policy.getId())
                    .policyCode(policy.getPolicyCode())
                    .policyName(policy.getPolicyName())
                    .provider(policy.getProvider())
                    .irdaUin(policy.getIrdaUin())
                    .planName(policy.getPlanName())
                    .category(policy.getCategory())
                    .policyWordingDocument(policy.getPolicyWordingDocument())
                    .overallMatchScore(round2(overallScore))
                    .costScore(round2(costScore))
                    .coverageScore(round2(coverageScore))
                    .outOfPocketProtectionScore(round2(oopScore))
                    .insurerReliabilityScore(round2(reliabilityScore))
                    .calculatedAnnualPremium(round2(estimatedPremium))
                    .formattedAnnualPremium(formatCurrency(estimatedPremium))
                    .selectedSumInsured(selectedSI)
                    .formattedSumInsured(formatSumInsured(selectedSI))
                    .withinBudget(withinBudget)
                    .budgetDifference(round2(budgetDiff))
                    .matchBadges(badges)
                    .matchReasons(matchReasons)
                    .limitations(limitations)
                    .outOfPocketRisk(oopRisk)
                    .keyExclusions(keyExclusions)
                    .supportingEvidence(evidence)
                    .insurerMetrics(policy.getInsurerMetrics())
                    .build();

            scored.add(item);
        }

        // Sort descending by overall match score
        scored.sort(Comparator.comparingDouble(RecommendationResult.RankedPolicyItem::getOverallMatchScore).reversed());

        // Assign ranks
        for (int i = 0; i < scored.size(); i++) {
            scored.get(i).setRank(i + 1);
        }

        log.info("[RankingEngine] Ranking complete. Top match: {} (score={})",
                scored.isEmpty() ? "N/A" : scored.get(0).getPolicyName(),
                scored.isEmpty() ? 0 : scored.get(0).getOverallMatchScore());

        return scored;
    }

    // =========================================================================
    // COST SCORE
    // =========================================================================
    private double calculateCostScore(double premium, double budget) {
        if (budget <= 0) return 50.0;
        if (premium <= 0) return 50.0;
        double ratio = premium / budget;
        if (ratio <= 0.6) return 100.0;
        if (ratio <= 0.8) return 90.0;
        if (ratio <= 1.0) return 75.0;
        if (ratio <= 1.2) return 55.0;
        if (ratio <= 1.5) return 35.0;
        return 15.0;
    }

    // =========================================================================
    // COVERAGE SCORE
    // =========================================================================
    private double calculateCoverageScore(StructuredPolicy policy) {
        double score = 50.0; // baseline
        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) return score;

        // Room rent — no cap is best
        if (!ct.isRoomRentCapped()) score += 10;
        else score += 3;

        // No co-pay
        if (ct.getCopayPercentage() <= 0) score += 8;
        else if (ct.getCopayPercentage() <= 10) score += 4;

        // Restoration benefit
        if (ct.getRestorationBenefit() != null && !ct.getRestorationBenefit().isBlank()) score += 8;

        // Day care procedures
        if (ct.getDayCareProceduresCount() >= 500) score += 5;
        else if (ct.getDayCareProceduresCount() >= 200) score += 3;

        // Modern treatments
        if (ct.isModernTreatmentsCovered()) score += 5;

        // Organ donor
        if (ct.isOrganDonorCovered()) score += 3;

        // Ambulance
        if (ct.isAmbulanceCovered()) score += 2;

        // Pre/Post hospitalization
        if (ct.getPreHospitalizationDays() >= 60) score += 3;
        if (ct.getPostHospitalizationDays() >= 180) score += 3;

        // No-claim bonus
        if (ct.getNoClaimBonus() != null && !ct.getNoClaimBonus().isBlank()) score += 3;

        return Math.min(100.0, score);
    }

    // =========================================================================
    // OUT-OF-POCKET PROTECTION SCORE
    // =========================================================================
    private double calculateOutOfPocketScore(StructuredPolicy policy) {
        double score = 100.0; // start from best and deduct
        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) return 50.0;

        // Room rent cap penalty
        if (ct.isRoomRentCapped()) score -= 20;
        if (ct.isProportionateDeductionApplies()) score -= 15;

        // Co-pay penalty
        if (ct.getCopayPercentage() > 0) {
            score -= ct.getCopayPercentage() * 2; // e.g. 20% copay = -40
        }

        // Deductible penalty
        if (ct.getDeductibleAmount() > 0) {
            score -= Math.min(20, ct.getDeductibleAmount() / 5000.0);
        }

        // Disease sublimits penalty
        if (ct.getDiseaseSublimits() != null && !ct.getDiseaseSublimits().isEmpty()) {
            score -= Math.min(15, ct.getDiseaseSublimits().size() * 3);
        }

        // Waiting period penalties
        if (ct.getWaitingPeriodPedMonths() > 36) score -= 5;
        if (ct.getWaitingPeriodSpecificMonths() > 24) score -= 3;

        return Math.max(0, score);
    }

    // =========================================================================
    // INSURER RELIABILITY SCORE
    // =========================================================================
    private double calculateReliabilityScore(StructuredPolicy policy) {
        StructuredPolicy.InsurerMetrics im = policy.getInsurerMetrics();
        if (im == null) return 50.0;

        double score = 0;

        // Claim settlement ratio (max 40 points)
        if (im.getClaimSettlementRatio() >= 95) score += 40;
        else if (im.getClaimSettlementRatio() >= 90) score += 30;
        else if (im.getClaimSettlementRatio() >= 80) score += 20;
        else score += 10;

        // Cashless hospitals (max 25 points)
        if (im.getCashlessHospitalsCount() >= 10000) score += 25;
        else if (im.getCashlessHospitalsCount() >= 5000) score += 18;
        else if (im.getCashlessHospitalsCount() >= 2000) score += 10;
        else score += 5;

        // Grievance resolution (max 20 points)
        if (im.getAvgGrievanceResolutionDays() <= 3) score += 20;
        else if (im.getAvgGrievanceResolutionDays() <= 7) score += 14;
        else if (im.getAvgGrievanceResolutionDays() <= 15) score += 8;
        else score += 3;

        // ICR (incurred claims ratio) — lower is better for insurer stability (max 15 points)
        if (im.getIncurredClaimsRatio() > 0 && im.getIncurredClaimsRatio() <= 90) score += 15;
        else if (im.getIncurredClaimsRatio() <= 100) score += 10;
        else score += 5;

        return Math.min(100, score);
    }

    // =========================================================================
    // WEIGHTED OVERALL SCORE
    // =========================================================================
    private double calculateWeightedScore(
            double cost, double coverage, double oop, double reliability,
            String priority) {

        double wCost, wCoverage, wOop, wReliability;

        switch (priority != null ? priority : "BALANCED") {
            case "AFFORDABLE_PREMIUMS":
                wCost = 0.45; wCoverage = 0.20; wOop = 0.20; wReliability = 0.15;
                break;
            case "BROADER_COVERAGE":
                wCost = 0.15; wCoverage = 0.45; wOop = 0.20; wReliability = 0.20;
                break;
            case "LOWER_OUT_OF_POCKET":
                wCost = 0.15; wCoverage = 0.20; wOop = 0.45; wReliability = 0.20;
                break;
            case "BALANCED":
            default:
                wCost = 0.25; wCoverage = 0.25; wOop = 0.25; wReliability = 0.25;
                break;
        }

        return wCost * cost + wCoverage * coverage + wOop * oop + wReliability * reliability;
    }

    // =========================================================================
    // PREMIUM ESTIMATION
    // =========================================================================
    private double estimatePremium(StructuredPolicy policy, RecommendationProfile profile) {
        List<StructuredPolicy.SumInsuredTier> tiers = policy.getSumInsuredTiers();
        if (tiers == null || tiers.isEmpty()) return 0;

        long targetSI = selectSumInsured(policy, profile);

        // Find the closest tier
        StructuredPolicy.SumInsuredTier selectedTier = tiers.get(0);
        long closestDiff = Math.abs(selectedTier.getSumInsured() - targetSI);

        for (StructuredPolicy.SumInsuredTier tier : tiers) {
            long diff = Math.abs(tier.getSumInsured() - targetSI);
            if (diff < closestDiff) {
                closestDiff = diff;
                selectedTier = tier;
            }
        }

        // Get base premium for age band
        int age = profile.getPrimaryAge();
        double basePremium;
        if (age <= 35) basePremium = selectedTier.getPremiumAge18To35();
        else if (age <= 45) basePremium = selectedTier.getPremiumAge36To45();
        else if (age <= 55) basePremium = selectedTier.getPremiumAge46To55();
        else if (age <= 65) basePremium = selectedTier.getPremiumAge56To65();
        else basePremium = selectedTier.getPremiumAge66Plus();

        if (basePremium <= 0) basePremium = selectedTier.getPremiumAge18To35();

        // Apply floater loadings for family members
        double totalPremium = basePremium;
        if (profile.getMembers() != null) {
            for (RecommendationProfile.InsuredMember m : profile.getMembers()) {
                String rel = m.getRelation().toUpperCase();
                if (rel.contains("SPOUSE")) {
                    totalPremium += basePremium * selectedTier.getSpouseLoadingFactor();
                } else if (rel.contains("CHILD")) {
                    totalPremium += basePremium * selectedTier.getChildLoadingFactor();
                } else if (rel.contains("PARENT") || rel.contains("FATHER") || rel.contains("MOTHER")) {
                    // Parent policies use the parent's own age bracket
                    int parentAge = m.getAge();
                    double parentBase;
                    if (parentAge <= 55) parentBase = selectedTier.getPremiumAge46To55();
                    else if (parentAge <= 65) parentBase = selectedTier.getPremiumAge56To65();
                    else parentBase = selectedTier.getPremiumAge66Plus();
                    totalPremium += (parentBase > 0 ? parentBase : basePremium * 1.5);
                }
            }
        }

        return totalPremium;
    }

    // =========================================================================
    // SUM INSURED SELECTION
    // =========================================================================
    private long selectSumInsured(StructuredPolicy policy, RecommendationProfile profile) {
        if (!profile.isHelpMeDecideSumInsured() && profile.getDesiredSumInsured() > 0) {
            return profile.getDesiredSumInsured();
        }
        // Auto-recommend based on age and city tier
        int age = profile.getPrimaryAge();
        int memberCount = 1 + (profile.getMembers() != null ? profile.getMembers().size() : 0);
        if (age >= 50 || memberCount >= 4) return 2500000;
        if (age >= 35 || memberCount >= 2) return 1000000;
        return 500000;
    }

    // =========================================================================
    // LIMITATION ASSESSMENT (Q2)
    // =========================================================================
    private RecommendationResult.LimitationAssessment buildLimitations(
            StructuredPolicy policy, RecommendationProfile profile) {

        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) {
            return RecommendationResult.LimitationAssessment.builder().build();
        }

        String pedStatus = "Not applicable";
        if (profile.getPreExistingConditions() != null && !profile.getPreExistingConditions().isEmpty()) {
            pedStatus = String.format("PED waiting period: %d months. Conditions: %s",
                    ct.getWaitingPeriodPedMonths(),
                    String.join(", ", profile.getPreExistingConditions()));
        }

        return RecommendationResult.LimitationAssessment.builder()
                .roomRentCondition(ct.getRoomRentLimit())
                .roomRentCapped(ct.isRoomRentCapped())
                .proportionateDeductionRisk(ct.isProportionateDeductionApplies())
                .roomRentWarning(ct.getRoomRentRiskExplanation())
                .copayPercentage(ct.getCopayPercentage())
                .copayNote(ct.getCopayNote())
                .sublimits(ct.getDiseaseSublimits() != null ? ct.getDiseaseSublimits() : List.of())
                .initialWaitingDays(ct.getWaitingPeriodInitialDays())
                .specificIllnessWaitingMonths(ct.getWaitingPeriodSpecificMonths())
                .pedWaitingMonths(ct.getWaitingPeriodPedMonths())
                .pedStatusForUser(pedStatus)
                .restorationSummary(ct.getRestorationBenefit())
                .noClaimBonusSummary(ct.getNoClaimBonus())
                .build();
    }

    // =========================================================================
    // OUT-OF-POCKET RISK ANALYSIS (Q2)
    // =========================================================================
    private RecommendationResult.OutOfPocketRiskAnalysis buildOutOfPocketRisk(
            StructuredPolicy policy, RecommendationProfile profile, long sumInsured) {

        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) {
            return RecommendationResult.OutOfPocketRiskAnalysis.builder()
                    .riskLevel("UNKNOWN")
                    .summary("Insufficient policy data to assess out-of-pocket risk.")
                    .build();
        }

        double sampleBill = 300000.0; // ₹3 lakh hospitalization scenario
        double deductions = 0;
        List<String> factors = new ArrayList<>();

        // Copay
        if (ct.getCopayPercentage() > 0) {
            deductions += sampleBill * (ct.getCopayPercentage() / 100.0);
            factors.add(String.format("%.0f%% co-payment applies", ct.getCopayPercentage()));
        }

        // Deductible
        if (ct.getDeductibleAmount() > 0) {
            deductions += ct.getDeductibleAmount();
            factors.add(String.format("₹%,.0f deductible", ct.getDeductibleAmount()));
        }

        // Room rent proportionate deduction (estimate 15% of bill if applicable)
        if (ct.isRoomRentCapped() && ct.isProportionateDeductionApplies()) {
            double roomDeduction = sampleBill * 0.15;
            deductions += roomDeduction;
            factors.add("Proportionate room rent deduction risk (~15% of bill)");
        }

        double covered = sampleBill - deductions;
        double exposurePct = (sampleBill > 0) ? (deductions / sampleBill) * 100 : 0;

        String riskLevel;
        if (exposurePct <= 5) riskLevel = "LOW";
        else if (exposurePct <= 20) riskLevel = "MODERATE";
        else riskLevel = "HIGH";

        return RecommendationResult.OutOfPocketRiskAnalysis.builder()
                .riskLevel(riskLevel)
                .summary(String.format("For a ₹%,.0f hospitalization, estimated out-of-pocket: ₹%,.0f (%.1f%%)",
                        sampleBill, deductions, exposurePct))
                .estimatedExposurePercentage(round2(exposurePct))
                .sampleScenarioBill(sampleBill)
                .estimatedCoveredAmount(round2(covered))
                .estimatedOutofPocketCost(round2(deductions))
                .exposureFactors(factors)
                .build();
    }

    // =========================================================================
    // MATCH REASONS
    // =========================================================================
    private List<String> buildMatchReasons(
            StructuredPolicy policy, RecommendationProfile profile,
            double costScore, double coverageScore, double oopScore) {

        List<String> reasons = new ArrayList<>();
        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) return reasons;

        if (!ct.isRoomRentCapped()) reasons.add("No room rent capping — eliminates proportionate deduction risk");
        if (ct.getCopayPercentage() <= 0) reasons.add("Zero co-payment on all claims");
        if (ct.getDeductibleAmount() <= 0) reasons.add("No deductible");
        if (ct.getRestorationBenefit() != null && !ct.getRestorationBenefit().isBlank())
            reasons.add("Restoration benefit: " + ct.getRestorationBenefit());
        if (ct.getDayCareProceduresCount() >= 500)
            reasons.add(ct.getDayCareProceduresCount() + " day care procedures covered");
        if (ct.isModernTreatmentsCovered()) reasons.add("Modern treatments covered (robotic surgery, stem cell, etc.)");
        if (costScore >= 80) reasons.add("Well within your stated budget");
        if (oopScore >= 85) reasons.add("Very low out-of-pocket exposure during claims");

        StructuredPolicy.InsurerMetrics im = policy.getInsurerMetrics();
        if (im != null && im.getClaimSettlementRatio() >= 95)
            reasons.add(String.format("%.1f%% claim settlement ratio", im.getClaimSettlementRatio()));

        return reasons;
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String formatCurrency(double amount) {
        if (amount >= 100000) return String.format("₹%,.0f", amount);
        return String.format("₹%,.0f", amount);
    }

    private String formatSumInsured(long amount) {
        if (amount >= 10000000) return String.format("₹%d Cr", amount / 10000000);
        if (amount >= 100000) return String.format("₹%d Lakh", amount / 100000);
        return String.format("₹%,d", amount);
    }
}
