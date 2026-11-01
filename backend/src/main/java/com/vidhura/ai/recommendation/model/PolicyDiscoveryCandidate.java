package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovered Policy Product Candidate in the Ingestion Pipeline.
 *
 * Tracks candidate product identity, UIN, document links, physical verification,
 * extraction quality, and publication gating.
 *
 * Lifecycle Stages:
 * DISCOVERED -> ACQUISITION_PENDING -> ACQUIRED -> EXTRACTED -> VALIDATED -> PENDING_REVIEW -> PUBLISHED_ELIGIBLE (or REJECTED)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_discovery_candidates")
@CompoundIndexes({
        @CompoundIndex(name = "candidate_uin_rev_idx", def = "{'irdaUin': 1, 'uinRevision': 1}"),
        @CompoundIndex(name = "candidate_insurer_stage_idx", def = "{'insurerCode': 1, 'lifecycleStage': 1}")
})
public class PolicyDiscoveryCandidate {

    @Id
    private String id;

    @Indexed
    private String insurerCode; // e.g. "NIC", "BAG", "HDFC_ERGO"

    private String insurerName; // e.g. "National Insurance Co. Ltd."

    private String productName; // e.g. "National Mediclaim Plus"

    @Indexed
    private String normalizedProductCode; // e.g. "NIC_NATIONAL_MEDICLAIM_PLUS"

    @Indexed
    private String irdaUin; // e.g. "NICHLIP21262V022021"

    private String uinRevision; // e.g. "V022021"

    private String variantName; // e.g. "Individual & Floater", "Diamond Plan"

    @Indexed
    private String sourceUrl; // URL where document/link was discovered

    private String finalAcquisitionUrl; // URL from which document was actually fetched

    @Indexed
    private String discoveryRunId; // ID of discovery run that detected this candidate

    private Instant discoveredAt;

    private String documentArtifactId; // ID or filename of physical artifact

    @Indexed
    private String verifiedSha256; // 64-char hex SHA-256 computed from physical bytes

    private Long fileSizeBytes;

    private String mediaType; // "application/pdf", "text/html"

    @Builder.Default
    private String documentAuthenticityStatus = "UNVERIFIED_SOURCE"; // VERIFIED_OFFICIAL_SOURCE, TEST_FIXTURE_OR_SYNTHETIC, UNVERIFIED_SOURCE, SOURCE_UNAVAILABLE, PRODUCT_IDENTITY_MISMATCH, ARTIFACT_INTEGRITY_FAILURE, MANUAL_VERIFICATION_REQUIRED

    @Builder.Default
    private String extractionQualityStatus = "PENDING_EXTRACTION"; // FULL_EXTRACTION, PARTIAL_WITH_UNKNOWN_FIELDS, FAILED_PARSING, PENDING_EXTRACTION

    @Indexed
    @Builder.Default
    private String lifecycleStage = "DISCOVERED";

    // Extraction & Confidence Scoring
    @Builder.Default
    private Double contractConfidenceScore = 0.0;

    @Builder.Default
    private Double pricingConfidenceScore = 0.0;

    @Builder.Default
    private List<String> contractUncertainFields = new ArrayList<>();

    @Builder.Default
    private List<String> pricingUncertainFields = new ArrayList<>();

    @Builder.Default
    private List<String> auditFindings = new ArrayList<>();

    private String rejectionReason;

    private String linkedPolicyId; // Reference to StructuredPolicy once staged/published

    @Builder.Default
    private int retryCount = 0;

    private Instant lastAttemptAt;

    private String lastErrorMessage;

    // Extracted core contract summary (for operator inspection in review queue)
    private String roomRentSummary;
    private String pedWaitingSummary;
    private String copaySummary;
    private String restorationSummary;
    private String noClaimBonusSummary;

    // Human Review Details
    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewerNotes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
