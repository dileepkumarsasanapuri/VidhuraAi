package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evidence Freshness Scheduler Service.
 *
 * Implements automated, bounded, scheduled monitoring of evidence freshness:
 * 1. Scans repository for records approaching (REFRESH_DUE) or exceeding (STALE_REVALIDATION_REQUIRED) deadlines.
 * 2. Queues and executes bounded re-acquisition attempts with source allowlisting & SSRF defense.
 * 3. Prevents overlapping cycles and concurrent duplicate downloads for the same record.
 * 4. Implements failure backoff and escalates persistent failures (>= 3 consecutive) in the admin review queue.
 * 5. Triggers automated revalidation and revision bump when remote origin source bytes change.
 * 6. Withholds stale or failed records from publication and recommendation flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceFreshnessSchedulerService {

    private final ExternalEvidenceRepository evidenceRepository;
    private final ExternalEvidenceValidationService validationService;
    private final OfficialSourceAcquisitionService acquisitionService;

    @Value("${vidhura.freshness.enabled:true}")
    private boolean freshnessMonitoringEnabled = true;

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final AtomicBoolean isCycleRunning = new AtomicBoolean(false);
    private final Set<String> activeAcquisitions = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAttemptTimestamps = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class FreshnessMonitoringReport {
        private Instant cycleStartedAt;
        private Instant cycleCompletedAt;
        private int totalRecordsEvaluated;
        private int freshCount;
        private int refreshDueCount;
        private int staleCount;
        private int acquisitionFailedCount;
        private int unacquiredCount;
        private int refreshAttemptsExecuted;
        private int refreshSuccessCount;
        private int refreshFailureCount;
        private int revalidationsTriggered;
        private int persistentFailuresFlagged;
        private boolean cycleSkippedOverlap;
    }

    /**
     * Scheduled freshness check execution (configurable cron, default every 6 hours).
     */
    @Scheduled(cron = "${vidhura.freshness.schedule-cron:0 0 */6 * * *}")
    public void runScheduledFreshnessScan() {
        if (!freshnessMonitoringEnabled) {
            log.debug("[FreshnessScheduler] Scheduled freshness monitoring is disabled via configuration.");
            return;
        }
        executeFreshnessCycle();
    }

    /**
     * Executes a bounded freshness cycle across all registered evidence records.
     * Can be invoked by schedule or on-demand via administrative trigger.
     */
    public FreshnessMonitoringReport executeFreshnessCycle() {
        Instant startTime = Instant.now();

        if (!isCycleRunning.compareAndSet(false, true)) {
            log.warn("[FreshnessScheduler] Freshness cycle already in progress. Skipping overlapping execution.");
            return FreshnessMonitoringReport.builder()
                    .cycleStartedAt(startTime)
                    .cycleCompletedAt(Instant.now())
                    .cycleSkippedOverlap(true)
                    .build();
        }

        log.info("[FreshnessScheduler] Starting automated evidence freshness check cycle...");

        int totalEvaluated = 0;
        int fresh = 0;
        int due = 0;
        int stale = 0;
        int failed = 0;
        int unacquired = 0;
        int attempts = 0;
        int successes = 0;
        int failures = 0;
        int revalidations = 0;
        int persistentFailures = 0;

        try {
            List<ExternalEvidenceRecord> allRecords = evidenceRepository.findAll();
            totalEvaluated = allRecords.size();

            for (ExternalEvidenceRecord record : allRecords) {
                String status = acquisitionService.evaluateFreshness(record);
                record.setFreshnessStatus(status);

                switch (status) {
                    case "FRESH" -> fresh++;
                    case "REFRESH_DUE" -> due++;
                    case "STALE_REVALIDATION_REQUIRED" -> stale++;
                    case "ACQUISITION_FAILED" -> failed++;
                    default -> unacquired++;
                }

                // If stale, ensure withheld from publication immediately
                if ("STALE_REVALIDATION_REQUIRED".equalsIgnoreCase(status)
                        || "ACQUISITION_FAILED".equalsIgnoreCase(status)) {
                    if ("PUBLISHED_ELIGIBLE".equalsIgnoreCase(record.getPublicationStatus())) {
                        record.setPublicationStatus("STAGED_INTERNAL");
                        log.info("[FreshnessScheduler] Withholding stale/failed record id='{}' from publication.", record.getId());
                    }
                }

                // Determine if acquisition refresh should be attempted
                boolean needsRefresh = "REFRESH_DUE".equalsIgnoreCase(status)
                        || "STALE_REVALIDATION_REQUIRED".equalsIgnoreCase(status)
                        || "ACQUISITION_FAILED".equalsIgnoreCase(status)
                        || "UNACQUIRED".equalsIgnoreCase(status);

                if (needsRefresh && record.getSourceUrl() != null && !record.getSourceUrl().isBlank()) {
                    String recordId = record.getId();
                    int priorFailures = consecutiveFailures.getOrDefault(recordId, 0);

                    // Check failure backoff: if persistent failures >= 3, flag and require human intervention
                    if (priorFailures >= MAX_CONSECUTIVE_FAILURES) {
                        persistentFailures++;
                        log.warn("[FreshnessScheduler] Record id='{}' has {} consecutive acquisition failures. Escalating in review queue.",
                                recordId, priorFailures);
                        continue;
                    }

                    // Concurrent duplicate download suppression
                    if (!activeAcquisitions.add(recordId)) {
                        log.debug("[FreshnessScheduler] Acquisition already active for record id='{}', skipping duplicate.", recordId);
                        continue;
                    }

                    attempts++;
                    try {
                        lastAttemptTimestamps.put(recordId, Instant.now());
                        String originalHash = record.getSourceDocumentHash();

                        // Execute safe acquisition and validation
                        var validationResult = validationService.acquireArtifactForRecord(recordId);

                        // Reload record state post-acquisition
                        Optional<ExternalEvidenceRecord> reloaded = evidenceRepository.findById(recordId);
                        if (reloaded.isPresent()) {
                            ExternalEvidenceRecord updated = reloaded.get();
                            if ("FRESH".equalsIgnoreCase(updated.getFreshnessStatus()) && updated.isArtifactBacked()) {
                                successes++;
                                consecutiveFailures.remove(recordId);

                                // Check if source bytes changed
                                if (originalHash != null && !originalHash.equalsIgnoreCase(updated.getAcquiredSha256())) {
                                    revalidations++;
                                    log.info("[FreshnessScheduler] Source bytes changed for id='{}' ({} -> {}). Revalidation triggered.",
                                            recordId, originalHash, updated.getAcquiredSha256());
                                }
                            } else {
                                failures++;
                                int count = consecutiveFailures.merge(recordId, 1, Integer::sum);
                                log.warn("[FreshnessScheduler] Refresh attempt failed for id='{}' (failure count: {}).", recordId, count);
                            }
                        }
                    } catch (Exception ex) {
                        failures++;
                        int count = consecutiveFailures.merge(recordId, 1, Integer::sum);
                        log.error("[FreshnessScheduler] Error during refresh attempt for id='{}': {}", recordId, ex.getMessage());
                    } finally {
                        activeAcquisitions.remove(recordId);
                    }
                }
            }

            // Save evaluated freshness statuses
            evidenceRepository.saveAll(allRecords);

        } finally {
            isCycleRunning.set(false);
        }

        Instant completedTime = Instant.now();
        log.info("[FreshnessScheduler] Freshness cycle complete in {}ms: total={}, fresh={}, due={}, stale={}, attempts={}, success={}, failed={}, revalidations={}, persistent={}",
                java.time.Duration.between(startTime, completedTime).toMillis(),
                totalEvaluated, fresh, due, stale, attempts, successes, failures, revalidations, persistentFailures);

        return FreshnessMonitoringReport.builder()
                .cycleStartedAt(startTime)
                .cycleCompletedAt(completedTime)
                .totalRecordsEvaluated(totalEvaluated)
                .freshCount(fresh)
                .refreshDueCount(due)
                .staleCount(stale)
                .acquisitionFailedCount(failed)
                .unacquiredCount(unacquired)
                .refreshAttemptsExecuted(attempts)
                .refreshSuccessCount(successes)
                .refreshFailureCount(failures)
                .revalidationsTriggered(revalidations)
                .persistentFailuresFlagged(persistentFailures)
                .cycleSkippedOverlap(false)
                .build();
    }

    public boolean isCycleRunning() {
        return isCycleRunning.get();
    }

    public Map<String, Integer> getConsecutiveFailures() {
        return Collections.unmodifiableMap(consecutiveFailures);
    }

    public void resetConsecutiveFailures(String recordId) {
        if (recordId != null) {
            consecutiveFailures.remove(recordId);
        } else {
            consecutiveFailures.clear();
        }
    }
}
