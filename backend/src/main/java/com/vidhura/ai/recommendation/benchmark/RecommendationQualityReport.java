package com.vidhura.ai.recommendation.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Recommendation Quality Evaluation Report.
 *
 * Measures recommendation engine accuracy, constraint compliance,
 * ranking agreement, and claim mathematical precision against a domain gold standard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationQualityReport {

    private Instant evaluatedAt;
    private String engineVersion;
    private int catalogPolicyCount;
    private int totalBenchmarkScenarios;
    private int passedScenarios;
    private double overallQualityScore; // 0 - 100%
    private double mandatoryConstraintViolationRate; // Must be 0.0%
    private double rankingAgreementRate; // >= 85%
    private double claimMathAccuracyRate; // 100%
    private double citationCoverageRate; // >= 90%
    private boolean regressionPassed;

    @Builder.Default
    private List<ScenarioEvaluationResult> scenarioResults = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioEvaluationResult {
        private String scenarioId;
        private String scenarioName;
        private String category;
        private boolean passed;
        private int eligiblePoliciesFound;
        private String topRankedPolicy;
        private String expectedTopPolicy;
        private boolean topMatchAligned;
        private boolean mandatoryConstraintsHonored;
        private boolean claimMathAccurate;
        private double citationIntegrityScore;
        private double executionTimeMs;
        private String documentedGroundTruth;
        private String independentActuarialBasis;
        private String auditNotes;
    }
}
