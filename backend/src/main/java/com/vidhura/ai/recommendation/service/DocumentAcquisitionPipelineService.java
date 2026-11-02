package com.vidhura.ai.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 6.2: Targeted Document Acquisition & Provenance Pipeline Service.
 *
 * Coordinates bounded, resumable, and integrity-validated document acquisition for
 * IRDAI registry records.
 *
 * Guarantees:
 * 1. Safe host & SSRF validation for every download URL.
 * 2. Immutable, content-addressed deduplicated storage outside the repository.
 * 3. Non-destructive UIN reconciliation (never overwrites registry UIN on mismatch; flags REVIEW_REQUIRED).
 * 4. Distinct logical PolicyDocumentRevision tracking for every registry record.
 * 5. Complete state history and forensic quarantine handling.
 */
@Slf4j
@Service
public class DocumentAcquisitionPipelineService {

    private final IrdaiRegistryRepository registryRepository;
    private final PolicyDocumentRevisionRepository revisionRepository;
    private final PolicyDiscoveryRunRepository runRepository;
    private final TargetedDocumentStorageService storageService;
    private final DocumentIntegrityValidator integrityValidator;
    private final PolicyDocumentIntelligenceService intelligenceService;

    public DocumentAcquisitionPipelineService(
            IrdaiRegistryRepository registryRepository,
            PolicyDocumentRevisionRepository revisionRepository,
            PolicyDiscoveryRunRepository runRepository,
            TargetedDocumentStorageService storageService,
            DocumentIntegrityValidator integrityValidator) {
        this(registryRepository, revisionRepository, runRepository, storageService, integrityValidator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentAcquisitionPipelineService(
            IrdaiRegistryRepository registryRepository,
            PolicyDocumentRevisionRepository revisionRepository,
            PolicyDiscoveryRunRepository runRepository,
            TargetedDocumentStorageService storageService,
            DocumentIntegrityValidator integrityValidator,
            @org.springframework.context.annotation.Lazy @org.springframework.beans.factory.annotation.Autowired(required = false)
            PolicyDocumentIntelligenceService intelligenceService) {
        this.registryRepository = registryRepository;
        this.revisionRepository = revisionRepository;
        this.runRepository = runRepository;
        this.storageService = storageService;
        this.integrityValidator = integrityValidator;
        this.intelligenceService = intelligenceService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (VidhuraAI-DocumentAcquisition/1.0; +https://vidhura.ai; regulatory-provenance@vidhura.ai)";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcquisitionBatchResult {
        private String runId;
        private int totalAttempted;
        private int successfulDownloads;
        private int contentDeduplicated;
        private int verifiedIntegrity;
        private int reviewRequired;
        private int quarantined;
        private int failed;
        private long totalBytesAcquired;
        private int uinMatchedCount;
        private int uinMismatchedCount;
        private int scannedNoTextLayerCount;
        private List<String> errorLog;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PipelineSummaryDto {
        private long totalRegistryRecords;
        private long totalRevisions;
        private long downloadedCount;
        private long verifiedIntegrityCount;
        private long reviewRequiredCount;
        private long quarantinedCount;
        private long retryPendingCount;
        private long uniqueArtifactCount;
        private long totalBytesStored;
        private long uinMatchedCount;
        private long uinMismatchedCount;
        private long scannedNoTextLayerCount;
        private String storageLocation;
    }

    /**
     * Executes the representative pilot batch covering the 10 distinct regulatory archetypes.
     */
    public AcquisitionBatchResult executePilotBatch(String runId) {
        log.info("[AcquisitionPipeline] Initiating Phase 6.2 representative pilot batch...");
        List<IrdaiRegistryRecord> pilotRecords = selectPilotCohort();
        log.info("[AcquisitionPipeline] Selected {} representative records for pilot execution", pilotRecords.size());

        return processBatch(pilotRecords, runId != null ? runId : "PILOT_RUN_" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Executes a bounded batch of records from the inventory with resumability and failure tracking.
     */
    public AcquisitionBatchResult executeBatch(int batchSize, boolean retryFailed, String runId) {
        String activeRunId = (runId != null && !runId.isBlank()) ? runId : "ACQ_BATCH_" + System.currentTimeMillis();

        List<IrdaiRegistryRecord> targetRecords = loadEligibleRecords(batchSize, retryFailed);
        log.info("[AcquisitionPipeline] Processing bounded batch of {} records (retryFailed={}) for runId={}",
                targetRecords.size(), retryFailed, activeRunId);

        return processBatch(targetRecords, activeRunId);
    }

    private AcquisitionBatchResult processBatch(List<IrdaiRegistryRecord> records, String runId) {
        int attempted = 0;
        int successful = 0;
        int deduplicated = 0;
        int verified = 0;
        int reviewRequired = 0;
        int quarantined = 0;
        int failed = 0;
        long totalBytes = 0;
        int uinMatched = 0;
        int uinMismatched = 0;
        int scannedNoText = 0;
        List<String> errors = new ArrayList<>();

        for (IrdaiRegistryRecord record : records) {
            attempted++;
            String recordId = record.getId();
            String docUrl = record.getDocumentDownloadUrl();

            try {
                // 1. Validate download URL presence
                if (docUrl == null || docUrl.isBlank()) {
                    record.setAcquisitionState("UNAVAILABLE");
                    registryRepository.save(record);
                    failed++;
                    errors.add("Record " + recordId + ": Missing document download URL");
                    continue;
                }

                // 2. Validate hostname & SSRF allowlist
                if (!isAllowlistedOfficialDomain(docUrl)) {
                    record.setAcquisitionState("QUARANTINED");
                    registryRepository.save(record);
                    quarantined++;
                    errors.add("Record " + recordId + ": Target domain not on official allowlist (SSRF safety): " + docUrl);
                    continue;
                }

                // 3. Acquire payload bytes
                record.setAcquisitionState("DOWNLOADING");
                registryRepository.save(record);

                byte[] payloadBytes = fetchDocumentBytesWithRetry(docUrl, 3);
                if (payloadBytes == null || payloadBytes.length == 0) {
                    record.setAcquisitionState("RETRY_PENDING");
                    registryRepository.save(record);
                    failed++;
                    errors.add("Record " + recordId + ": Download returned null or empty payload: " + docUrl);
                    continue;
                }

                totalBytes += payloadBytes.length;
                successful++;

                // 4. Validate Document Integrity
                DocumentIntegrityValidator.ValidationResult valResult = integrityValidator.validatePdfPayload(payloadBytes);

                if (!valResult.isValid()) {
                    // Quarantine invalid or corrupt payload
                    storageService.quarantinePayload(payloadBytes, recordId, valResult.getFailureReason());
                    record.setAcquisitionState("QUARANTINED");
                    registryRepository.save(record);
                    quarantined++;
                    errors.add(String.format("Record %s quarantined: %s", recordId, valResult.getFailureReason()));

                    // Record revision state
                    recordRevisionMetadata(record, valResult, null, runId, "QUARANTINED", valResult.getFailureReason());
                    continue;
                }

                // 5. Store in content-addressed deduplicated storage
                String sha256 = valResult.getSha256();
                Map<String, Object> manifest = new HashMap<>();
                manifest.put("registryRecordId", recordId);
                manifest.put("sourceUin", record.getSourceUin());
                manifest.put("insurerName", record.getInsurerName());
                manifest.put("productName", record.getProductName());
                manifest.put("sourceUrl", docUrl);
                manifest.put("pageCount", valResult.getPageCount());
                manifest.put("hasTextLayer", valResult.isHasTextLayer());
                manifest.put("acquisitionRunId", runId);

                TargetedDocumentStorageService.StorageReceipt receipt = storageService.storeDocument(
                        payloadBytes, sha256, recordId, record.getDocumentFilename(), manifest
                );

                if (receipt.isAlreadyExisted()) {
                    deduplicated++;
                }

                // 6. UIN Reconciliation & State Resolution
                String regUin = record.getSourceUin() != null ? record.getSourceUin().trim() : "";
                String extractedUin = valResult.getExtractedUin();
                String uinMatchStatus;
                String finalState;

                if (!valResult.isHasTextLayer()) {
                    scannedNoText++;
                    uinMatchStatus = "UNEXTRACTED_SCANNED";
                    finalState = "VERIFIED_INTEGRITY";
                    verified++;
                } else if (regUin.isBlank()) {
                    uinMatchStatus = "MISSING_REGISTRY_UIN";
                    finalState = "REVIEW_REQUIRED";
                    reviewRequired++;
                } else if (extractedUin != null && isUinFuzzyMatch(regUin, extractedUin)) {
                    uinMatchStatus = "MATCHED";
                    finalState = "VERIFIED_INTEGRITY";
                    uinMatched++;
                    verified++;
                } else {
                    uinMatchStatus = "MISMATCH";
                    finalState = "REVIEW_REQUIRED";
                    uinMismatched++;
                    reviewRequired++;
                    log.info("[AcquisitionPipeline] UIN mismatch for {}: Registry='{}' vs Extracted='{}'",
                            recordId, regUin, extractedUin);
                }

                // 7. Persist PolicyDocumentRevision
                PolicyDocumentRevision rev = recordRevisionMetadata(record, valResult, receipt, runId, finalState, null);
                rev.setUinMatchStatus(uinMatchStatus);
                revisionRepository.save(rev);

                // 8. Update IrdaiRegistryRecord
                record.setDocumentRevisionId(rev.getId());
                record.setVerifiedSha256(sha256);
                record.setExtractedDocumentUin(extractedUin);
                record.setUinMatchStatus(uinMatchStatus);
                record.setAcquisitionState(finalState);
                record.setUpdatedAt(Instant.now());
                registryRepository.save(record);

                // 9. Automated Post-Acquisition Structured Extraction
                if (intelligenceService != null && ("VERIFIED_INTEGRITY".equals(finalState) || "REVIEW_REQUIRED".equals(finalState))) {
                    try {
                        intelligenceService.processRevision(rev, runId, false);
                        log.info("[AcquisitionPipeline] Automated post-acquisition extraction completed for revision {}", rev.getId());
                    } catch (Exception ex) {
                        log.warn("[AcquisitionPipeline] Automated post-acquisition extraction deferred for revision {}: {}", rev.getId(), ex.getMessage());
                    }
                }

                // Polite pause between requests to prevent server exhaustion
                Thread.sleep(150);

            } catch (Exception e) {
                log.error("[AcquisitionPipeline] Error acquiring record {}: {}", recordId, e.getMessage());
                record.setAcquisitionState("RETRY_PENDING");
                registryRepository.save(record);
                failed++;
                errors.add("Record " + recordId + ": " + e.getMessage());
            }
        }

        return AcquisitionBatchResult.builder()
                .runId(runId)
                .totalAttempted(attempted)
                .successfulDownloads(successful)
                .contentDeduplicated(deduplicated)
                .verifiedIntegrity(verified)
                .reviewRequired(reviewRequired)
                .quarantined(quarantined)
                .failed(failed)
                .totalBytesAcquired(totalBytes)
                .uinMatchedCount(uinMatched)
                .uinMismatchedCount(uinMismatched)
                .scannedNoTextLayerCount(scannedNoText)
                .errorLog(errors)
                .build();
    }

    private PolicyDocumentRevision recordRevisionMetadata(IrdaiRegistryRecord record,
                                                         DocumentIntegrityValidator.ValidationResult val,
                                                         TargetedDocumentStorageService.StorageReceipt receipt,
                                                         String runId, String state, String errorMsg) {

        Optional<PolicyDocumentRevision> existing = revisionRepository.findByRegistryRecordId(record.getId());
        PolicyDocumentRevision rev = existing.orElseGet(PolicyDocumentRevision::new);

        rev.setRegistryRecordId(record.getId());
        rev.setSourceUin(record.getSourceUin());
        rev.setIrdaUin(record.getNormalizedUin() != null && !record.getNormalizedUin().isBlank()
                ? record.getNormalizedUin() : "UNASSIGNED");
        rev.setInsurerCode(record.getNormalizedInsurerCode());
        rev.setProductName(record.getProductName());
        rev.setSourceUrl(record.getDocumentDownloadUrl());
        rev.setFinalAcquisitionUrl(record.getDocumentDownloadUrl());
        rev.setAcquiredAt(Instant.now());
        rev.setAcquisitionRunId(runId);
        rev.setAcquisitionState(state);
        rev.setMediaType("application/pdf");

        if (val != null) {
            rev.setVerifiedSha256(val.getSha256());
            rev.setFileSizeBytes(val.getByteSize());
            rev.setPageCount(val.getPageCount());
            rev.setHasTextLayer(val.isHasTextLayer());
            rev.setIsEncrypted(val.isEncrypted());
            rev.setExtractedDocumentUin(val.getExtractedUin());
            rev.setIntegrityStatus(val.getIntegrityStatus());
        }

        if (receipt != null) {
            rev.setStorageKey(receipt.getContentAddressedPath());
            rev.setManifestPath(receipt.getManifestPath());
            rev.setDocumentArtifactId(new File(receipt.getContentAddressedPath()).getName());
        }

        if (errorMsg != null) {
            rev.setLastErrorMessage(errorMsg);
        }

        rev.getStateHistory().add(String.format("%s@%s", state, Instant.now()));
        if (rev.getCreatedAt() == null) {
            rev.setCreatedAt(Instant.now());
        }
        rev.setUpdatedAt(Instant.now());

        return revisionRepository.save(rev);
    }

    private byte[] fetchDocumentBytesWithRetry(String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();

                if (status == 200) {
                    return response.body();
                } else if (status == 429 || status >= 500) {
                    long backoff = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("[AcquisitionPipeline] HTTP {} on {}. Backoff {} ms", status, url, backoff);
                    Thread.sleep(backoff);
                } else {
                    log.warn("[AcquisitionPipeline] HTTP {} unrecoverable on {}", status, url);
                    return null;
                }
            } catch (Exception e) {
                log.warn("[AcquisitionPipeline] Network error fetching {}: {}. Attempt {}/{}", url, e.getMessage(), attempt, maxRetries);
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isAllowlistedOfficialDomain(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase();

            // Guard against loopback, private IPs, metadata endpoints
            if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1") ||
                    lowerHost.equals("169.254.169.254") || lowerHost.startsWith("192.168.") ||
                    lowerHost.startsWith("10.") || lowerHost.endsWith(".internal")) {
                return false;
            }

            return OfficialSourceAcquisitionService.ALLOWED_OFFICIAL_DOMAINS.contains(lowerHost) ||
                    lowerHost.endsWith(".irdai.gov.in") || lowerHost.equals("irdai.gov.in");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUinFuzzyMatch(String uin1, String uin2) {
        if (uin1 == null || uin2 == null) return false;
        String c1 = uin1.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String c2 = uin2.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return c1.equals(c2) || c1.contains(c2) || c2.contains(c1);
    }

    public List<IrdaiRegistryRecord> selectPilotCohort() {
        List<IrdaiRegistryRecord> cohort = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        List<String> targetCategories = List.of(
                "INDIVIDUAL", "FAMILY_FLOATER", "GROUP", "PERSONAL_ACCIDENT",
                "TRAVEL", "BENEFIT_BASED", "RIDER_ADDON", "UNKNOWN_UNCLASSIFIED"
        );

        // 1. Pick 1 record for each target classification
        for (String cat : targetCategories) {
            org.springframework.data.domain.Page<IrdaiRegistryRecord> page = registryRepository.findByClassificationStatus(cat, org.springframework.data.domain.PageRequest.of(0, 1));
            if (page != null && !page.getContent().isEmpty()) {
                IrdaiRegistryRecord rec = page.getContent().get(0);
                if (!selectedIds.contains(rec.getId())) {
                    cohort.add(rec);
                    selectedIds.add(rec.getId());
                }
            }
        }

        List<IrdaiRegistryRecord> allRecords = registryRepository.findAll();
        if (allRecords == null) {
            allRecords = List.of();
        }

        // 2. Pick 1 record with missing UIN
        List<IrdaiRegistryRecord> missingUins = allRecords.stream()
                .filter(r -> r.getSourceUin() == null || r.getSourceUin().isBlank())
                .limit(1)
                .toList();
        for (IrdaiRegistryRecord r : missingUins) {
            if (!selectedIds.contains(r.getId())) {
                cohort.add(r);
                selectedIds.add(r.getId());
            }
        }

        // 3. Pick 1 record with conflict or duplicate status
        List<IrdaiRegistryRecord> conflicts = allRecords.stream()
                .filter(r -> "CONFLICT_DETECTED".equals(r.getDuplicateStatus()) || "DUPLICATE_IDENTICAL".equals(r.getDuplicateStatus()))
                .limit(1)
                .toList();
        for (IrdaiRegistryRecord r : conflicts) {
            if (!selectedIds.contains(r.getId())) {
                cohort.add(r);
                selectedIds.add(r.getId());
            }
        }

        // If repository is empty (e.g. startup before crawl), populate from offline inventory
        if (cohort.isEmpty()) {
            cohort = loadPilotFromOfflineInventory();
        }

        return cohort;
    }

    private List<IrdaiRegistryRecord> loadEligibleRecords(int limit, boolean retryFailed) {
        List<IrdaiRegistryRecord> records = registryRepository.findAll();
        if (records.isEmpty()) {
            records = loadFromOfflineInventory();
        }

        return records.stream()
                .filter(r -> {
                    String state = r.getAcquisitionState();
                    if (state == null || "DISCOVERED".equals(state) || "QUEUED".equals(state)) {
                        return true;
                    }
                    if (retryFailed && ("RETRY_PENDING".equals(state) || "UNAVAILABLE".equals(state))) {
                        return true;
                    }
                    return false;
                })
                .limit(limit)
                .toList();
    }

    public List<IrdaiRegistryRecord> loadFromOfflineInventory() {
        File file = new File("scripts/irdai_portal_full_inventory.json");
        if (!file.exists()) return Collections.emptyList();

        try {
            Map<String, Object> map = objectMapper.readValue(file, new TypeReference<>() {});
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) map.get("records");
            List<IrdaiRegistryRecord> records = new ArrayList<>();

            for (Map<String, Object> item : rawList) {
                Map<String, String> raw = (Map<String, String>) item.get("raw");
                String uin = raw.get("uin");
                String ins = raw.get("insurer_name");
                String prod = raw.get("product_name");
                String doc = raw.get("document_filename");

                String id = (uin != null && !uin.isBlank())
                        ? "IRDAI_" + uin.trim().toUpperCase()
                        : "IRDAI_NO_UIN_" + Math.abs((ins + "_" + prod + "_" + doc).hashCode());

                IrdaiRegistryRecord r = IrdaiRegistryRecord.builder()
                        .id(id)
                        .sourceUin(uin)
                        .normalizedUin(uin != null ? uin.trim().toUpperCase() : "")
                        .insurerName(ins)
                        .productName(prod)
                        .financialYear(raw.get("financial_year"))
                        .approvalDate(raw.get("approval_date"))
                        .rawProductType(raw.get("product_type"))
                        .documentDownloadUrl(raw.get("document_url"))
                        .documentFilename(doc)
                        .advertisedDocumentSize(raw.get("advertised_size"))
                        .archiveStatus(raw.get("archive_status"))
                        .archived("Archived".equalsIgnoreCase(raw.get("archive_status")))
                        .acquisitionState("DISCOVERED")
                        .build();

                records.add(r);
            }
            return records;
        } catch (Exception e) {
            log.error("[AcquisitionPipeline] Could not load offline inventory: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<IrdaiRegistryRecord> loadPilotFromOfflineInventory() {
        List<IrdaiRegistryRecord> all = loadFromOfflineInventory();
        if (all.isEmpty()) return Collections.emptyList();

        List<IrdaiRegistryRecord> pilot = new ArrayList<>();
        // Select 10 diverse records
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            pilot.add(all.get(i * (all.size() / 10)));
        }
        return pilot;
    }

    public PipelineSummaryDto getPipelineSummary() {
        long totalRecords = registryRepository.count();
        long totalRevisions = revisionRepository.count();
        long downloaded = revisionRepository.countByAcquisitionState("VERIFIED_INTEGRITY") +
                revisionRepository.countByAcquisitionState("REVIEW_REQUIRED");
        long verified = revisionRepository.countByAcquisitionState("VERIFIED_INTEGRITY");
        long reviewRequired = revisionRepository.countByAcquisitionState("REVIEW_REQUIRED");
        long quarantined = revisionRepository.countByAcquisitionState("QUARANTINED");
        long retryPending = revisionRepository.countByAcquisitionState("RETRY_PENDING");

        long matched = revisionRepository.findAll().stream()
                .filter(r -> "MATCHED".equals(r.getUinMatchStatus()))
                .count();
        long mismatched = revisionRepository.findAll().stream()
                .filter(r -> "MISMATCH".equals(r.getUinMatchStatus()))
                .count();
        long scanned = revisionRepository.findAll().stream()
                .filter(r -> "UNEXTRACTED_SCANNED".equals(r.getUinMatchStatus()))
                .count();

        long uniqueArtifacts = revisionRepository.findAll().stream()
                .map(PolicyDocumentRevision::getVerifiedSha256)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long totalBytes = revisionRepository.findAll().stream()
                .mapToLong(r -> r.getFileSizeBytes() != null ? r.getFileSizeBytes() : 0L)
                .sum();

        return PipelineSummaryDto.builder()
                .totalRegistryRecords(totalRecords)
                .totalRevisions(totalRevisions)
                .downloadedCount(downloaded)
                .verifiedIntegrityCount(verified)
                .reviewRequiredCount(reviewRequired)
                .quarantinedCount(quarantined)
                .retryPendingCount(retryPending)
                .uniqueArtifactCount(uniqueArtifacts)
                .totalBytesStored(totalBytes)
                .uinMatchedCount(matched)
                .uinMismatchedCount(mismatched)
                .scannedNoTextLayerCount(scanned)
                .storageLocation(storageService.getStorageRootPath())
                .build();
    }
}
