package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Model storing generated recommendation outputs answering:
 * 1. Which health insurance policies best match their requirements?
 * 2. What important limitations, exclusions, and potential out-of-pocket costs should they know about?
 * 3. What evidence from the actual policy documents supports each recommendation?
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recommendation_results")
public class RecommendationResult {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String profileId;
    private RecommendationProfile userRequirements;

    // Guidance insights generated for this profile
    private ProfileGuidance guidance;

    // Ranked Policies
    @Builder.Default
    private List<RankedPolicyItem> rankedPolicies = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileGuidance {
        private long recommendedSumInsured;
        private String formattedRecommendedSumInsured;
        private String sumInsuredRationale;
        private String zoneClassification;
        private String medicalInflationInsight;
        private String budgetAdequacyNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedPolicyItem {
        private int rank;
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String planName;
        private String category;
        private String policyWordingDocument;

        // Core Q1 Answer: Match calculations
        private double overallMatchScore; // 0 - 100
        private double costScore;
        private double coverageScore;
        private double outOfPocketProtectionScore;
        private double insurerReliabilityScore;

        private double calculatedAnnualPremium;
        private String formattedAnnualPremium;
        private long selectedSumInsured;
        private String formattedSumInsured;
        private boolean withinBudget;
        private double budgetDifference; // positive = savings, negative = over budget

        @Builder.Default
        private List<String> matchBadges = new ArrayList<>(); // "Best Overall Match", "Lowest Out-of-Pocket", etc.
        @Builder.Default
        private List<String> matchReasons = new ArrayList<>();

        // Core Q2 Answer: Limitations, Exclusions & Out-of-Pocket Risks
        private LimitationAssessment limitations;
        private OutOfPocketRiskAnalysis outOfPocketRisk;
        @Builder.Default
        private List<String> keyExclusions = new ArrayList<>();

        // Core Q3 Answer: Supporting Document Evidence
        @Builder.Default
        private List<StructuredPolicy.PolicyEvidenceItem> supportingEvidence = new ArrayList<>();

        // Insurer Service metrics
        private StructuredPolicy.InsurerMetrics insurerMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitationAssessment {
        private String roomRentCondition;
        private boolean roomRentCapped;
        private boolean proportionateDeductionRisk;
        private String roomRentWarning;

        private double copayPercentage;
        private String copayNote;

        @Builder.Default
        private List<StructuredPolicy.DiseaseSublimit> sublimits = new ArrayList<>();

        private int initialWaitingDays;
        private int specificIllnessWaitingMonths;
        private int pedWaitingMonths;
        private String pedStatusForUser;
        private String restorationSummary;
        private String noClaimBonusSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutOfPocketRiskAnalysis {
        private String riskLevel; // "LOW", "MODERATE", "HIGH"
        private String summary;
        private double estimatedExposurePercentage; // e.g. 5% vs 35%
        private double sampleScenarioBill; // e.g. ₹3,00,000 hospitalization
        private double estimatedCoveredAmount;
        private double estimatedOutofPocketCost;
        @Builder.Default
        private List<String> exposureFactors = new ArrayList<>(); // e.g. "Proportionate room rent deduction", "10% zone copay"
    }
}
