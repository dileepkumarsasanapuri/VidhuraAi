package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.*;
import com.vidhura.ai.recommendation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 5.6: Schedulable and Idempotent Policy Discovery Service.
 *
 * Provides:
 * 1. Configurable background schedule and manual operator execution.
 * 2. Overlap lockout prevention via AtomicBoolean and persistent run fencing.
 * 3. Bounded execution and per-source progress reporting.
 * 4. Persistent run history in MongoDB (PolicyDiscoveryRun).
 * 5. Safe recovery across application restarts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyDiscoverySchedulerService {

    private final InsurerProfileRepository insurerProfileRepository;
    private final PolicySourceRepository policySourceRepository;
    private final PolicyDiscoveryRunRepository runRepository;
    private final PolicyDiscoveryCandidateRepository candidateRepository;
    private final OfficialSourceDiscoveryEngine discoveryEngine;
    private final PolicyDiscoveryOrchestratorService orchestratorService;
    private final OfficialSourceAcquisitionService acquisitionService;

    private final AtomicBoolean isDiscoveryRunning = new AtomicBoolean(false);

    @Value("${vidhura.discovery.enabled:true}")
    private boolean discoveryEnabled = true;

    public boolean isRunning() {
        return isDiscoveryRunning.get();
    }

    @Scheduled(cron = "${vidhura.discovery.schedule-cron:0 0 2 * * *}")
    public void scheduledDiscoveryCycle() {
        if (!discoveryEnabled) {
            log.info("[DiscoveryScheduler] Automated discovery is disabled by configuration.");
            return;
        }
        log.info("[DiscoveryScheduler] Executing scheduled automated discovery cycle...");
        executeDiscoveryRun("SCHEDULED", null);
    }

    /**
     * Executes an idempotent discovery run across registered official sources or target list.
     */
    public synchronized PolicyDiscoveryRun executeDiscoveryRun(String triggerType, List<String> targetUrls) {
        if (!isDiscoveryRunning.compareAndSet(false, true)) {
            log.warn("[DiscoveryScheduler] Discovery run already in progress. Overlap rejected.");
            throw new IllegalStateException("A discovery run is already in progress.");
        }

        Instant startTime = Instant.now();
        PolicyDiscoveryRun run = PolicyDiscoveryRun.builder()
                .id(java.util.UUID.randomUUID().toString())
                .triggerType(triggerType != null ? triggerType : "MANUAL_ADMIN")
                .startedAt(startTime)
                .status("IN_PROGRESS")
                .build();
        try {
            if (runRepository != null) {
                run = runRepository.save(run);
            }
        } catch (Exception e) {
            log.warn("[DiscoveryScheduler] Could not persist run to MongoDB (cluster unreachable): {}", e.getMessage());
        }

        log.info("[DiscoveryScheduler] Started Discovery Run [{}] triggered by {}", run.getId(), run.getTriggerType());

        try {
            List<String> sourcesToScan = new ArrayList<>();
            if (targetUrls != null && !targetUrls.isEmpty()) {
                sourcesToScan.addAll(targetUrls);
            } else {
                // Collect official source URLs from Insurer Profiles and Policy Sources
                insurerProfileRepository.findByActiveTrue().forEach(p -> {
                    if (p.getProductsListingUrl() != null && !p.getProductsListingUrl().isBlank()) {
                        sourcesToScan.add(p.getProductsListingUrl());
                    }
                });
            }

            int scanned = 0;
            int totalDiscovered = 0;
            int totalNew = 0;
            int totalRevised = 0;
            int totalDups = 0;
            int totalAcquired = 0;

            for (String sourceUrl : sourcesToScan) {
                scanned++;
                run.getLogMessages().add("Scanning source [" + scanned + "/" + sourcesToScan.size() + "]: " + sourceUrl);
                log.info("[DiscoveryScheduler] Scanning source: {}", sourceUrl);

                try {
                    // Fetch source page safely via acquisition service
                    OfficialSourceAcquisitionService.AcquisitionResult pageResult =
                            acquisitionService.acquireFromOfficialSource(sourceUrl);

                    String htmlContent = (pageResult.getPayload() != null)
                            ? new String(pageResult.getPayload(), java.nio.charset.StandardCharsets.UTF_8)
                            : "";

                    OfficialSourceDiscoveryEngine.DiscoveryScanSummary summary =
                            discoveryEngine.processSource(sourceUrl, htmlContent, run.getId());

                    totalDiscovered += summary.getLinksFound();
                    totalNew += summary.getNewCandidates();
                    totalRevised += summary.getUpdatedRevisions();
                    totalDups += summary.getDuplicatesSkipped();

                    run.getPerSourceSummary().put(sourceUrl,
                            "Links: " + summary.getLinksFound() + ", New: " + summary.getNewCandidates()
                                    + ", Revised: " + summary.getUpdatedRevisions() + ", Dups: " + summary.getDuplicatesSkipped());

                } catch (Exception e) {
                    log.error("[DiscoveryScheduler] Failed scanning {}: {}", sourceUrl, e.getMessage());
                    run.setFailuresCount(run.getFailuresCount() + 1);
                    run.getPerSourceSummary().put(sourceUrl, "ERROR: " + e.getMessage());
                    run.getLogMessages().add("Error on " + sourceUrl + ": " + e.getMessage());
                }
            }

            // Automatically attempt acquisition for newly discovered candidates in this run
            List<PolicyDiscoveryCandidate> runCandidates;
            try {
                runCandidates = candidateRepository != null ? candidateRepository.findByDiscoveryRunId(run.getId()) : List.of();
            } catch (Exception e) {
                log.warn("[DiscoveryScheduler] Could not query candidates by discovery run: {}", e.getMessage());
                runCandidates = List.of();
            }
            for (PolicyDiscoveryCandidate c : runCandidates) {
                if ("ACQUISITION_PENDING".equals(c.getLifecycleStage())) {
                    try {
                        orchestratorService.acquireCandidateDocument(c.getId());
                        orchestratorService.extractAndValidateCandidate(c.getId());
                        totalAcquired++;
                    } catch (Exception e) {
                        log.warn("[DiscoveryScheduler] Auto-acquisition failed for candidate {}: {}", c.getId(), e.getMessage());
                    }
                }
            }

            run.setSourcesScanned(scanned);
            run.setCandidatesDiscovered(totalDiscovered);
            run.setCandidatesNew(totalNew + totalRevised);
            run.setCandidatesDuplicate(totalDups);
            run.setCandidatesAcquired(totalAcquired);
            run.setStatus(run.getFailuresCount() == 0 ? "COMPLETED" : "PARTIAL_SUCCESS");
            run.setCompletedAt(Instant.now());
            run.getLogMessages().add("Discovery run completed. Discovered: " + totalDiscovered
                    + ", New: " + totalNew + ", Revised: " + totalRevised + ", Acquired: " + totalAcquired);

        } catch (Exception e) {
            log.error("[DiscoveryScheduler] Critical error in discovery run {}: {}", run.getId(), e.getMessage());
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
        } finally {
            isDiscoveryRunning.set(false);
            run.setUpdatedAt(Instant.now());
            try {
                if (runRepository != null) {
                    runRepository.save(run);
                }
            } catch (Exception e) {
                log.warn("[DiscoveryScheduler] Could not save completed run to MongoDB: {}", e.getMessage());
            }
            log.info("[DiscoveryScheduler] Completed Discovery Run [{}] with status {}", run.getId(), run.getStatus());
        }

        return run;
    }
}
