package com.vidhura.ai.recommendation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.HospitalNetworkNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExternalEvidenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestEvidenceRequest {
        @NotBlank(message = "Evidence type is required (e.g. IRDAI_ANNUAL_REPORT, NL_DISCLOSURE)")
        private String evidenceType;

        @NotBlank(message = "Insurer code is required (e.g. HDFC_ERGO, STAR_HEALTH)")
        private String insurerCode;

        private String insurerName;

        @NotBlank(message = "Metric category is required (e.g. SETTLEMENT_RATIO_LESS_3_MONTHS, INCURRED_CLAIM_RATIO)")
        private String metricCategory;

        @NotBlank(message = "Metric key is required")
        private String metricKey;

        @NotBlank(message = "Formatted metric value is required (e.g. '98.40%')")
        private String metricValue;

        @NotNull(message = "Numeric value is required for quantitative ranking")
        private BigDecimal numericValue;

        private String metricUnit;

        @NotBlank(message = "Reporting period is required (e.g. FY2023-24)")
        private String reportingPeriod;

        private LocalDate publicationDate;

        @NotBlank(message = "Source document title is required")
        private String sourceDocumentTitle;

        @NotBlank(message = "Canonical source URL is required")
        private String sourceUrl;

        @NotBlank(message = "Valid 64-character SHA-256 cryptographic hash of source filing is required")
        private String sourceDocumentHash;

        private Integer citationPageNumber;
        private String citationTableReference;
        private String verificationNotes;

        private Integer revision;
        private String revisionType;
        private String previousValue;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private String calculationFormula;
        private String calculationPrecision;
        private boolean isDerived;

        // Phase 3.8 Grievance, Ombudsman & SLA Intelligence fields
        private String businessSegment;
        private String complaintChannel;
        private String reportingPopulation;
        private String ageingBucket;
        private String actualComplianceStatus;
        private LocalDate effectiveDate;
        private Integer slaTargetMinutes;
        private String regulatoryCitation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewAuditEntryDto {
        private String decision;
        private String decisionMode;
        private String reviewerEmail;
        private String reviewerRole;
        private Instant timestamp;
        private String reason;
        private String sourceDocumentHash;
        private int revision;
        private String governanceRuleVersion;
        private String validationRunId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyEvidenceRequest {
        @NotBlank(message = "Verification status is required: VERIFIED_STATUTORY or REJECTED")
        private String verificationStatus;

        private String verificationNotes;
        private String expectedSourceDocumentHash;
        private Integer targetRevision;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRecordDto {
        private String id;
        private String evidenceType;
        private String insurerCode;
        private String insurerName;
        private String metricCategory;
        private String metricKey;
        private String metricValue;
        private BigDecimal numericValue;
        private String metricUnit;
        private String reportingPeriod;
        private LocalDate publicationDate;
        private String sourceDocumentTitle;
        private String sourceUrl;
        private String sourceDocumentHash;
        private Integer citationPageNumber;
        private String citationTableReference;
        private String verificationStatus;
        private String verifiedBy;
        private Instant verifiedAt;
        private String verificationNotes;
        private Instant ingestionTimestamp;

        private int revision;
        private String revisionType;
        private String previousValue;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private String calculationFormula;
        private String calculationPrecision;
        private boolean isDerived;
        private String rejectionReason;

        // Phase 3.8 Grievance, Ombudsman & SLA Intelligence fields
        private String businessSegment;
        private String complaintChannel;
        private String reportingPopulation;
        private String ageingBucket;
        private String actualComplianceStatus;
        private LocalDate effectiveDate;
        private Integer slaTargetMinutes;
        private String regulatoryCitation;
        // Phase 3.9 Automated Validation & Review Prioritization
        private String latestValidationOutcome;
        private Integer latestReviewPriorityScore;
        private String latestPriorityTier;
        private Instant latestValidatedAt;
        private ValidationResultDto latestValidationResult;

        // Phase 5.3 Automated Evidence Acceptance
        private String approvalMode;
        private String acceptanceDecision;
        private String publicationStatus;
        private String sourceProvenanceStatus;
        private String governanceRuleVersion;
        private String acceptanceReason;
        private List<String> passedMandatoryChecks;
        private List<String> failedMandatoryChecks;

        private List<ReviewAuditEntryDto> auditHistory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatorySlaBenchmarkDto {
        private String ruleKey;
        private String ruleTitle;
        private String processingStage;
        private Integer targetTimelineMinutes;
        private String targetTimelineDisplay;
        private String statutoryRuleWording;
        private String regulatoryCitation;
        private LocalDate effectiveDate;
        private String circularReference;
        private String circularTitle;
        private String circularHash;
        private String circularUrl;
        private String actualComplianceStatus;
        private String complianceExplanation;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrievanceChannelSummaryDto {
        private String channel;
        private String businessSegment;
        private String reportingPeriod;
        private BigDecimal received;
        private BigDecimal disposed;
        private BigDecimal pending;
        private BigDecimal disposalRate;
        private BigDecimal ageingUpTo15Days;
        private BigDecimal ageing15To30Days;
        private BigDecimal ageingBeyond30Days;
        private BigDecimal awardsInFavorComplainant;
        private BigDecimal awardAmountLakhs;
        private String sourceTitle;
        private String sourceUrl;
        private String sourceDocumentHash;
        private String citation;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NormalizedGrievanceRateDto {
        private String rateKey;
        private String rateTitle;
        private BigDecimal rate;
        private String rateDisplay;
        private BigDecimal rawNumerator;
        private String numeratorDefinition;
        private BigDecimal rawDenominator;
        private String denominatorDefinition;
        private String formula;
        private String roundingPrecision;
        private boolean isAvailable;
        private String limitationNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsurerGrievanceIntelligenceDto {
        private String insurerCode;
        private String insurerName;
        private String reportingPeriod;
        private GrievanceChannelSummaryDto internalGrievances;
        private GrievanceChannelSummaryDto ombudsmanHealth;
        private GrievanceChannelSummaryDto ombudsmanGeneral;
        @Builder.Default
        private List<NormalizedGrievanceRateDto> normalizedRates = new ArrayList<>();
        @Builder.Default
        private List<RegulatorySlaBenchmarkDto> regulatorySlas = new ArrayList<>();
        private String duplicateCountingWarning;
        private String bimaBharosaContextNote;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsurerPerformanceSummaryDto {
        private String insurerCode;
        private String insurerName;
        private String reportingPeriod;

        // Metric fields extracted from verified statutory filings
        private String incurredClaimRatio;
        private BigDecimal incurredClaimRatioValue;

        private String settlementRatioLessThan3Months;
        private BigDecimal settlementRatioLessThan3MonthsValue;

        private String annualSettlementRatio;
        private BigDecimal annualSettlementRatioValue;

        private String repudiationRatio;
        private BigDecimal repudiationRatioValue;

        private String complaintsPer10kClaims;
        private BigDecimal complaintsPer10kClaimsValue;

        private String solvencyRatio;
        private BigDecimal solvencyRatioValue;

        private String claimTurnaroundDays;
        private BigDecimal claimTurnaroundDaysValue;

        private String overallPerformanceRating; // EXCELLENT, VERY_GOOD, STANDARD, REQUIRES_ATTENTION
        private String statutorySourceTitle;
        private String statutoryDocumentHash;
        private String sourceUrl;
        private String verificationStatus;
        private boolean verifiedStatutory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalNodeDto {
        private String hospitalId;
        private String hospitalName;
        private String registryRohiniCode;
        private String address;
        private String city;
        private String pinCode;
        private Double latitude;
        private Double longitude;
        private String category;
        private boolean cashlessActive;
        private String tier;
        private LocalDate agreementValidUntil;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalNetworkSummaryDto {
        private String insurerCode;
        private String city;
        private String pinCode;
        private int totalNetworkHospitalsInCity;
        private int preferredTier1Count;
        private int secondaryCount;
        private double cashlessAccessibilityScore; // 0 to 100
        private String verificationBadge; // "VERIFIED_HOSPITAL_REGISTRY" or "UNVERIFIED"
        private String networkDisclaimer;

        @Builder.Default
        private List<HospitalNodeDto> hospitals = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestHospitalRequest {
        @NotBlank(message = "Hospital ID is required")
        private String hospitalId;

        @NotBlank(message = "Hospital name is required")
        private String hospitalName;

        @NotBlank(message = "ROHINI code is required")
        private String registryRohiniCode;

        private String address;

        @NotBlank(message = "City is required")
        private String city;

        private String state;

        @NotBlank(message = "Pincode is required")
        private String pinCode;

        private Double latitude;
        private Double longitude;
        private String category;

        @NotNull(message = "Insurer agreement list is required")
        @Builder.Default
        private List<HospitalNetworkNode.CashlessAgreement> cashlessAgreements = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteDetailDto {
        private String quoteId;
        private String policyCode;
        private String uinRevision;
        private String insurerCode;
        private BigDecimal sumInsured;
        private int age;
        private String pinCode;
        private String zone;
        private String familyConstruct;
        private BigDecimal quotedAnnualPremium;
        private BigDecimal quotedBasePremium;
        private BigDecimal quotedGst;
        private String quotingChannel;
        private Instant quoteTimestamp;
        private String quoteReferenceNumber;
        private String sourceDocumentHash;
        private boolean isAuthentic;
        private String verificationStatus;
        private String formattedAnnualPremium;
        private boolean isStale;
    }

    // ==========================================
    // Phase 3.9 Automated Evidence Validation DTOs
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationFindingDto {
        private String checkCategory; // SOURCE_INTEGRITY, CITATION, NUMERICAL_CALCULATION, SEMANTICS_GOVERNANCE, FRESHNESS_REVISION
        private String findingCode;   // DOC_HASH_MISMATCH, CALC_REPRODUCTION_MISMATCH, etc.
        private String severity;      // BLOCKER, ERROR, WARNING, INFO
        private String message;
        private String expectedValue;
        private String actualValue;
        private String affectedDocument;
        private String citation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResultDto {
        private String id;
        private String evidenceRecordId;
        private int recordRevision;
        private String insurerCode;
        private String metricKey;
        private String metricCategory;
        private Instant validatedAt;
        private String overallOutcome; // VALIDATION_PASSED, VALIDATION_PASSED_WITH_WARNINGS, VALIDATION_FAILED, VALIDATION_NOT_RUN
        private int reviewPriorityScore;
        private String priorityTier; // CRITICAL, HIGH, MEDIUM, LOW
        private List<ValidationFindingDto> findings;
        private String validatorVersion;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewQueueItemDto {
        private String evidenceRecordId;
        private int recordRevision;
        private String insurerCode;
        private String insurerName;
        private String metricKey;
        private String metricCategory;
        private String metricValue;
        private String verificationStatus;
        private String validationOutcome;
        private int reviewPriorityScore;
        private String priorityTier;
        private boolean materialImpact;
        private boolean isDerived;
        private String sourceDocumentTitle;
        private String sourceDocumentHash;
        private String citation;
        private Instant validatedAt;
        private int totalFindings;
        private int blockerCount;
        private int errorCount;
        private int warningCount;
        private List<ValidationFindingDto> topFindings;

        // Phase 5.3 Automated Acceptance
        private String approvalMode;
        private String acceptanceDecision;
        private String publicationStatus;
        private String governanceRuleVersion;
        private String acceptanceReason;
        private List<String> passedMandatoryChecks;
        private List<String> failedMandatoryChecks;

        // Phase 5.4 Source Acquisition & Freshness
        private boolean artifactBacked;
        private String artifactStoragePath;
        private String acquiredMediaType;
        private Long acquiredByteSize;
        private String acquiredSha256;
        private Instant lastAcquiredAt;
        private Instant nextFreshnessCheckDue;
        private String freshnessStatus;
        private String finalSourceUrl;
        private String contentValidationStatus;
        private String contentValidationNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FreshnessSummaryDto {
        private int totalRecords;
        private int freshCount;
        private int refreshDueCount;
        private int staleRevalidationCount;
        private int acquisitionFailedCount;
        private int unacquiredCount;
        private Instant evaluatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchValidationSummaryDto {
        private int totalRecordsScanned;
        private int passedCount;
        private int warningsCount;
        private int failedCount;
        private int criticalPriorityCount;
        private int highPriorityCount;
        private int mediumPriorityCount;
        private int lowPriorityCount;
        private Instant batchTimestamp;
    }
}
