package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Global catalog metadata tracking atomic version sequences, invalidation timestamps,
 * and multi-instance consistency boundaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "catalog_metadata")
public class CatalogMetadata {

    public static final String GLOBAL_ID = "GLOBAL_CATALOG_METADATA";

    @Id
    @Builder.Default
    private String id = GLOBAL_ID;

    @Builder.Default
    private long version = 1L;

    private Instant lastModifiedDate;
    private int activePoliciesCount;
    private String lastAction; // "INITIALIZED", "PUBLISHED", "DEACTIVATED", "FLAGGED"
    private String lastModifiedBy;

    @Builder.Default
    private java.util.List<String> revokedPolicyCodes = new java.util.ArrayList<>();

    @Builder.Default
    private boolean bootstrapCompleted = false;

    @Builder.Default
    private String initializationStatus = "UNINITIALIZED"; // "UNINITIALIZED", "BOOTSTRAPPED", "CLEARED_RECOVERY_REQUIRED"

    private Instant bootstrapTimestamp;
}
