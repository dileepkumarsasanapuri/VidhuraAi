package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicyChangeImpactReport;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Policy Change Impact Analyzer.
 *
 * Compares an incoming draft policy revision against the existing active revision
 * to determine:
 * 1. Contractual clause diffs (Room rent, copay, PED waiting period, sublimits).
 * 2. Materiality assessment (whether changes negatively impact user out-of-pocket costs).
 * 3. Downstream impact on eligibility rules, ranking scores, and claim readiness simulations.
 * 4. Whether senior reviewer sign-off is required before publication.
 */
@Slf4j
@Service
public class PolicyChangeImpactAnalyzer {

    public PolicyChangeImpactReport analyzeImpact(StructuredPolicy previousActive, StructuredPolicy newDraft) {
        if (newDraft == null) {
            throw new IllegalArgumentException("New draft policy cannot be null for change impact analysis.");
        }

        Instant now = Instant.now();
        String policyCode = newDraft.getPolicyCode();
        String productName = newDraft.getPolicyName();
        String provider = newDraft.getProvider();
        String newRev = newDraft.getUinRevision() != null ? newDraft.getUinRevision() : "New Revision";
        String prevRev = previousActive != null && previousActive.getUinRevision() != null
                ? previousActive.getUinRevision() : "Initial Release";

        if (previousActive == null) {
            // First time ingestion of this product
            return PolicyChangeImpactReport.builder()
                    .policyCode(policyCode)
                    .productName(productName)
                    .provider(provider)
                    .previousRevision("None (New Product Offering)")
                    .newRevision(newRev)
                    .analyzedAt(now)
                    .materialChangesDetected(true)
                    .requiresSeniorReview(true)
                    .contractualDiffs(List.of(
                            PolicyChangeImpactReport.FactDiff.builder()
                                    .fieldName("Product Introduction")
                                    .previousValue("Not in catalog")
                                    .newValue("New filing registered: " + newDraft.getIrdaUin())
                                    .isMaterial(true)
                                    .severity("CRITICAL")
                                    .impactExplanation("Brand new product catalog entry. Requires full verification before entering recommendations.")
                                    .build()
                    ))
                    .eligibilityImpacts(List.of("New product will expand recommendation choices across eligible age bands and zones."))
                    .rankingScoreImpacts(List.of("Will compete against existing flagship policies in ranking engine."))
                    .claimSimulationImpacts(List.of("Full scenario readiness model must be calibrated."))
                    .build();
        }

        List<PolicyChangeImpactReport.FactDiff> diffs = new ArrayList<>();
        List<String> eligibilityImpacts = new ArrayList<>();
        List<String> rankingImpacts = new ArrayList<>();
        List<String> claimImpacts = new ArrayList<>();
        boolean materialChanges = false;
        boolean seniorReviewRequired = false;

        StructuredPolicy.ContractTerms oldTerms = previousActive.getContractTerms();
        StructuredPolicy.ContractTerms newTerms = newDraft.getContractTerms();

        if (oldTerms != null && newTerms != null) {
            // 1. Room Rent Capping Diff
            if (oldTerms.isRoomRentCapped() != newTerms.isRoomRentCapped()
                    || !Objects.equals(oldTerms.getRoomRentLimit(), newTerms.getRoomRentLimit())) {
                materialChanges = true;
                seniorReviewRequired = true;
                String oldRent = oldTerms.isRoomRentCapped() ? "Capped: " + oldTerms.getRoomRentLimit() : "No Room Rent Capping (Single Private AC or above)";
                String newRent = newTerms.isRoomRentCapped() ? "Capped: " + newTerms.getRoomRentLimit() : "No Room Rent Capping (Single Private AC or above)";
                
                String impact = newTerms.isRoomRentCapped()
                        ? "CRITICAL REGRESSION: A room rent cap has been introduced. Claim simulations will now penalize users with proportionate deductions on doctor and surgical charges."
                        : "IMPROVEMENT: Room rent capping has been removed. Broadens claim payout eligibility.";

                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("Room Rent Terms")
                        .previousValue(oldRent)
                        .newValue(newRent)
                        .isMaterial(true)
                        .severity("CRITICAL")
                        .impactExplanation(impact)
                        .previousEvidenceCitation(findCitation(previousActive, "ROOM_RENT"))
                        .newEvidenceCitation(findCitation(newDraft, "ROOM_RENT"))
                        .build());

                claimImpacts.add(impact);
                rankingImpacts.add(newTerms.isRoomRentCapped()
                        ? "Policy ranking score will drop by up to 20 points in Balanced and Comprehensive recommendation profiles due to room rent risk."
                        : "Policy ranking score will increase by 10 points due to unrestricted room rent cover.");
            }

            // 2. Co-payment Diff
            if (Double.compare(oldTerms.getCopayPercentage(), newTerms.getCopayPercentage()) != 0) {
                materialChanges = true;
                seniorReviewRequired = true;
                double oldCopay = oldTerms.getCopayPercentage();
                double newCopay = newTerms.getCopayPercentage();

                String impact = newCopay > oldCopay
                        ? String.format("CRITICAL: Mandatory co-payment increased from %.0f%% to %.0f%%. Directly increases customer out-of-pocket liability on all claims.", oldCopay, newCopay)
                        : String.format("IMPROVEMENT: Mandatory co-payment reduced from %.0f%% to %.0f%%.", oldCopay, newCopay);

                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("Co-payment Percentage")
                        .previousValue(String.format("%.0f%%", oldCopay))
                        .newValue(String.format("%.0f%%", newCopay))
                        .isMaterial(true)
                        .severity("CRITICAL")
                        .impactExplanation(impact)
                        .previousEvidenceCitation(findCitation(previousActive, "COPAY"))
                        .newEvidenceCitation(findCitation(newDraft, "COPAY"))
                        .build());

                claimImpacts.add(impact);
                rankingImpacts.add(String.format("Copay change from %.0f%% to %.0f%% will adjust ranking score by %.0f points.",
                        oldCopay, newCopay, (oldCopay - newCopay) * 1.5));
            }

