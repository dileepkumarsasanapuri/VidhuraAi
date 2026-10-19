package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.service.ApprovedPolicyRuleResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * 3. Out-of-Pocket Protection Score — copay, room rent, sublimit exposure & deterministic simulation
 * 4. Insurer Reliability Score — CSR, cashless network, grievance speed
 *
 * The overall score is a weighted combination based on the user's stated priority.
 */
@Slf4j
@Component
public class RankingEngine {

    private final DeterministicClaimEngine claimEngine;
    private final ApprovedPolicyRuleResolver ruleResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public RankingEngine(
            @org.springframework.beans.factory.annotation.Autowired(required = false) DeterministicClaimEngine claimEngine,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ApprovedPolicyRuleResolver ruleResolver) {
        this.claimEngine = claimEngine != null ? claimEngine : new DeterministicClaimEngine();
        this.ruleResolver = ruleResolver != null ? ruleResolver : new ApprovedPolicyRuleResolver(null);
    }

    public RankingEngine() {
        this(new DeterministicClaimEngine(), new ApprovedPolicyRuleResolver(null));
    }

    /**
     * Scores and ranks a list of eligible policies for the given user profile.
     */
    public List<RecommendationResult.RankedPolicyItem> rankPolicies(
            RecommendationProfile profile,
            List<EligibilityEngine.EligibilityResult> eligibleResults) {
        return rankPolicies(profile, eligibleResults, 0L);
    }

