package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * MongoDB document storing verified or staged external real-world evidence.
 * Examples include IRDAI Annual Reports, NL-Form Public Disclosures, GIPSA preferred provider
 * agreements, regulatory orders, and ombudsman/consumer forum grievance ratios.
 *
 * Strict Provenance Rule:
 * Every statutory record requires a verified cryptographic SHA-256 hash of the original filing PDF/data,
 * precise page/table citation, and a human reviewer gate before it can be marked VERIFIED_STATUTORY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "external_evidence_records")
@CompoundIndexes({
        @CompoundIndex(name = "insurer_metric_period_rev_idx", def = "{'insurerCode': 1, 'metricKey': 1, 'reportingPeriod': 1, 'revision': 1}", unique = true),
        @CompoundIndex(name = "insurer_period_idx", def = "{'insurerCode': 1, 'reportingPeriod': 1}")
})
public class ExternalEvidenceRecord {

    @Id
    private String id;

    /**
     * Category of authoritative external source:
     * IRDAI_ANNUAL_REPORT, NL_DISCLOSURE, HOSPITAL_NETWORK, REGULATORY_ORDER,
     * CONSUMER_FORUM, GIPSA_REGISTRY, PUBLIC_DISCLOSURE, SYNTHETIC_BENCHMARK
     */
    @Indexed
    private String evidenceType;

    /**
     * Standard insurer identifier (e.g., HDFC_ERGO, STAR_HEALTH, CARE, ICICI_LOMBARD, NIVA_BUPA)
     */
    @Indexed
    private String insurerCode;

    private String insurerName;

    /**
     * Metric category:
     * INCURRED_CLAIM_RATIO, SETTLEMENT_RATIO_LESS_3_MONTHS, SETTLEMENT_RATIO_ANNUAL,
     * REPUDIATION_RATIO, COMPLAINTS_PER_10K_CLAIMS, SOLVENCY_RATIO,
     * CASHLESS_HOSPITAL_COUNT, CLAIM_TURNAROUND_DAYS, OMBUDSMAN_DISPOSAL_RATE
     */
    @Indexed
    private String metricCategory;

    /**
     * Normalized key identifier for the metric (e.g. "claim_settlement_ratio_3m")
     */
    @Indexed
    private String metricKey;

    /**
     * Formatted human-readable string representation (e.g. "98.40%", "14.2 days", "2.1 per 10k")
     */
    private String metricValue;

    /**
     * Exact decimal numeric value for sorting, percentile calculations, and constraint evaluations
     */
    private BigDecimal numericValue;

    /**
     * Unit of metric (PERCENTAGE, DAYS, COUNT, RATIO, INR_LAKHS)
     */
    private String metricUnit;

    /**
     * Audited reporting period (e.g. "FY2023-24", "Q3-FY2024-25")
     */
    @Indexed
    private String reportingPeriod;

    private String financialYear;

    private LocalDate publicationDate;

    /**
     * Official document title (e.g. "IRDAI Annual Report 2023-24 - Handbook on Indian Insurance Statistics")
     */
    private String sourceDocumentTitle;

    /**
     * Canonical source URL where document can be independently inspected
     */
    private String sourceUrl;

    /**
     * Cryptographic SHA-256 hash of the exact source filing PDF/data artifact
     */
    @Indexed
    private String sourceDocumentHash;

    private Integer citationPageNumber;

    private String citationTableReference;

    @Builder.Default
    private Instant ingestionTimestamp = Instant.now();

    /**
     * Verification status:
     * VERIFIED_STATUTORY: Human compliance officer or cryptographic statutory audit verified
     * UNVERIFIED_PENDING_REVIEW: Newly ingested; not yet confirmed
     * REJECTED: Failed provenance or checksum audit
     * SYNTHETIC_BENCHMARK: Explicitly simulated test data
     */
    @Indexed
    @Builder.Default
    private String verificationStatus = "UNVERIFIED_PENDING_REVIEW";

    private String verifiedBy;

    private Instant verifiedAt;

    private String verificationNotes;

    @Builder.Default
    private boolean materialImpact = true;

    @Builder.Default
    private int revision = 1;

    private String revisionType; // HISTORICAL_ESTIMATE, STAGED_CORRECTION, DERIVED_ACTUARIAL_CALCULATION, SOURCE_VERIFIED

    private String previousValue;

    private BigDecimal numerator;

    private BigDecimal denominator;

    private String calculationFormula;

    private String calculationPrecision;

    @Builder.Default
    private boolean isDerived = false;

    private String rejectionReason;

