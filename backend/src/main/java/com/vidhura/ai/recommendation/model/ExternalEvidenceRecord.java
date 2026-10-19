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
        @CompoundIndex(name = "insurer_metric_period_idx", def = "{'insurerCode': 1, 'metricKey': 1, 'reportingPeriod': 1}", unique = true),
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
}