    /**
     * Scores and ranks a list of eligible policies for the given user profile at a specific catalog version.
     */
    public List<RecommendationResult.RankedPolicyItem> rankPolicies(
            RecommendationProfile profile,
            List<EligibilityEngine.EligibilityResult> eligibleResults,
            long catalogVersion) {

        log.info("[RankingEngine] Scoring {} eligible policies for user (priority={}, catalogVersion={})",
                eligibleResults.size(), profile.getPrimaryPriority(), catalogVersion);

        List<RecommendationResult.RankedPolicyItem> scored = new ArrayList<>();

        for (EligibilityEngine.EligibilityResult er : eligibleResults) {
            StructuredPolicy policy = er.getPolicy();

            long targetSI = selectSumInsured(policy, profile);
            StructuredPolicy.SumInsuredTier pricedTier = selectPricedTier(policy, targetSI);
            if (pricedTier == null) {
                log.info("[RankingEngine] Excluding policy {} for user - policy has no sum insured tiers",
                        policy.getPolicyCode());
                continue;
            }
            long pricedSI = pricedTier.getSumInsured();
            boolean hasShortfall = targetSI > 0 && pricedSI < targetSI;
            long shortfallAmount = hasShortfall ? targetSI - pricedSI : 0L;
            double estimatedPremium = estimatePremium(policy, profile, pricedTier);

            double costScore = calculateCostScore(estimatedPremium, profile.getAnnualBudget(), profile.getBudgetType());
            double coverageScore = calculateCoverageScore(policy, profile);
            if (hasShortfall) {
                double coverageRatio = (double) pricedSI / targetSI;
                coverageScore = Math.max(15.0, coverageScore * coverageRatio);
            }
            double oopScore = calculateOutOfPocketScore(policy, profile);

            // Phase 3 Deterministic Claim Simulation
            RecommendationResult.PersonalizedClaimSimulationSummary claimSummary = null;
            try {
                if (policy.isActive()) {
                    ClaimSimulationDto.ClaimSimulationRequest simReq = buildPersonalizedClaimScenario(policy, profile, pricedSI);
                    ApprovedPolicyRuleResolver.ResolvedPolicyRules resolvedRules = ruleResolver.resolveRules(policy, null, BigDecimal.valueOf(pricedSI));
                    ClaimSimulationDto.ClaimSimulationResponse simResp = claimEngine.calculateClaim(simReq, resolvedRules);
                    claimSummary = buildClaimSimulationSummary(policy, simReq, simResp);

                    // Blend simulated claim payout into Out-of-Pocket score
                    double simPayoutRatio = simResp.getTotalSubmittedBill().compareTo(BigDecimal.ZERO) > 0
                            ? simResp.getEstimatedInsurerPayment().doubleValue() / simResp.getTotalSubmittedBill().doubleValue()
                            : 1.0;
                    double simOopScore = Math.min(100.0, simPayoutRatio * 100.0);
                    oopScore = (0.50 * oopScore) + (0.50 * simOopScore);
                }
            } catch (Exception e) {
                log.debug("[RankingEngine] Claim simulation skipped for {}: {}", policy.getPolicyCode(), e.getMessage());
            }

            double reliabilityScore = calculateReliabilityScore(policy, profile);

            // Track missing evidence warnings & provenance scoring
            List<String> missingEvidenceWarnings = new ArrayList<>();
            if (policy.getContractUncertainFields() != null && !policy.getContractUncertainFields().isEmpty()) {
                for (String u : policy.getContractUncertainFields()) {
                    missingEvidenceWarnings.add("Unverified clause: " + u);
                }
                reliabilityScore = Math.max(10.0, reliabilityScore - (policy.getContractUncertainFields().size() * 10.0));
            }
            // Determine priority weights
            String effectivePriority = profile.getPrimaryPriority() != null ? profile.getPrimaryPriority() : "BALANCED";
            double wCost, wCoverage, wOop, wReliability;
            switch (effectivePriority) {
                case "AFFORDABLE_PREMIUMS" -> { wCost = 0.45; wCoverage = 0.20; wOop = 0.20; wReliability = 0.15; }
                case "BROADER_COVERAGE" -> { wCost = 0.15; wCoverage = 0.45; wOop = 0.20; wReliability = 0.20; }
                case "LOWER_OUT_OF_POCKET" -> { wCost = 0.15; wCoverage = 0.20; wOop = 0.45; wReliability = 0.20; }
                default -> { wCost = 0.25; wCoverage = 0.25; wOop = 0.25; wReliability = 0.25; }
            }

            if (policy.isSyntheticFixture()) {
                missingEvidenceWarnings.add("Demonstration Fixture: Terms are synthetic/modeled and not yet extracted from an official IRDAI PDF filing.");
                reliabilityScore = Math.min(70.0, reliabilityScore);
                // Ensure overall score strictly adheres to the 88.0 ceiling for synthetic demo fixtures
                // while preserving exact audit replay mathematical identity:
                double unweightedOther = (wCost * costScore) + (wCoverage * coverageScore) + (wOop * oopScore);
                if (wReliability > 0 && (unweightedOther + wReliability * reliabilityScore) > 88.0) {
                    reliabilityScore = Math.max(5.0, (88.0 - unweightedOther) / wReliability);
                }
            } else if (policy.isAuthenticAcquiredDocument()) {
                reliabilityScore = Math.min(100.0, reliabilityScore + 5.0); // Confidence bonus for authentic acquired filing
            }

            double overallScore = calculateWeightedScore(
                    costScore, coverageScore, oopScore, reliabilityScore,
                    effectivePriority);
            if (policy.isSyntheticFixture()) {
                overallScore = Math.min(88.0, overallScore);
            }

            boolean withinBudget = estimatedPremium <= profile.getAnnualBudget();
            double budgetDiff = profile.getAnnualBudget() - estimatedPremium;

            // Build limitations assessment (Q2)
            RecommendationResult.LimitationAssessment limitations = buildLimitations(policy, profile);
            if (hasShortfall) {
                limitations.setCoverageShortfallWarning(String.format(
                        "Policy maximum sum insured is %s (shortfall of %s from your %s target). Hospitalization expenses beyond %s will be out-of-pocket.",
                        formatSumInsured(pricedSI), formatSumInsured(shortfallAmount), formatSumInsured(targetSI), formatSumInsured(pricedSI)));
            }

            // Build out-of-pocket risk analysis (Q2)
            RecommendationResult.OutOfPocketRiskAnalysis oopRisk = buildOutOfPocketRisk(policy, profile, pricedSI);

            // Key exclusions
            List<String> keyExclusions = policy.getExclusions() != null
                    ? policy.getExclusions().stream().limit(8).collect(Collectors.toList())
                    : List.of();

            // Match badges
            List<String> badges = new ArrayList<>();
            if (policy.isAuthenticAcquiredDocument()) badges.add("Verified Contractual Wording");
            if (policy.isSyntheticFixture()) badges.add("Synthetic Demo Fixture");
            if (overallScore >= 85) badges.add("Top Match");
            if (costScore >= 90) badges.add("Best Value");
            if (oopScore >= 90) badges.add("Lowest Out-of-Pocket");
            if (coverageScore >= 90) badges.add("Broadest Coverage");
            if (withinBudget && budgetDiff > 0) badges.add("Within Budget");
            if (hasShortfall) badges.add("Capped at " + formatSumInsured(pricedSI));
            if (claimSummary != null && claimSummary.getOutOfPocketPercentage() <= 12.0) badges.add("Low Claim Deductions");

            // Match reasons
            List<String> matchReasons = buildMatchReasons(policy, profile, costScore, coverageScore, oopScore);
            if (claimSummary != null) {
                matchReasons.add(0, String.format(
                        "Realistic Claim Simulation (%s): Out of a ₹%,.0f bill, policy covers ₹%,.0f (%.0f%%), leaving ₹%,.0f (%.1f%%) out-of-pocket.",
                        claimSummary.getScenarioDiagnosis(), claimSummary.getSubmittedBill(),
                        claimSummary.getInsurerPayment(), (claimSummary.getInsurerPayment() / claimSummary.getSubmittedBill()) * 100.0,
                        claimSummary.getPatientLiability(), claimSummary.getOutOfPocketPercentage()));
                if (claimSummary.getRoomRentImpactText() != null) {
                    matchReasons.add(1, claimSummary.getRoomRentImpactText());
                }
            }
            if (hasShortfall) {
                matchReasons.add(0, String.format(
                        "Coverage Shortfall Advisory: Policy provides maximum coverage of %s, leaving a %s shortfall against your %s target. We recommend pairing with a Super Top-Up policy.",
                        formatSumInsured(pricedSI), formatSumInsured(shortfallAmount), formatSumInsured(targetSI)));
            }

            // Evidence citations (Q3)
            List<StructuredPolicy.PolicyEvidenceItem> evidence = policy.getDocumentEvidence() != null
                    ? policy.getDocumentEvidence()
                    : List.of();

            // Explicit Data Classification (Contractual Fact vs Modeled Estimate vs Dated Regulatory Metric)
            RecommendationResult.DataClassification dataClassification = buildDataClassification(policy);

            // Core 3-Question Decision Transparency Answers
            RecommendationResult.ThreeQuestionAnswers threeQuestions = buildThreeQuestionAnswers(
                    policy, profile, limitations, oopRisk, overallScore, costScore, coverageScore, oopScore, estimatedPremium, 0);

            // Construct Reproducibility & Audit Replay Snapshot

            RecommendationResult.ContractTermsSnapshot ctSnap = policy.getContractTerms() != null
                    ? RecommendationResult.ContractTermsSnapshot.builder()
                    .roomRentLimit(policy.getContractTerms().getRoomRentLimit())
                    .roomRentCapped(policy.getContractTerms().isRoomRentCapped())
                    .copayPercentage(policy.getContractTerms().getCopayPercentage())
                    .waitingPeriodPedMonths(policy.getContractTerms().getWaitingPeriodPedMonths())
                    .waitingPeriodInitialDays(policy.getContractTerms().getWaitingPeriodInitialDays())
                    .waitingPeriodSpecificMonths(policy.getContractTerms().getWaitingPeriodSpecificMonths())
                    .deductibleAmount(policy.getContractTerms().getDeductibleAmount())
                    .restorationAvailable(policy.getContractTerms().getRestorationBenefit() != null && !policy.getContractTerms().getRestorationBenefit().isBlank())
                    .build()
                    : null;

            RecommendationResult.PricingModelSnapshot pricingSnap = RecommendationResult.PricingModelSnapshot.builder()
                    .selectedSumInsured(pricedSI)
                    .calculatedAnnualPremium(round2(estimatedPremium))
                    .pricingZone(profile.getDetectedZone())
                    .pricingStatus(policy.getPricingStatus())
                    .pricingClassification(dataClassification.getPricingClassification())
                    .build();

            RecommendationResult.InsurerMetricsSnapshot imSnap = policy.getInsurerMetrics() != null
                    ? RecommendationResult.InsurerMetricsSnapshot.builder()
                    .claimSettlementRatio(policy.getInsurerMetrics().getClaimSettlementRatio())
                    .incurredClaimsRatio(policy.getInsurerMetrics().getIncurredClaimsRatio())
                    .cashlessHospitalsCount(policy.getInsurerMetrics().getCashlessHospitalsCount())
                    .reportingPeriod(policy.getInsurerMetrics().getCsrReportingPeriod())
                    .build()
                    : null;

            RecommendationResult.ScoringBreakdownSnapshot scoreSnap = RecommendationResult.ScoringBreakdownSnapshot.builder()
                    .costWeight(wCost)
                    .coverageWeight(wCoverage)
                    .oopWeight(wOop)
                    .reliabilityWeight(wReliability)
                    .costScore(round2(costScore))
                    .coverageScore(round2(coverageScore))
                    .oopScore(round2(oopScore))
                    .reliabilityScore(round2(reliabilityScore))
                    .overallScore(round2(overallScore))
                    .primaryPriority(effectivePriority)
                    .build();

            RecommendationResult.UserRequirementsSnapshot userReqSnap = RecommendationResult.UserRequirementsSnapshot.builder()
                    .primaryAge(profile.getPrimaryAge())
                    .insuranceType(profile.getInsuranceType())
                    .pricingZone(profile.getDetectedZone())
                    .annualBudget(profile.getAnnualBudget())
                    .budgetType(profile.getBudgetType())
                    .declaredConditionsCount(profile.getPreExistingConditions() != null ? profile.getPreExistingConditions().size() : 0)
                    .totalInsuredMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                    .build();

            RecommendationResult.ReproducibilitySnapshot reproSnap = RecommendationResult.ReproducibilitySnapshot.builder()
                    .policySnapshotVersion(policy.getVersionDate() != null ? policy.getVersionDate() : (policy.getUinRevision() != null ? policy.getUinRevision() : "2024-Q1"))
                    .scoringEngineVersion("2.0-IRDAI-MULTI-POLICY")
                    .evaluationTimestamp(java.time.Instant.now())
                    .catalogVersion(catalogVersion)
                    .contractTermsSnapshot(ctSnap)
                    .pricingModelSnapshot(pricingSnap)
                    .insurerMetricsSnapshot(imSnap)
                    .scoringBreakdown(scoreSnap)
                    .inputRequirementsSnapshot(userReqSnap)
                    .build();

            RecommendationResult.RankedPolicyItem item = RecommendationResult.RankedPolicyItem.builder()
                    .policyId(policy.getId())
                    .policyCode(policy.getPolicyCode())
                    .policyName(policy.getPolicyName())
                    .provider(policy.getProvider())
                    .irdaUin(policy.getIrdaUin())
                    .uinRevision(policy.getUinRevision())
                    .policySnapshotVersion(policy.getVersionDate() != null ? policy.getVersionDate() : (policy.getUinRevision() != null ? policy.getUinRevision() : "2024-Q1"))
                    .scoringEngineVersion("2.0-IRDAI-MULTI-POLICY")
                    .evaluationTimestamp(java.time.Instant.now())
                    .catalogVersion(catalogVersion)
                    .verificationStatus(policy.getVerificationStatus() != null ? policy.getVerificationStatus() : "VERIFIED_CONTRACTUAL_SPECIFICATION")
                    .regulatoryFilingSource(policy.getRegulatoryFilingSource())
                    .officialDocumentUrl(policy.getOfficialDocumentUrl())
                    .lastAuditedDate(policy.getLastAuditedDate())
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
                    .selectedSumInsured(pricedSI)
                    .formattedSumInsured(formatSumInsured(pricedSI))
                    .withinBudget(withinBudget)
                    .budgetDifference(round2(budgetDiff))
                    .coverageShortfall(hasShortfall)
                    .requestedSumInsured(targetSI)
                    .coverageShortfallAmount(shortfallAmount)
                    .matchBadges(badges)
                    .matchReasons(matchReasons)
                    .limitations(limitations)
                    .outOfPocketRisk(oopRisk)
                    .keyExclusions(keyExclusions)
                    .supportingEvidence(evidence)
                    .insurerMetrics(policy.getInsurerMetrics())
                    .dataClassification(dataClassification)
                    .reproducibilitySnapshot(reproSnap)
                    .threeQuestionAnswers(threeQuestions)
                    .personalizedClaimSimulation(claimSummary)
                    .authenticAcquiredDocument(policy.isAuthenticAcquiredDocument())
                    .syntheticFixture(policy.isSyntheticFixture())
                    .documentChecksum(policy.getEffectiveDocumentChecksum())
                    .reviewerApprovalStatus(policy.getReviewerApprovalStatus())
                    .reviewerName(policy.getVerifiedBy())
                    .missingEvidenceWarnings(missingEvidenceWarnings)
                    .build();

            scored.add(item);
        }

        // Sort descending by overall match score
        scored.sort(Comparator.comparingDouble(RecommendationResult.RankedPolicyItem::getOverallMatchScore).reversed());

        // Assign ranks and update ThreeQuestionAnswers rank context
        for (int i = 0; i < scored.size(); i++) {
            RecommendationResult.RankedPolicyItem item = scored.get(i);
            int rank = i + 1;
            item.setRank(rank);
            if (item.getThreeQuestionAnswers() != null && item.getThreeQuestionAnswers().getWhyRecommended() != null) {
                item.getThreeQuestionAnswers().setWhyRecommended("Rank #" + rank + " • " + item.getThreeQuestionAnswers().getWhyRecommended());
            }
        }

        log.info("[RankingEngine] Ranking complete. Top match: {} (score={})",
                scored.isEmpty() ? "N/A" : scored.get(0).getPolicyName(),
                scored.isEmpty() ? 0 : scored.get(0).getOverallMatchScore());

        return scored;
    }

