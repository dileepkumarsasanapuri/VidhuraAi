package com.vidhura.ai.recommendation.dto;

import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete recommendation response returned by the Spring Boot REST API.
 * Explicitly structures answers to:
 * 1. Which health insurance policies best match their requirements?
 * 2. What important limitations, exclusions, and potential out-of-pocket costs should they know about?
 * 3. What evidence from the actual policy documents supports each recommendation?
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponseDto {

    private String recommendationId;
    private String profileId;
    private Instant generatedAt;

    // Evaluated profile summary
    private ProfileSummary profileSummary;

    // Sum Insured & Budget Guidance
    private RecommendationResult.ProfileGuidance guidance;

    // Question 1: Best Matching Ranked Policies
    @Builder.Default
    private List<RankedPolicyCard> rankedPolicies = new ArrayList<>();

    // Count of total verified policies evaluated in catalog
    private int totalPoliciesEvaluated;
    private int eligiblePoliciesCount;
    private long catalogVersion;
    @Builder.Default
    private String catalogStatus = "CURRENT"; // "CURRENT", "STALE_WARM_CACHE", "METADATA_UNREACHABLE_FROZEN", "DEGRADED_PARTITION_RECENT", "PARTITION_STALENESS_EXCEEDED_HALTED"
    private boolean isStale;
    private boolean isHalted;
    private String catalogFreshnessWarning;
    private String coverageAdvisory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileSummary {
        private int primaryAge;
        private String insuranceType;
        private int totalInsuredMembers;
        private String cityOrPincode;
        private String pricingZone;
        private double annualBudget;
        private String formattedBudget;
        private String budgetType;
        private long selectedSumInsured;
        private String formattedSumInsured;
        private String primaryPriorityLabel;
        @Builder.Default
        private List<String> declaredConditions = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedPolicyCard {
        // Basic Info
        private int rank;
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String planName;
        private String category;
        private String policyWordingDocument;

        // Q1 Details: Scoring & Pricing
        private double overallMatchScore; // 0 - 100
        private double costScore;
        private double coverageScore;
        private double outOfPocketProtectionScore;
        private double insurerReliabilityScore;

        private double calculatedAnnualPremium;
        private String formattedAnnualPremium;
        private long sumInsured;
        private String formattedSumInsured;
        private boolean withinBudget;
        private double budgetDifference;
        private String budgetComparisonText;
        private boolean coverageShortfall;
        private long requestedSumInsured;
        private long coverageShortfallAmount;

        @Builder.Default
        private List<String> matchBadges = new ArrayList<>();
        @Builder.Default
        private List<String> matchReasons = new ArrayList<>();

        // Q2 Details: Limitations, Exclusions & Out-of-Pocket Risk
        private RecommendationResult.LimitationAssessment limitations;
        private RecommendationResult.OutOfPocketRiskAnalysis outOfPocketRisk;
        @Builder.Default
        private List<String> keyExclusions = new ArrayList<>();

        // Q3 Details: Verifiable Evidence from Official Policy Wordings
        @Builder.Default
        private List<StructuredPolicy.PolicyEvidenceItem> supportingEvidence = new ArrayList<>();

        // Insurer Performance Metrics
        private StructuredPolicy.InsurerMetrics insurerMetrics;

        // Canonical verification and provenance metadata
        private String verificationStatus; // "VERIFIED_CONTRACTUAL_SPECIFICATION"
        private String uinRevision; // e.g. "V012021"
        private String regulatoryFilingSource; // "IRDAI Health Product Registry"
        private String officialDocumentUrl;
        private String lastAuditedDate;

        // Version & Scoring Provenance Traceability
        private String policySnapshotVersion;
        private String scoringEngineVersion;
        private Instant evaluationTimestamp;

        // Explicit Data Classification (Verified Fact vs Modeled Estimate vs Dated Regulatory Metric)
        private RecommendationResult.DataClassification dataClassification;

        // The 3 Core Questions Answers
        private RecommendationResult.ThreeQuestionAnswers threeQuestionAnswers;

        // Reproducibility Audit Replay Snapshot
        private RecommendationResult.ReproducibilitySnapshot reproducibilitySnapshot;

        // Phase 3 Intelligence & Deterministic Claim Simulation Integration
        private RecommendationResult.PersonalizedClaimSimulationSummary personalizedClaimSimulation;
        private boolean authenticAcquiredDocument;
        private boolean syntheticFixture;
        private String documentChecksum;
        private String reviewerApprovalStatus;
        private String reviewerName;
        @Builder.Default
        private List<String> missingEvidenceWarnings = new ArrayList<>();
    }
}