            // 3. Pre-Existing Disease (PED) Waiting Period Diff
            if (oldTerms.getWaitingPeriodPedMonths() != newTerms.getWaitingPeriodPedMonths()) {
                materialChanges = true;
                int oldPed = oldTerms.getWaitingPeriodPedMonths();
                int newPed = newTerms.getWaitingPeriodPedMonths();

                String impact = newPed > oldPed
                        ? String.format("REGRESSION: Pre-existing disease waiting period lengthened from %d to %d months. Users with diabetes/hypertension will wait longer for coverage.", oldPed, newPed)
                        : String.format("IMPROVEMENT: Pre-existing disease waiting period shortened from %d to %d months (Aligns with IRDAI 2024 3-year PED cap).", oldPed, newPed);

                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("PED Waiting Period")
                        .previousValue(oldPed + " months")
                        .newValue(newPed + " months")
                        .isMaterial(true)
                        .severity(newPed > oldPed ? "CRITICAL" : "MODERATE")
                        .impactExplanation(impact)
                        .previousEvidenceCitation(findCitation(previousActive, "PED"))
                        .newEvidenceCitation(findCitation(newDraft, "PED"))
                        .build());

                claimImpacts.add(impact);
                rankingImpacts.add(String.format("Waiting period altered from %d to %d months. Score for users with pre-existing conditions will change.", oldPed, newPed));
            }

            // 4. Restoration Benefit Diff
            if (!Objects.equals(oldTerms.getRestorationBenefit(), newTerms.getRestorationBenefit())) {
                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("Restoration Benefit")
                        .previousValue(oldTerms.getRestorationBenefit() != null ? oldTerms.getRestorationBenefit() : "Standard")
                        .newValue(newTerms.getRestorationBenefit() != null ? newTerms.getRestorationBenefit() : "Standard")
                        .isMaterial(false)
                        .severity("MODERATE")
                        .impactExplanation("Restoration clause wording revised in contract.")
                        .previousEvidenceCitation(findCitation(previousActive, "RESTORATION"))
                        .newEvidenceCitation(findCitation(newDraft, "RESTORATION"))
                        .build());
            }
        }

        // 5. Eligibility Bounds Diff
        StructuredPolicy.EligibilityRules oldElig = previousActive.getEligibility();
        StructuredPolicy.EligibilityRules newElig = newDraft.getEligibility();
        if (oldElig != null && newElig != null) {
            if (oldElig.getMaxAdultAge() != newElig.getMaxAdultAge()) {
                materialChanges = true;
                String impact = String.format("Maximum adult entry age changed from %d to %d.", oldElig.getMaxAdultAge(), newElig.getMaxAdultAge());
                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("Maximum Entry Age")
                        .previousValue(oldElig.getMaxAdultAge() + " years")
                        .newValue(newElig.getMaxAdultAge() + " years")
                        .isMaterial(true)
                        .severity("MODERATE")
                        .impactExplanation(impact)
                        .build());
                eligibilityImpacts.add(impact);
            }
            if (oldElig.getMinSumInsured() != newElig.getMinSumInsured() || oldElig.getMaxSumInsured() != newElig.getMaxSumInsured()) {
                String impact = String.format("Sum insured range shifted from [₹%d - ₹%d] to [₹%d - ₹%d].",
                        oldElig.getMinSumInsured(), oldElig.getMaxSumInsured(), newElig.getMinSumInsured(), newElig.getMaxSumInsured());
                diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                        .fieldName("Sum Insured Bounds")
                        .previousValue(String.format("₹%d - ₹%d", oldElig.getMinSumInsured(), oldElig.getMaxSumInsured()))
                        .newValue(String.format("₹%d - ₹%d", newElig.getMinSumInsured(), newElig.getMaxSumInsured()))
                        .isMaterial(true)
                        .severity("MODERATE")
                        .impactExplanation(impact)
                        .build());
                eligibilityImpacts.add(impact);
            }
        }

        if (diffs.isEmpty()) {
            diffs.add(PolicyChangeImpactReport.FactDiff.builder()
                    .fieldName("No Material Contract Changes")
                    .previousValue("Identical terms")
                    .newValue("Identical terms")
                    .isMaterial(false)
                    .severity("INFORMATIONAL")
                    .impactExplanation("No contractual differences detected in room limits, copayments, or waiting periods.")
                    .build());
        }

        List<PolicyChangeImpactReport.BenchmarkSimulationResult> benchmarkSimulations =
                runBenchmarkSimulations(previousActive, newDraft);

        return PolicyChangeImpactReport.builder()
                .policyCode(policyCode)
                .productName(productName)
                .provider(provider)
                .previousRevision(prevRev)
                .newRevision(newRev)
                .analyzedAt(now)
                .materialChangesDetected(materialChanges)
                .requiresSeniorReview(seniorReviewRequired)
                .advisoryOnly(true)
                .advisoryDisclaimer("Advisory decision-support only. Empirical score deltas are simulated against benchmark archetypes and do not alter contractual facts.")
                .contractualDiffs(diffs)
                .eligibilityImpacts(eligibilityImpacts)
                .rankingScoreImpacts(rankingImpacts)
                .claimSimulationImpacts(claimImpacts)
                .benchmarkSimulations(benchmarkSimulations)
                .build();
    }

    private List<PolicyChangeImpactReport.BenchmarkSimulationResult> runBenchmarkSimulations(
            StructuredPolicy prev, StructuredPolicy next) {
        List<PolicyChangeImpactReport.BenchmarkSimulationResult> results = new ArrayList<>();
        if (prev == null || next == null) return results;

        double scorePrevYoung = simulateScore(prev, 28, "COMPREHENSIVE_COVER");
        double scoreNextYoung = simulateScore(next, 28, "COMPREHENSIVE_COVER");
        results.add(PolicyChangeImpactReport.BenchmarkSimulationResult.builder()
                .scenarioName("Young Individual (Age 28, Zone 1, Comprehensive Priority)")
                .previousRankScore(round(scorePrevYoung))
                .newDraftRankScore(round(scoreNextYoung))
                .scoreDelta(round(scoreNextYoung - scorePrevYoung))
                .notes(formatDeltaNote(scoreNextYoung - scorePrevYoung))
                .build());

        double scorePrevFam = simulateScore(prev, 45, "BALANCED");
        double scoreNextFam = simulateScore(next, 45, "BALANCED");
        results.add(PolicyChangeImpactReport.BenchmarkSimulationResult.builder()
                .scenarioName("Middle-Age Family (Age 45, Zone 2, Balanced Priority)")
                .previousRankScore(round(scorePrevFam))
                .newDraftRankScore(round(scoreNextFam))
                .scoreDelta(round(scoreNextFam - scorePrevFam))
                .notes(formatDeltaNote(scoreNextFam - scorePrevFam))
                .build());

        double scorePrevSenior = simulateScore(prev, 65, "LOWEST_COST");
        double scoreNextSenior = simulateScore(next, 65, "LOWEST_COST");
        results.add(PolicyChangeImpactReport.BenchmarkSimulationResult.builder()
                .scenarioName("Senior Citizen (Age 65, Zone 1, Low Cost Priority)")
                .previousRankScore(round(scorePrevSenior))
                .newDraftRankScore(round(scoreNextSenior))
                .scoreDelta(round(scoreNextSenior - scorePrevSenior))
                .notes(formatDeltaNote(scoreNextSenior - scorePrevSenior))
                .build());

        return results;
    }

    private double simulateScore(StructuredPolicy policy, int age, String priority) {
        double score = 85.0;
        StructuredPolicy.ContractTerms terms = policy.getContractTerms();
        if (terms != null) {
            if (terms.isRoomRentCapped()) {
                score -= "COMPREHENSIVE_COVER".equals(priority) ? 14.0 : 8.0;
            }
            if (terms.getCopayPercentage() > 0) {
                score -= terms.getCopayPercentage() * ("LOWEST_COST".equals(priority) ? 0.4 : 0.8);
            }
            if (terms.getWaitingPeriodPedMonths() > 36) {
                score -= (terms.getWaitingPeriodPedMonths() - 36) * 0.4;
            } else if (terms.getWaitingPeriodPedMonths() < 36) {
                score += (36 - terms.getWaitingPeriodPedMonths()) * 0.3;
            }
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String formatDeltaNote(double delta) {
        if (Math.abs(delta) < 0.1) return "No perceptible score change under this profile.";
        if (delta > 0) return String.format("+%.1f pts competitive advantage.", delta);
        return String.format("%.1f pts competitive penalty under this profile archetype.", delta);
    }

    private String findCitation(StructuredPolicy policy, String tag) {
        if (policy == null || policy.getDocumentEvidence() == null) return "No citation documented";
        return policy.getDocumentEvidence().stream()
                .filter(e -> tag.equalsIgnoreCase(e.getTag()))
                .map(e -> (e.getSectionReference() != null ? e.getSectionReference() + ": " : "")
                        + (e.getVerbatimText() != null ? "\"" + e.getVerbatimText() + "\"" : ""))
                .findFirst()
                .orElse("No verbatim citation found");
    }
}