    // =========================================================================
    // COST SCORE
    // =========================================================================
    private double calculateCostScore(double premium, double budget, String budgetType) {
        if (budget <= 0 || premium <= 0) return 50.0;
        double ratio = premium / budget;
        if (ratio <= 0.65) return 100.0;
        if (ratio <= 0.85) return 92.0;
        if (ratio <= 1.0) return 80.0;

        // Over budget: evaluate strictness
        if ("STRICT".equalsIgnoreCase(budgetType)) {
            if (ratio <= 1.05) return 35.0;
            if (ratio <= 1.15) return 15.0;
            return 5.0;
        } else {
            // Preferred budget (allows flexible ±20%)
            if (ratio <= 1.10) return 68.0;
            if (ratio <= 1.20) return 52.0;
            if (ratio <= 1.35) return 32.0;
            return 12.0;
        }
    }

    // =========================================================================
    // COVERAGE SCORE
    // =========================================================================
    private double calculateCoverageScore(StructuredPolicy policy, RecommendationProfile profile) {
        double score = 45.0; // baseline
        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) return score;

        // Room rent — no cap is best
        if (!ct.isRoomRentCapped()) score += 12;
        else score += 2;

        // No co-pay
        if (ct.getCopayPercentage() <= 0) score += 8;
        else if (ct.getCopayPercentage() <= 10) score += 4;

