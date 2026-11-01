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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit record of a policy discovery run.
 * Tracks per-source progress, discovered candidates, duplicate detection, and execution lifecycle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policy_discovery_runs")
public class PolicyDiscoveryRun {

    @Id
    private String id;

    @Indexed
    private String triggerType; // "SCHEDULED", "MANUAL_ADMIN", "STARTUP_SYNC"

    @Indexed
    private Instant startedAt;

    private Instant completedAt;

    @Indexed
    @Builder.Default
    private String status = "IN_PROGRESS"; // "IN_PROGRESS", "COMPLETED", "FAILED", "PARTIAL_SUCCESS"

    @Builder.Default
    private int sourcesScanned = 0;

    @Builder.Default
    private int candidatesDiscovered = 0;

    @Builder.Default
    private int candidatesNew = 0;

    @Builder.Default
    private int candidatesDuplicate = 0;

    @Builder.Default
    private int candidatesAcquired = 0;

    @Builder.Default
    private int candidatesPendingReview = 0;

    @Builder.Default
    private int candidatesPublished = 0;

    @Builder.Default
    private int failuresCount = 0;

    @Builder.Default
    private Map<String, String> perSourceSummary = new HashMap<>(); // sourceUrl -> summary

    @Builder.Default
    private List<String> logMessages = new ArrayList<>();

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
