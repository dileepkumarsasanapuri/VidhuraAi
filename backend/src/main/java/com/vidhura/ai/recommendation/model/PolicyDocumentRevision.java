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

/**
 * Tracks historical and current physical document revisions for a policy product.
 * Enables multi-revision tracking without merging or overwriting previous official wordings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_document_revisions")
@CompoundIndexes({
        @CompoundIndex(name = "doc_rev_uin_rev_idx", def = "{'irdaUin': 1, 'uinRevision': 1}"),
        @CompoundIndex(name = "doc_rev_code_sha_idx", def = "{'policyCode': 1, 'verifiedSha256': 1}")
})
public class PolicyDocumentRevision {

    @Id
    private String id;

    @Indexed
    private String irdaUin; // e.g. "HDFHLIP21147V012021"

    private String uinRevision; // e.g. "V012021"

    @Indexed
    private String insurerCode; // e.g. "HDFC_ERGO"

    @Indexed
    private String policyCode; // e.g. "HDFC_OPTIMA_SECURE"

    private String productName;

    private String variantName;

    private String documentArtifactId; // Stored filename e.g. "<sha256>.pdf"

    @Indexed
    private String verifiedSha256; // 64-character SHA-256 integrity checksum

    private Long fileSizeBytes;
    private Integer pageCount;
    @Builder.Default
    private String documentAuthenticityStatus = "VERIFIED_OFFICIAL_SOURCE";

    private String mediaType; // "application/pdf"

    private String effectiveFrom; // e.g. "2021-04-01"

    private String effectiveTo; // e.g. "OPEN" or "2024-03-31"

    private String sourceUrl; // Original source page/link

    private String finalAcquisitionUrl; // Actual downloaded document URL

    private Instant discoveredAt;

    private Instant acquiredAt;

    @Builder.Default
    private String documentClassification = "POLICY_WORDING"; // POLICY_WORDING, CIS, PROSPECTUS

    @Indexed
    @Builder.Default
    private String status = "ACTIVE_REVISION"; // ACTIVE_REVISION, SUPERSEDED, PENDING_VERIFICATION

    private String candidateId; // Link to PolicyDiscoveryCandidate

    // Phase 6.2: Targeted Document Acquisition & Provenance Fields
    @Indexed
    private String registryRecordId; // Link to IrdaiRegistryRecord.id

    private String sourceUin; // Raw registry UIN
    private String extractedDocumentUin; // UIN extracted directly from PDF text layer
    private String storageKey; // Path or key in external document storage
    private String manifestPath; // Path to immutable JSON provenance manifest
    private Boolean hasTextLayer; // Whether PDF has extractable text layer
    private Boolean isEncrypted; // Whether PDF is password protected

    @Indexed
    private String acquisitionRunId; // ID of acquisition run batch

    @Indexed
    @Builder.Default
    private String acquisitionState = "DISCOVERED"; // DISCOVERED, QUEUED, DOWNLOADING, DOWNLOADED, VALIDATION_PENDING, VERIFIED_INTEGRITY, REVIEW_REQUIRED, UNAVAILABLE, RETRY_PENDING, QUARANTINED

    @Builder.Default
    private String integrityStatus = "PENDING"; // PENDING, PASSED, CORRUPT, TRUNCATED, NON_PDF, SIZE_EXCEEDED, QUARANTINED

    @Builder.Default
    private String provenanceStatus = "UNVERIFIED"; // UNVERIFIED, OFFICIAL_PORTAL_VERIFIED, UIN_MATCHED, UIN_MISMATCH_SUSPECT, UNKNOWN_SOURCE

    private String uinMatchStatus; // MATCHED, MISMATCH, UNEXTRACTED_SCANNED, MISSING_REGISTRY_UIN
    private String lastErrorMessage;

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private java.util.List<String> stateHistory = new java.util.ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