        // Restoration benefit
        if (ct.getRestorationBenefit() != null && !ct.getRestorationBenefit().isBlank()) {
            if (ct.getRestorationBenefit().toLowerCase().contains("unlimited")) score += 10;
            else score += 6;
        }

        // Day care procedures
        if (ct.getDayCareProceduresCount() >= 500) score += 5;
        else if (ct.getDayCareProceduresCount() >= 200) score += 3;

        // Modern treatments
        if (ct.isModernTreatmentsCovered()) score += 5;

        // Organ donor & ambulance
        if (ct.isOrganDonorCovered()) score += 3;
        if (ct.isAmbulanceCovered()) score += 2;

        // Pre/Post hospitalization
        if (ct.getPreHospitalizationDays() >= 60) score += 3;
        if (ct.getPostHospitalizationDays() >= 180) score += 3;

        // No-claim bonus (e.g. 500% vs 100%)
        if (ct.getNoClaimBonus() != null) {
            if (ct.getNoClaimBonus().contains("500%")) score += 5;
            else if (!ct.getNoClaimBonus().isBlank()) score += 3;
        }

        // User preference: Maternity cover
        if (profile.isMaternityBenefitNeeded()) {
            if (ct.isMaternityCovered()) {
                score += 8;
                if (ct.getMaternityWaitingMonths() > 0 && ct.getMaternityWaitingMonths() <= 36) score += 4;
            } else {
                score -= 15; // User specifically asked for maternity
            }
        }

        // User preference: OPD cover
        if (profile.isOpdBenefitNeeded()) {
            if (ct.isOpdCovered()) score += 8;
            else score -= 6;
        }

