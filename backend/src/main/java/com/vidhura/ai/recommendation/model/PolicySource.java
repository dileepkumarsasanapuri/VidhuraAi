package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Monitored official policy source registry.
 * Represents an official IRDAI or insurer document endpoint (PDF or page)
 * tracked by the automated background discovery pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_sources")
public class PolicySource {

    @Id
    private String id;

    private String sourceName; // e.g. "HDFC ERGO Optima Secure Official Wordings"
    private String provider; // e.g. "HDFC ERGO General Insurance Co. Ltd."
    private String canonicalProductName; // e.g. "Optima Secure"

    @Indexed
    private String irdaUin; // e.g. "HDFHLIP21147V012021"

    private String expectedRevision; // e.g. "V012021"
    private String documentType; // "POLICY_WORDING", "CUSTOMER_INFORMATION_SHEET", "PROSPECTUS", "RATE_TABLE"
    private String sourceAuthority; // "OFFICIAL_INSURER_WEBSITE", "IRDAI_PORTAL", "PUBLIC_DISCLOSURE"

    @Indexed
    private String sourceUrl; // Official HTTPS document or landing page URL

    @Builder.Default
    private boolean active = true;

    // HTTP Cache & ETag Headers for Conditional Fetching (avoid redundant downloads)
    private String lastEtag;
    private String lastModifiedHeader;
    private String lastSha256Checksum;
    private Long lastFileSizeBytes;

    // Crawl, Freshness & Observability Tracking
    private Instant lastCheckedAt;
    private Instant lastDiscoveryAttemptAt;
    private Instant lastFetchedAt;
    private Instant lastSuccessfulCrawlAt;
    private Instant lastSuccessfulDiscoveryAt;
    private Instant lastSuccessfulExtractionAt;
    private Integer lastHttpStatusCode;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, SYNCED, PENDING_CRAWL, FAILED, FAILED_BACKOFF, DISABLED

    private String lastErrorMessage;
    private String lastErrorCategory; // BOT_PROTECTION_403, NOT_FOUND_404, SSRF_BLOCKED, INVALID_DOCUMENT_SIGNATURE, UNAPPROVED_DOMAIN, EXTRACTION_FAILED, TIMEOUT_ERROR, HTTP_ERROR

    @Builder.Default
    private int consecutiveFailures = 0;

    @Builder.Default
    private int backoffHours = 0;

    private Instant nextRetryEligibleAt;

    @Builder.Default
    private int candidateCount = 0;

    @Builder.Default
    private int contractualWordingsCount = 0;

    @Builder.Default
    private int cataloguesCount = 0;

    private String discoveryMode; // LIVE_DISCOVERY, CURATED_FALLBACK, MANUAL_CURATED_OFFICIAL
    private String documentClassification; // POLICY_WORDING, PRODUCT_CATALOGUE, CUSTOMER_INFORMATION_SHEET, REGULATORY_FILING, UNKNOWN

    @Builder.Default
    private boolean isContractualWording = false;

    @Builder.Default
    private boolean ocrApplied = false;

    private Double ocrConfidenceScore;

    @Builder.Default
    private int pollFrequencyHours = 24;

    private String linkedDraftPolicyId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
