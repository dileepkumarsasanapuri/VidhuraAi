package com.vidhura.ai.recommendation.model;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
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

    private long catalogVersion;

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
        private String coverageAdvisory;
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
        private boolean coverageShortfall;
        private long requestedSumInsured;
        private long coverageShortfallAmount;

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

        // Canonical verification and provenance metadata
        private String verificationStatus; // "VERIFIED_CONTRACTUAL_SPECIFICATION"
        private String uinRevision; // e.g. "V012021"
        private String regulatoryFilingSource; // "IRDAI Health Product Registry"
        private String officialDocumentUrl;
        private String lastAuditedDate;

        // Version & Scoring Provenance Traceability
        private String policySnapshotVersion; // e.g. "2024-Q1" or "v2024.1"
        private String scoringEngineVersion; // e.g. "2.0-IRDAI-MULTI-POLICY"
        private Instant evaluationTimestamp;
        private long catalogVersion;

        // Explicit Data Classification (Verified Fact vs Modeled Estimate vs Regulatory Metric)
        private DataClassification dataClassification;

        // Complete Audit Reproducibility Snapshot (Historical Calculation Replay)
        private ReproducibilitySnapshot reproducibilitySnapshot;

        // The 3 Core Questions Answers
        private ThreeQuestionAnswers threeQuestionAnswers;

        // Phase 3 Intelligence & Deterministic Claim Simulation Integration
        private PersonalizedClaimSimulationSummary personalizedClaimSimulation;
        private boolean authenticAcquiredDocument;
        private boolean syntheticFixture;
        private String documentChecksum;
        private String reviewerApprovalStatus;
        private String reviewerName;
        @Builder.Default
        private List<String> missingEvidenceWarnings = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataClassification {
        @Builder.Default
        private String contractualFactsStatus = "VERIFIED_CONTRACTUAL_SPECIFICATION";
        @Builder.Default
        private String pricingClassification = "MODELED_ACTUARIAL_ESTIMATE";
        @Builder.Default
        private boolean isBindingQuote = false;
        @Builder.Default
        private String pricingDisclaimer = "Indicative non-binding premium estimate based on policy age-band rate tables and family floater loadings. Excludes individual medical underwriting loads, GST (18%), tobacco use, and optional riders. Final binding quote is determined solely by the insurer's underwriting decision.";
        @Builder.Default
        private String billSimulationClassification = "HYPOTHETICAL_SCENARIO_ESTIMATE";
        @Builder.Default
        private String billSimulationAssumptions = "Simulated on a hypothetical ₹3,00,000 hospitalization bill evaluating policy-specific deductible, co-payment, and proportionate room rent deductions.";
        @Builder.Default
        private String regulatoryMetricsClassification = "DATED_REGULATORY_DISCLOSURE";
        @Builder.Default
        private String regulatoryPeriod = "IRDAI Annual Report FY 2022-23";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreeQuestionAnswers {
        private String whyRecommended;
        @Builder.Default
        private List<String> verifiedFactsSupporting = new ArrayList<>();
        @Builder.Default
        private List<String> estimatesAndAssumptions = new ArrayList<>();
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
        private String coverageShortfallWarning;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalizedClaimSimulationSummary {
        private String scenarioDiagnosis;
        private double submittedBill;
        private double insurerPayment;
        private double patientLiability;
        private double outOfPocketPercentage;
        private boolean reconciled;
        private String calculationStatus;
        @Builder.Default
        private List<ClaimSimulationDto.DeductionItemDto> deductionBreakdown = new ArrayList<>();
        private String roomRentImpactText;
        private String proportionateDeductionImpactText;
        private String copayImpactText;
        private String actionableComparisonInsight;
    }

    // =========================================================================
    // REPRODUCIBILITY & CALCULATION AUDIT REPLAY SNAPSHOTS
    // =========================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReproducibilitySnapshot {
        private String policySnapshotVersion;
        private String scoringEngineVersion;
        private Instant evaluationTimestamp;
        private long catalogVersion;
        private ContractTermsSnapshot contractTermsSnapshot;
        private PricingModelSnapshot pricingModelSnapshot;
        private InsurerMetricsSnapshot insurerMetricsSnapshot;
        private ScoringBreakdownSnapshot scoringBreakdown;
        private UserRequirementsSnapshot inputRequirementsSnapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractTermsSnapshot {
        private String roomRentLimit;
        private boolean roomRentCapped;
        private double copayPercentage;
        private int waitingPeriodPedMonths;
        private int waitingPeriodInitialDays;
        private int waitingPeriodSpecificMonths;
        private double deductibleAmount;
        private boolean restorationAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingModelSnapshot {
        private long selectedSumInsured;
        private double calculatedAnnualPremium;
        private String pricingZone;
        private String pricingStatus;
        private String pricingClassification;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsurerMetricsSnapshot {
        private double claimSettlementRatio;
        private double incurredClaimsRatio;
        private int cashlessHospitalsCount;
        private String reportingPeriod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringBreakdownSnapshot {
        private double costWeight;
        private double coverageWeight;
        private double oopWeight;
        private double reliabilityWeight;
        private double costScore;
        private double coverageScore;
        private double oopScore;
        private double reliabilityScore;
        private double overallScore;
        private String primaryPriority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRequirementsSnapshot {
        private int primaryAge;
        private String insuranceType;
        private String pricingZone;
        private double annualBudget;
        private String budgetType;
        private int declaredConditionsCount;
        private int totalInsuredMembers;
    }
}
