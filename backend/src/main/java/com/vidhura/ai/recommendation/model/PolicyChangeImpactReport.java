package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit and regression impact analysis comparing a newly extracted draft policy revision
 * against the currently approved active revision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyChangeImpactReport {

    private String policyCode;
    private String productName;
    private String provider;
    private String previousRevision;
    private String newRevision;
    private Instant analyzedAt;

    private boolean materialChangesDetected;
    private boolean requiresSeniorReview;

    @Builder.Default
    private List<FactDiff> contractualDiffs = new ArrayList<>();

    @Builder.Default
    private List<String> eligibilityImpacts = new ArrayList<>();

    @Builder.Default
    private List<String> rankingScoreImpacts = new ArrayList<>();

    @Builder.Default
    private boolean advisoryOnly = true;

    @Builder.Default
    private String advisoryDisclaimer = "Advisory decision-support only. Forecasted score deltas are empirical simulation benchmarks and do not replace reviewer verification.";

    @Builder.Default
    private List<BenchmarkSimulationResult> benchmarkSimulations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkSimulationResult {
        private String scenarioName;
        private double previousRankScore;
        private double newDraftRankScore;
        private double scoreDelta;
        private String notes;
    }

    @Builder.Default
    private List<String> claimSimulationImpacts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactDiff {
        private String fieldName; // e.g. "Room Rent Limit", "Co-payment", "PED Waiting Period"
        private String previousValue;
        private String newValue;
        private boolean isMaterial;
        private String severity; // "CRITICAL", "MODERATE", "INFORMATIONAL"
        private String impactExplanation;
        private String previousEvidenceCitation;
        private String newEvidenceCitation;
    }
}
