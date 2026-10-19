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

/**
 * Audit record of a downloaded policy document.
 * Records the source URL, SHA-256 hash, fetch timestamp, file size,
 * and link to the staged draft policy in MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "discovered_documents")
public class DiscoveredDocument {

    @Id
    private String id;

    @Indexed
    private String sourceId;

    private String sourceUrl;
    private String documentName;

    @Indexed
    private String sha256Checksum;

    private long fileSizeBytes;
    private String etag;
    private String contentType;

    @CreatedDate
    private Instant fetchedAt;

    @Builder.Default
    private String extractionStatus = "PENDING"; // PENDING, EXTRACTED_STAGED, SKIPPED_UNCHANGED, FAILED

    @Builder.Default
    private String documentClassification = "POLICY_WORDING";

    private String extractedPolicyId;
    private String irdaUin;
    private String uinRevision;
    private Double confidenceScore;
    private String errorMessage;
}