    // Phase 3.8 Grievance, Ombudsman & SLA Intelligence fields
    private String businessSegment; // HEALTH, GENERAL_ALL_LINES, LIFE
    private String complaintChannel; // INSURER_INTERNAL, BIMA_BHAROSA_IRDAI, INSURANCE_OMBUDSMAN, REGULATORY_BENCHMARK
    private String reportingPopulation; // TOTAL_ACTIVE_POLICIES, CLAIMS_REGISTERED, ALL_CUSTOMERS
    private String ageingBucket; // UP_TO_15_DAYS, 15_TO_30_DAYS, 30_TO_90_DAYS, BEYOND_90_DAYS
    private String actualComplianceStatus; // UNAVAILABLE, MEASURED_COMPLIANT, MEASURED_NON_COMPLIANT
    private LocalDate effectiveDate;
    private Integer slaTargetMinutes;
    private String regulatoryCitation;

    // Phase 3.9 Automated Validation & Review Prioritization
    @Builder.Default
    private String latestValidationOutcome = "VALIDATION_NOT_RUN"; // VALIDATION_PASSED, VALIDATION_PASSED_WITH_WARNINGS, VALIDATION_FAILED, VALIDATION_NOT_RUN
    private Integer latestReviewPriorityScore;
    private String latestPriorityTier; // CRITICAL, HIGH, MEDIUM, LOW
    private Instant latestValidatedAt;

    // Phase 5.3 Policy-Governed Automated Acceptance States
    /**
     * Approval mode:
     * AUTO_ACCEPTED: Rules engine automatically accepted based on versioned governance policy
     * MANUAL_REVIEWER: Explicitly approved by authenticated compliance reviewer
     * PENDING_REVIEW: Disqualified from auto-acceptance; routed to human review queue
     * REJECTED: Failed mandatory integrity or provenance checks
     * REVALIDATION_REQUIRED: Governing rules or source document changed; re-check needed
     */
    @Indexed
    @Builder.Default
    private String approvalMode = "PENDING_REVIEW";

    /**
     * Acceptance decision: ACCEPTED, PENDING, REJECTED, QUARANTINED
     */
    @Indexed
    @Builder.Default
    private String acceptanceDecision = "PENDING";

    /**
     * Downstream Recommendation & Public Availability Status:
     * PUBLISHED_ELIGIBLE: Can be used in recommendation scoring & comparison displays
     * STAGED_INTERNAL: Visible in admin review queue only; withheld from recommendations
     * QUARANTINED: Integrity failure; strictly quarantined
     * INELIGIBLE: Disqualified from publication
     */
    @Indexed
    @Builder.Default
    private String publicationStatus = "STAGED_INTERNAL";

    /**
     * Source authenticity status:
     * OFFICIAL_STATUTORY_DISCLOSURE, CATALOG_REFERENCE, UNVERIFIED_SOURCE, SYNTHETIC_BENCHMARK
     */
    @Builder.Default
    private String sourceProvenanceStatus = "OFFICIAL_STATUTORY_DISCLOSURE";

    /**
     * Version of governance policy applied (e.g. "2026.1-phase5.3")
     */
    private String governanceRuleVersion;

    /**
     * Detailed reason for auto-acceptance or routing to review
     */
    private String acceptanceReason;

    @Builder.Default
    private List<String> passedMandatoryChecks = new java.util.ArrayList<>();

    @Builder.Default
    private List<String> failedMandatoryChecks = new java.util.ArrayList<>();

    // Phase 5.4 Official Source Acquisition & Freshness Fields
    private String artifactStoragePath;
    private String acquiredMediaType;
    private Long acquiredByteSize;
    private String acquiredSha256;
    private Instant lastAcquiredAt;
    private Instant nextFreshnessCheckDue;
    @Builder.Default
    private String freshnessStatus = "UNACQUIRED"; // FRESH, REFRESH_DUE, STALE_REVALIDATION_REQUIRED, ACQUISITION_FAILED, UNACQUIRED
    private String finalSourceUrl;
    @Builder.Default
    private boolean artifactBacked = false;
    @Builder.Default
    private String contentValidationStatus = "CITATION_UNVERIFIED"; // CONTENT_VERIFIED, CITATION_UNVERIFIED, CONTENT_MISMATCH
    private String contentValidationNotes;

    @Builder.Default
    private java.util.List<ReviewAuditEntry> auditHistory = new java.util.ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewAuditEntry {
        private String decision; // APPROVED, REJECTED, STAGED, AUTO_ACCEPTED
        private String decisionMode; // AUTO_ACCEPTED, MANUAL_REVIEWER
        private String reviewerEmail;
        private String reviewerRole;
        private Instant timestamp;
        private String reason;
        private String sourceDocumentHash;
        private int revision;
        private String governanceRuleVersion;
        private String validationRunId;
    }
}