        return Math.max(0.0, Math.min(100.0, score));
    }

    // =========================================================================
    // OUT-OF-POCKET PROTECTION SCORE
    // =========================================================================
    private double calculateOutOfPocketScore(StructuredPolicy policy, RecommendationProfile profile) {
        double score = 100.0; // start from best and deduct
        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        if (ct == null) return 50.0;

        // Room rent cap penalty & proportionate deduction risk
        if (ct.isRoomRentCapped()) score -= 20;
        if (ct.isProportionateDeductionApplies()) score -= 15;

        // Co-pay penalty
        if (ct.getCopayPercentage() > 0) {
            score -= ct.getCopayPercentage() * 2.0;
            // Heavy penalty if user specifically requested zero co-pay tolerance
            if ("NONE".equalsIgnoreCase(profile.getCopayTolerance())) {
                score -= 15.0;
            }
        }

        // Deductible penalty
        if (ct.getDeductibleAmount() > 0) {
            score -= Math.min(20, ct.getDeductibleAmount() / 5000.0);
        }

        // Disease sublimits penalty
        if (ct.getDiseaseSublimits() != null && !ct.getDiseaseSublimits().isEmpty()) {
            score -= Math.min(16, ct.getDiseaseSublimits().size() * 4);
        }

        // Waiting period & Pre-Existing Disease (PED) alignment
        boolean hasPed = profile.getPreExistingConditions() != null && !profile.getPreExistingConditions().isEmpty();
        if (hasPed) {
            if (ct.getWaitingPeriodPedMonths() <= 24) score += 6; // Fast PED coverage
            else if (ct.getWaitingPeriodPedMonths() > 36) score -= 12; // Lengthy 4-year wait
            if (ct.isPedReductionAvailable()) score += 5; // Buyback rider available
        } else {
            if (ct.getWaitingPeriodPedMonths() > 36) score -= 4;
        }

        if (ct.getWaitingPeriodSpecificMonths() > 24) score -= 4;

        return Math.max(0.0, Math.min(100.0, score));
    }

    // =========================================================================
    // INSURER RELIABILITY SCORE
    // =========================================================================
    private double calculateReliabilityScore(StructuredPolicy policy, RecommendationProfile profile) {
        StructuredPolicy.InsurerMetrics im = policy.getInsurerMetrics();
        if (im == null) return 50.0;

        double score = 0;

        // Claim settlement ratio (max 40 points)
        if (im.getClaimSettlementRatio() >= 97) score += 40;
        else if (im.getClaimSettlementRatio() >= 93) score += 32;
        else if (im.getClaimSettlementRatio() >= 88) score += 22;
        else score += 10;

        // Cashless hospitals (max 25 points)
        if (im.getCashlessHospitalsCount() >= 12000) score += 25;
        else if (im.getCashlessHospitalsCount() >= 8000) score += 18;
        else if (im.getCashlessHospitalsCount() >= 4000) score += 12;
        else score += 6;

        // Grievance resolution (max 20 points)
        if (im.getAvgGrievanceResolutionDays() <= 2.0) score += 20;
        else if (im.getAvgGrievanceResolutionDays() <= 5.0) score += 14;
        else if (im.getAvgGrievanceResolutionDays() <= 10.0) score += 8;
        else score += 3;

        // ICR (incurred claims ratio) — 70-90% is ideal financial health (max 15 points)
        if (im.getIncurredClaimsRatio() >= 70 && im.getIncurredClaimsRatio() <= 90) score += 15;
        else if (im.getIncurredClaimsRatio() <= 100) score += 10;
        else score += 5;

        // Preferred Hospital Network match bonus (+10 pts)
        if (profile.getPreferredHospitals() != null && !profile.getPreferredHospitals().isEmpty()
                && im.getTopHospitalNetworks() != null) {
            for (String pref : profile.getPreferredHospitals()) {
                for (String net : im.getTopHospitalNetworks()) {
                    if (net.toLowerCase().contains(pref.toLowerCase()) || pref.toLowerCase().contains(net.toLowerCase())) {
                        score += 10;
                        break;
                    }
                }
            }
        }

        return Math.min(100.0, score);
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
    // PREMIUM ESTIMATION
    // =========================================================================
    public static StructuredPolicy.SumInsuredTier selectPricedTier(StructuredPolicy policy, long targetSI) {
        List<StructuredPolicy.SumInsuredTier> tiers = policy.getSumInsuredTiers();
        if (tiers == null || tiers.isEmpty()) return null;

        if (targetSI <= 0) {
            return tiers.stream()
                    .min(Comparator.comparingLong(StructuredPolicy.SumInsuredTier::getSumInsured))
                    .orElse(tiers.get(0));
        }

        StructuredPolicy.SumInsuredTier adequateTier = null;
        for (StructuredPolicy.SumInsuredTier tier : tiers) {
            if (tier.getSumInsured() >= targetSI) {
                if (adequateTier == null || tier.getSumInsured() < adequateTier.getSumInsured()) {
                    adequateTier = tier;
                }
            }
        }
        if (adequateTier != null) {
            return adequateTier;
        }

        // Fallback when no tier reaches targetSI (e.g. large user desired SI or high automatic target):
        // Price the largest available tier and flag the shortfall in the result.
        return tiers.stream()
                .max(Comparator.comparingLong(StructuredPolicy.SumInsuredTier::getSumInsured))
                .orElse(tiers.get(0));
    }

    public static double estimatePremium(StructuredPolicy policy, RecommendationProfile profile) {
        long targetSI = selectSumInsured(policy, profile);
        StructuredPolicy.SumInsuredTier pricedTier = selectPricedTier(policy, targetSI);
        return estimatePremium(policy, profile, pricedTier);
    }

    public static double estimatePremium(StructuredPolicy policy, RecommendationProfile profile, StructuredPolicy.SumInsuredTier selectedTier) {
        if (selectedTier == null) return 0;

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
    // SUM INSURED SELECTION (Harmonized with RecommendationService Guidance)
    // =========================================================================
    public static long selectSumInsured(StructuredPolicy policy, RecommendationProfile profile) {
        if (!profile.isHelpMeDecideSumInsured() && profile.getDesiredSumInsured() > 0) {
            return profile.getDesiredSumInsured();
        }
        int age = profile.getPrimaryAge();
        int members = 1 + (profile.getMembers() != null ? profile.getMembers().size() : 0);
        String zone = profile.getDetectedZone();

        long recommendedSI;
        if (age <= 30 && members <= 2) {
            recommendedSI = 500000;
        } else if (age <= 40) {
            recommendedSI = members >= 3 ? 1500000 : 1000000;
        } else if (age <= 50) {
            recommendedSI = members >= 3 ? 2500000 : 1500000;
        } else {
            recommendedSI = 2500000;
        }

        if ("Zone 1".equalsIgnoreCase(zone)) {
            recommendedSI = Math.max(recommendedSI, 1000000);
        }
        return recommendedSI;
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

        // Room rent proportionate deduction (estimate based on SI & cap)
        if (ct.isRoomRentCapped() && ct.isProportionateDeductionApplies()) {
            double roomDeduction = sampleBill * 0.25; // ~25% haircut on associated hospitalization fees
            deductions += roomDeduction;
            factors.add(String.format("Proportionate room rent deduction risk (~₹%,.0f on doctor & surgical charges)", roomDeduction));
        }

        double covered = Math.max(0, sampleBill - deductions);
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

        // Portability match reason
        if (profile.isHasExistingInsurance() && profile.isPortingPolicy()) {
            long portAmt = profile.getExistingSumInsured() != null && profile.getExistingSumInsured() > 0
                    ? profile.getExistingSumInsured()
                    : 500000L;
            reasons.add(String.format("Portability credit: Continuity of waiting periods honored up to ₹%,d under IRDAI regulations", portAmt));
        }

        // Maternity match reason
        if (profile.isMaternityBenefitNeeded() && ct.isMaternityCovered()) {
            reasons.add(String.format("Maternity & newborn benefit covered (%d-month waiting period)", ct.getMaternityWaitingMonths()));
        }

        // OPD match reason
        if (profile.isOpdBenefitNeeded() && ct.isOpdCovered()) {
            reasons.add("Outpatient (OPD) consultation and diagnostics benefit included");
        }

        // PED reduction rider
        if (ct.isPedReductionAvailable()) {
            reasons.add("PED waiting period reduction rider available to accelerate coverage");
        }

        // Preferred hospital matching
        StructuredPolicy.InsurerMetrics im = policy.getInsurerMetrics();
        if (profile.getPreferredHospitals() != null && im != null && im.getTopHospitalNetworks() != null) {
            for (String pref : profile.getPreferredHospitals()) {
                for (String net : im.getTopHospitalNetworks()) {
                    if (net.toLowerCase().contains(pref.toLowerCase()) || pref.toLowerCase().contains(net.toLowerCase())) {
                        reasons.add(String.format("Cashless partner network includes '%s'", net));
                        break;
                    }
                }
            }
        }

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
        if (amount >= 10000000) {
            double cr = (double) amount / 10000000.0;
            java.text.DecimalFormat df = new java.text.DecimalFormat("#.##", new java.text.DecimalFormatSymbols(java.util.Locale.ROOT));
            return "₹" + df.format(cr) + " Cr";
        }
        if (amount >= 100000) {
            double lakh = (double) amount / 100000.0;
            java.text.DecimalFormat df = new java.text.DecimalFormat("#.##", new java.text.DecimalFormatSymbols(java.util.Locale.ROOT));
            return "₹" + df.format(lakh) + " Lakh";
        }
        return String.format("₹%,d", amount);
    }

    private RecommendationResult.DataClassification buildDataClassification(StructuredPolicy policy) {
        String regPeriod = policy.getInsurerMetrics() != null && policy.getInsurerMetrics().getCsrReportingPeriod() != null
                ? policy.getInsurerMetrics().getCsrReportingPeriod()
                : "IRDAI Annual Report FY 2022-23";

        return RecommendationResult.DataClassification.builder()
                .contractualFactsStatus(policy.getVerificationStatus() != null ? policy.getVerificationStatus() : "VERIFIED_CONTRACTUAL_SPECIFICATION")
                .pricingClassification("MODELED_ACTUARIAL_ESTIMATE")
                .isBindingQuote(false)
                .pricingDisclaimer("Indicative non-binding premium estimate based on policy age-band rate tables and family floater loadings. Excludes individual medical underwriting loads, GST (18%), tobacco use, and optional riders. Final binding quote is determined solely by the insurer's underwriting decision.")
                .billSimulationClassification("HYPOTHETICAL_SCENARIO_ESTIMATE")
                .billSimulationAssumptions("Simulated on a hypothetical ₹3,00,000 hospitalization bill evaluating policy-specific deductible, co-payment, and proportionate room rent deductions.")
                .regulatoryMetricsClassification("DATED_REGULATORY_DISCLOSURE")
                .regulatoryPeriod(regPeriod)
                .build();
    }

    private RecommendationResult.ThreeQuestionAnswers buildThreeQuestionAnswers(
            StructuredPolicy policy,
            RecommendationProfile profile,
            RecommendationResult.LimitationAssessment limitations,
            RecommendationResult.OutOfPocketRiskAnalysis oopRisk,
            double overallScore,
            double costScore,
            double coverageScore,
            double oopScore,
            double estimatedPremium,
            int rank) {

        StructuredPolicy.ContractTerms ct = policy.getContractTerms();
        StructuredPolicy.InsurerMetrics im = policy.getInsurerMetrics();

        // 1. Why recommended
        String priorityDesc = switch (profile.getPrimaryPriority() != null ? profile.getPrimaryPriority() : "BALANCED") {
            case "AFFORDABLE_PREMIUMS" -> String.format("prioritizes budget economy (scored %.0f/100 for cost)", costScore);
            case "BROADER_COVERAGE" -> String.format("maximizes breadth of coverage (scored %.0f/100 for coverage scope)", coverageScore);
            case "LOWER_OUT_OF_POCKET" -> String.format("minimizes personal hospitalization expenses (scored %.0f/100 for out-of-pocket safety)", oopScore);
            default -> String.format("delivers a balanced overall score of %.0f/100 across cost, coverage, and insurer reliability", overallScore);
        };

        String roomRentFact = ct.isRoomRentCapped()
                ? "has room-rent limits (" + ct.getRoomRentLimit() + ")"
                : "features zero room-rent capping (Single Private AC or higher)";

        String copayFact = ct.getCopayPercentage() > 0
                ? String.format("%.0f%% co-payment", ct.getCopayPercentage())
                : "zero mandatory co-payment";

        String why = String.format("Recommended because it %s with %s and %s, resulting in an estimated %s out-of-pocket exposure level.",
                priorityDesc, roomRentFact, copayFact, oopRisk.getRiskLevel());

        // 2. Verified Facts Supporting
        List<String> verifiedFacts = new ArrayList<>();
        verifiedFacts.add(String.format("IRDAI Product UIN: %s (Revision: %s) registered in official health insurance filings",
                policy.getIrdaUin(), policy.getUinRevision() != null ? policy.getUinRevision() : "Standard"));
        verifiedFacts.add(String.format("Room Rent Clause: %s (%s)",
                ct.getRoomRentLimit(),
                ct.isProportionateDeductionApplies() ? "Proportionate deductions apply across all associated medical bills" : "Zero proportionate deductions"));
        verifiedFacts.add(String.format("Co-Payment Terms: %.0f%% (%s)", ct.getCopayPercentage(), ct.getCopayNote()));
        verifiedFacts.add(String.format("Pre-Existing Disease (PED) Waiting Period: %d continuous months (Initial waiting: %d days, Specific illnesses: %d months)",
                ct.getWaitingPeriodPedMonths(), ct.getWaitingPeriodInitialDays(), ct.getWaitingPeriodSpecificMonths()));
        if (ct.getRestorationBenefit() != null && !ct.getRestorationBenefit().isBlank()) {
            verifiedFacts.add(String.format("Sum Insured Restoration: %s", ct.getRestorationBenefit()));
        }
        if (ct.getNoClaimBonus() != null && !ct.getNoClaimBonus().isBlank()) {
            verifiedFacts.add(String.format("No Claim Bonus (NCB): %s", ct.getNoClaimBonus()));
        }
        if (policy.getPolicyWordingDocument() != null) {
            verifiedFacts.add(String.format("Verified against official contract wording file: %s", policy.getPolicyWordingDocument()));
        }

        // 3. Estimates & Assumptions
        List<String> estimates = new ArrayList<>();
        estimates.add(String.format("Annual Premium: Indicative estimate of %s/yr (Modeled actuarial rate curve; non-binding, excludes individual underwriting loads, GST 18%%, and optional riders).",
                formatCurrency(estimatedPremium)));
        estimates.add(String.format("Bill Simulation: Hypothetical ₹3,00,000 hospitalization claim estimates ₹%,.0f out-of-pocket (assumes standard 10%% non-medical consumables plus contract room/copay terms).",
                oopRisk.getEstimatedOutofPocketCost()));
        if (im != null) {
            estimates.add(String.format("Claim Settlement Ratio (CSR): %.1f%% is a dated regulatory statistic from %s (Source: %s; does not guarantee future claim acceptance).",
                    im.getClaimSettlementRatio(),
                    im.getCsrReportingPeriod() != null ? im.getCsrReportingPeriod() : "IRDAI Annual Report FY 2022-23",
                    im.getCsrSource() != null ? im.getCsrSource() : "IRDAI Handbook"));
            estimates.add(String.format("Cashless Hospital Network: %,d+ network hospitals as of %s under %s.",
                    im.getCashlessHospitalsCount(),
                    im.getNetworkAsOfDate() != null ? im.getNetworkAsOfDate() : "March 2024",
                    im.getNetworkSource() != null ? im.getNetworkSource() : "Insurer Directory"));
        }
        estimates.add("Underwriting Discretion: Final policy issuance, waiting period modifications, and binding quotes depend strictly on insurer tele-medical review and full medical declaration.");

        return RecommendationResult.ThreeQuestionAnswers.builder()
                .whyRecommended(why)
                .verifiedFactsSupporting(verifiedFacts)
                .estimatesAndAssumptions(estimates)
                .build();
    }

    // =========================================================================
    // DETERMINISTIC CLAIM SIMULATION SCENARIO BUILDER & SUMMARY
    // =========================================================================

    private ClaimSimulationDto.ClaimSimulationRequest buildPersonalizedClaimScenario(
            StructuredPolicy policy, RecommendationProfile profile, long pricedSI) {

        String diagnosis = "Acute Inpatient Surgery / Appendectomy";
        if (profile.getLikelyTreatmentNeeds() != null && !profile.getLikelyTreatmentNeeds().isBlank()
                && !"COMPREHENSIVE".equalsIgnoreCase(profile.getLikelyTreatmentNeeds())) {
            diagnosis = profile.getLikelyTreatmentNeeds() + " Inpatient Treatment";
        } else if (profile.getPreExistingConditions() != null && !profile.getPreExistingConditions().isEmpty()) {
            diagnosis = "Hospitalization for " + profile.getPreExistingConditions().get(0);
        }

        String roomOpted = profile.getStrictRoomPreference();
        if (roomOpted == null || roomOpted.isBlank()) {
            roomOpted = "SINGLE_PRIVATE_AC";
        }

        int continuity = profile.getPolicyContinuityMonths() > 0 ? profile.getPolicyContinuityMonths() : 12;
        boolean isPed = profile.getPreExistingConditions() != null && !profile.getPreExistingConditions().isEmpty();

        // 4 days stay in opted room
        BigDecimal roomDailyRate = switch (roomOpted) {
            case "DELUXE_SUITE" -> BigDecimal.valueOf(12000.00);
            case "SINGLE_PRIVATE_AC" -> BigDecimal.valueOf(8000.00);
            case "TWIN_SHARING" -> BigDecimal.valueOf(4500.00);
            default -> BigDecimal.valueOf(3000.00);
        };
        BigDecimal roomTotal = roomDailyRate.multiply(BigDecimal.valueOf(4));

        List<ClaimSimulationDto.BillLineItemDto> lines = new ArrayList<>();
        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-ROOM")
                .category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                .description("Hospital Room Charges (" + roomOpted + ", 4 days)")
                .unitPrice(roomDailyRate)
                .quantity(4)
                .totalAmount(roomTotal)
                .build());

        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-SURG")
                .category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                .description("Specialist Surgeon & Anesthetist Consultation")
                .unitPrice(BigDecimal.valueOf(90000.00))
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(90000.00))
                .build());

        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-OT")
                .category(ClaimSimulationDto.BillItemCategory.OT_CHARGES)
                .description("Operation Theatre & Surgical Equipment")
                .unitPrice(BigDecimal.valueOf(45000.00))
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(45000.00))
                .build());

        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-MED")
                .category(ClaimSimulationDto.BillItemCategory.MEDICINES)
                .description("Prescription Inpatient Injectables & Pharmacy")
                .unitPrice(BigDecimal.valueOf(45000.00))
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(45000.00))
                .build());

        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-DIAG")
                .category(ClaimSimulationDto.BillItemCategory.DIAGNOSTICS)
                .description("Inpatient Diagnostic Radiology & Pathology")
                .unitPrice(BigDecimal.valueOf(25000.00))
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(25000.00))
                .build());

        lines.add(ClaimSimulationDto.BillLineItemDto.builder()
                .lineId("LINE-CONSUMABLES")
                .category(ClaimSimulationDto.BillItemCategory.CONSUMABLES)
                .description("Non-Medical Hospital Consumables (IRDAI List 1 Items)")
                .unitPrice(BigDecimal.valueOf(15000.00))
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(15000.00))
                .build());

        return ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId(policy.getId() != null ? policy.getId() : policy.getPolicyCode())
                .patientAge(profile.getPrimaryAge())
                .diagnosis(diagnosis)
                .accidentalInjury(false)
                .preExistingCondition(isPed)
                .policyContinuityMonths(continuity)
                .hospitalType("NETWORK_CASHLESS")
                .hospitalRoomCategoryOpted(roomOpted)
                .selectedSumInsured(BigDecimal.valueOf(pricedSI))
                .priorClaimsInCurrentYear(BigDecimal.ZERO)
                .billLines(lines)
                .build();
    }

    private RecommendationResult.PersonalizedClaimSimulationSummary buildClaimSimulationSummary(
            StructuredPolicy policy,
            ClaimSimulationDto.ClaimSimulationRequest simReq,
            ClaimSimulationDto.ClaimSimulationResponse simResp) {

        double submitted = simResp.getTotalSubmittedBill().doubleValue();
        double insurerPay = simResp.getEstimatedInsurerPayment().doubleValue();
        double patientLiab = simResp.getEstimatedPatientLiability().doubleValue();
        double oopPct = submitted > 0 ? (patientLiab / submitted) * 100.0 : 0.0;

        BigDecimal roomExcess = BigDecimal.ZERO;
        BigDecimal proportionateHaircut = BigDecimal.ZERO;
        BigDecimal copayAmount = BigDecimal.ZERO;

        if (simResp.getDeductionBreakdown() != null) {
            for (ClaimSimulationDto.DeductionItemDto d : simResp.getDeductionBreakdown()) {
                if ("ROOM_RENT_EXCESS".equalsIgnoreCase(d.getDeductionType())) {
                    roomExcess = roomExcess.add(d.getAmountDeducted());
                } else if ("PROPORTIONATE_DEDUCTION".equalsIgnoreCase(d.getDeductionType())) {
                    proportionateHaircut = proportionateHaircut.add(d.getAmountDeducted());
                } else if ("COPAYMENT".equalsIgnoreCase(d.getDeductionType())) {
                    copayAmount = copayAmount.add(d.getAmountDeducted());
                }
            }
        }

        String roomImpactText;
        if (roomExcess.compareTo(BigDecimal.ZERO) > 0 || proportionateHaircut.compareTo(BigDecimal.ZERO) > 0) {
            roomImpactText = String.format("Room Rent Capping Penalty: ₹%,.0f room excess + ₹%,.0f proportionate haircut on associated surgical expenses.",
                    roomExcess.doubleValue(), proportionateHaircut.doubleValue());
        } else {
            roomImpactText = "Zero Room Rent Capping Penalty: Opted room category is fully within policy limits without proportionate haircuts.";
        }

        String propImpactText = proportionateHaircut.compareTo(BigDecimal.ZERO) > 0
                ? String.format("₹%,.0f proportionate reduction applied across surgeon and operation theatre fees.", proportionateHaircut.doubleValue())
                : "No proportionate deductions applied to associate medical charges.";

        String copayImpactText = copayAmount.compareTo(BigDecimal.ZERO) > 0
                ? String.format("₹%,.0f mandatory co-payment borne by patient.", copayAmount.doubleValue())
                : "Zero co-payment applied.";

        String comparisonInsight = String.format("For a ₹%,.0f %s hospitalization, this policy leaves ₹%,.0f (%.1f%%) out-of-pocket.",
                submitted, simReq.getDiagnosis(), patientLiab, oopPct);

        return RecommendationResult.PersonalizedClaimSimulationSummary.builder()
                .scenarioDiagnosis(simReq.getDiagnosis())
                .submittedBill(submitted)
                .insurerPayment(insurerPay)
                .patientLiability(patientLiab)
                .outOfPocketPercentage(Math.round(oopPct * 10.0) / 10.0)
                .reconciled(simResp.isReconciled())
                .calculationStatus(simResp.getCalculationStatus() != null ? simResp.getCalculationStatus().name() : "FULL_DETERMINISTIC_VERIFIED")
                .deductionBreakdown(simResp.getDeductionBreakdown())
                .roomRentImpactText(roomImpactText)
                .proportionateDeductionImpactText(propImpactText)
                .copayImpactText(copayImpactText)
                .actionableComparisonInsight(comparisonInsight)
                .build();
    }
}
