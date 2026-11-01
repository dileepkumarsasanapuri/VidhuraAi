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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 6.1: IRDAI Health Insurance Products Registry Inventory Record.
 *
 * Logically and structurally separated from consumer-recommendation StructuredPolicy records.
 * An IRDAI registry record tracks official portal metadata and provenance, and is NOT
 * automatically eligible for consumer recommendation without formal physical acquisition,
 * clause extraction, and human review gating.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "irdai_registry_records")
@CompoundIndexes({
        @CompoundIndex(name = "irdai_reg_uin_idx", def = "{'normalizedUin': 1, 'duplicateStatus': 1}"),
        @CompoundIndex(name = "irdai_reg_ins_cat_idx", def = "{'normalizedInsurerCode': 1, 'classificationStatus': 1}"),
        @CompoundIndex(name = "irdai_reg_fy_arch_idx", def = "{'financialYear': 1, 'archived': 1}")
})
public class IrdaiRegistryRecord {

    @Id
    private String id; // Stable deterministic ID

    @Indexed
    private String sourceUin; // Exact raw UIN as published on portal

    @Indexed
    private String normalizedUin; // Clean uppercase UIN

    @Indexed
    private String insurerName; // Exact raw insurer name from portal

    @Indexed
    private String normalizedInsurerCode; // Normalized insurer slug or abbreviation

    @Indexed
    private String productName; // Exact raw product name from portal

    @Indexed
    private String financialYear; // e.g. "2022-2023", "2013-2014"

    private String approvalDate; // Exact raw approval date e.g. "06-05-2022"

    private String rawProductType; // Raw product type e.g. "Individual", "Group", "Revision"

    private String normalizedCategory; // Normalized category from portal

    private String archiveStatus; // "Non-Archived", "Archived"

    @Indexed
    @Builder.Default
    private boolean archived = false;

    private String documentDownloadUrl; // Direct portal document download URL
    private String documentFilename; // Filename from portal link text
    private String advertisedDocumentSize; // Advertised size e.g. "1.3 MB"

    private String sourceListingUrl; // The portal page URL where this record was observed
    private Integer sourcePage; // Portal page number (1-based)
    private Integer sourceRowIndex; // Row index on source page (1-based)

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    @Indexed
    private String lastInventoryRunId;

    @Indexed
    @Builder.Default
    private String classificationStatus = "UNKNOWN_UNCLASSIFIED";
    // INDIVIDUAL, FAMILY_FLOATER, GROUP, PERSONAL_ACCIDENT, TRAVEL, BENEFIT_BASED, RETAIL, RIDER_ADDON, ARCHIVED, UNKNOWN_UNCLASSIFIED

    @Indexed
    @Builder.Default
    private String duplicateStatus = "UNIQUE"; // UNIQUE, DUPLICATE_IDENTICAL, CONFLICT_DETECTED

    // Phase 6.2 Document Acquisition Linkage
    @Indexed
    private String documentRevisionId; // Link to PolicyDocumentRevision.id

    @Indexed
    @Builder.Default
    private String acquisitionState = "DISCOVERED"; // DISCOVERED, QUEUED, DOWNLOADING, DOWNLOADED, VALIDATION_PENDING, VERIFIED_INTEGRITY, REVIEW_REQUIRED, UNAVAILABLE, RETRY_PENDING, QUARANTINED

    @Indexed
    private String verifiedSha256;

    private String extractedDocumentUin;
    private String uinMatchStatus; // MATCHED, MISMATCH, UNEXTRACTED_SCANNED, MISSING_REGISTRY_UIN

    @Builder.Default
    private int occurrenceCount = 1;

    @Builder.Default
    private List<String> conflictDetails = new ArrayList<>();

    @Builder.Default
    private Map<String, String> rawSourcePayload = new HashMap<>();

    @Builder.Default
    private Map<String, String> documentMetadata = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
