package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 6.1: IRDAI Health Insurance Products Registry Inventory Service.
 *
 * Implements reliable, resumable, and persistent inventory crawler for the official
 * IRDAI Health Insurance Products portal (https://irdai.gov.in/health-insurance-products).
 *
 * Guarantees:
 * 1. Complete metadata extraction (exact UIN, product name, insurer, financial year, approval date,
 *    product type, archive status, direct PDF download URL, document filename, advertised size).
 * 2. Logically separated from recommendation-ready StructuredPolicy records.
 * 3. Deterministic identity, duplicate tracking, and explicit preservation of conflicting records.
 * 4. Resumable traversal with exponential backoff on transient errors and rate limits.
 * 5. Idempotent persistence with in-place updates and zero unneeded allocations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrdaiRegistryInventoryService {

    public static final String DEFAULT_PORTAL_URL = "https://irdai.gov.in/health-insurance-products";
    public static final String PORTLET_ID = "com_irdai_document_media_IRDAIDocumentMediaPortlet";
    public static final int DEFAULT_DELTA = 60; // 60 items per page as supported natively by Liferay

    private final IrdaiRegistryRepository registryRepository;
    private final PolicyDiscoveryRunRepository runRepository;
    private final MongoTemplate mongoTemplate;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (VidhuraAI-IRDAI-Registry/1.0; +https://vidhura.ai)";

    // Regex patterns for parsing Liferay HTML table rows
    private static final Pattern TOTAL_RESULTS_PATTERN = Pattern.compile(
            "(?i)Showing\\s+[\\d,]+\\s*-\\s*[\\d,]+\\s*of\\s*([\\d,]+)\\s*results"
    );

    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(
            "(?is)<tr[^>]*>(.*?)</tr>"
    );

    private static final Pattern TABLE_CELL_PATTERN = Pattern.compile(
            "(?is)<td[^>]*>(.*?)</td>"
    );

    private static final Pattern LINK_HREF_PATTERN = Pattern.compile(
            "(?is)<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"
    );

    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "(?is)<p[^>]*>(.*?)</p>"
    );

    private static final Pattern TAG_STRIP_PATTERN = Pattern.compile(
            "(?is)<[^>]+>"
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlExecutionSummary {
        private String runId;
        private Instant startedAt;
        private Instant completedAt;
        private String status;
        private int portalReportedTotal;
        private int totalPages;
        private int pagesProcessed;
        private int recordsCollected;
        private int newRecordsSaved;
        private int recordsUpdated;
        private int duplicatesFound;
        private int conflictsDetected;
        private List<Integer> failedPages;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventorySummaryDto {
        private long totalRecords;
        private long uniqueUins;
        private long nonArchivedCount;
        private long archivedCount;
        private long duplicateCount;
        private long conflictCount;
        private long missingUinCount;
        private long missingDocumentLinkCount;
        private Map<String, Long> classificationDistribution;
        private Map<String, Long> insurerDistribution;
        private Map<String, Long> financialYearDistribution;
        private String lastRunId;
        private Instant lastRunCompletedAt;
        private String lastRunStatus;
    }

    /**
     * Executes the full IRDAI portal inventory crawl with optional checkpoint resumability.
     */
    public synchronized CrawlExecutionSummary executeInventoryCrawl(boolean resumeIfPossible, String targetUrl) {
        String portalUrl = (targetUrl != null && !targetUrl.isBlank()) ? targetUrl : DEFAULT_PORTAL_URL;
        Instant startTime = Instant.now();

        // 1. Establish or resume PolicyDiscoveryRun audit record
        PolicyDiscoveryRun runRecord = initializeRunRecord(resumeIfPossible);
        String runId = runRecord.getId();
        log.info("[IRDAI-Registry] Starting crawl run ID={} (resumable={}) against {}", runId, resumeIfPossible, portalUrl);

        int delta = DEFAULT_DELTA;
        List<Integer> failedPages = new ArrayList<>();
        int recordsCollected = 0;
        int newRecordsSaved = 0;
        int recordsUpdated = 0;
        int duplicatesFound = 0;
        int conflictsDetected = 0;

        try {
            // 2. Fetch Page 1 to discover total records and pagination metadata dynamically
            String page1Url = buildPageUrl(portalUrl, 1, delta);
            String page1Html = fetchUrlWithBackoff(page1Url, 3);
            if (page1Html == null || page1Html.isBlank()) {
                throw new IllegalStateException("Failed to fetch page 1 from IRDAI portal: " + page1Url);
            }

            int totalReported = extractTotalResults(page1Html);
            if (totalReported <= 0) {
                log.warn("[IRDAI-Registry] Could not parse total results from page 1; defaulting to 1820");
                totalReported = 1820;
            }

            int totalPages = (int) Math.ceil((double) totalReported / delta);
            log.info("[IRDAI-Registry] Discovered {} total portal records across {} pages (delta={})",
                    totalReported, totalPages, delta);

            // Determine resume start page
            int startPage = 1;
            if (resumeIfPossible && runRecord.getSourcesScanned() > 1 && runRecord.getSourcesScanned() <= totalPages) {
                startPage = runRecord.getSourcesScanned();
                log.info("[IRDAI-Registry] Resuming crawl from checkpoint page {}", startPage);
            }

            // 3. Sequential traversal across all result pages
            for (int page = startPage; page <= totalPages; page++) {
                String pageUrl = (page == 1) ? page1Url : buildPageUrl(portalUrl, page, delta);
                String pageHtml = (page == 1) ? page1Html : fetchUrlWithBackoff(pageUrl, 3);

                if (pageHtml == null || pageHtml.isBlank()) {
                    log.error("[IRDAI-Registry] Failed to fetch page {} after retries", page);
                    failedPages.add(page);
                    continue;
                }

                List<IrdaiRegistryRecord> pageRecords = parseHtmlPage(pageHtml, page, pageUrl, runId);
                recordsCollected += pageRecords.size();

                for (IrdaiRegistryRecord record : pageRecords) {
                    SaveOutcome outcome = saveRecordIdempotently(record);
                    if (outcome == SaveOutcome.CREATED) newRecordsSaved++;
                    else if (outcome == SaveOutcome.UPDATED) recordsUpdated++;
                    else if (outcome == SaveOutcome.DUPLICATE) duplicatesFound++;
                    else if (outcome == SaveOutcome.CONFLICT) conflictsDetected++;
                }

                // Update run progress in audit record
                runRecord.setSourcesScanned(page);
                runRecord.setCandidatesDiscovered(recordsCollected);
                runRecord.setCandidatesNew(newRecordsSaved);
                runRecord.setCandidatesDuplicate(duplicatesFound + conflictsDetected);
                runRecord.getPerSourceSummary().put("page_" + page, "Records: " + pageRecords.size());
                runRecord.setUpdatedAt(Instant.now());
                runRepository.save(runRecord);

                log.info("[IRDAI-Registry] Page {}/{} processed: {} records. Cumulative={}",
                        page, totalPages, pageRecords.size(), recordsCollected);

                // Polite pause between requests to prevent rate limiting
                if (page < totalPages) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 4. Finalize run record
            String finalStatus = failedPages.isEmpty() ? "COMPLETED" : "PARTIAL_SUCCESS";
            runRecord.setStatus(finalStatus);
            runRecord.setCompletedAt(Instant.now());
            runRecord.setFailuresCount(failedPages.size());
            runRecord.getLogMessages().add(String.format("Crawl completed: %d records collected, %d new, %d updated, %d conflicts, %d failed pages",
                    recordsCollected, newRecordsSaved, recordsUpdated, conflictsDetected, failedPages.size()));
            runRepository.save(runRecord);

            return CrawlExecutionSummary.builder()
                    .runId(runId)
                    .startedAt(startTime)
                    .completedAt(runRecord.getCompletedAt())
                    .status(finalStatus)
                    .portalReportedTotal(totalReported)
                    .totalPages(totalPages)
                    .pagesProcessed(runRecord.getSourcesScanned())
                    .recordsCollected(recordsCollected)
                    .newRecordsSaved(newRecordsSaved)
                    .recordsUpdated(recordsUpdated)
                    .duplicatesFound(duplicatesFound)
                    .conflictsDetected(conflictsDetected)
                    .failedPages(failedPages)
                    .build();

        } catch (Exception e) {
            log.error("[IRDAI-Registry] Fatal error in crawl run {}: {}", runId, e.getMessage(), e);
            runRecord.setStatus("FAILED");
            runRecord.setErrorMessage(e.getMessage());
            runRecord.setCompletedAt(Instant.now());
            runRepository.save(runRecord);

            return CrawlExecutionSummary.builder()
                    .runId(runId)
                    .startedAt(startTime)
                    .completedAt(Instant.now())
                    .status("FAILED")
                    .failedPages(failedPages)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Parses an HTML table page from IRDAI portal into structured IrdaiRegistryRecord entities.
     * Pure function suitable for deterministic testing without network calls.
     */
    public List<IrdaiRegistryRecord> parseHtmlPage(String htmlContent, int pageNumber, String pageUrl, String runId) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return Collections.emptyList();
        }

        List<IrdaiRegistryRecord> records = new ArrayList<>();
        Matcher rowMatcher = TABLE_ROW_PATTERN.matcher(htmlContent);
        int rowIndex = 0;

        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = TABLE_CELL_PATTERN.matcher(rowHtml);
            while (cellMatcher.find()) {
                cells.add(cellMatcher.group(1));
            }

            // Valid product rows have at least 8 or 9 cells
            if (cells.size() < 8) {
                continue;
            }

            // Cell 0: Checkbox
            // Cell 1: Archive / Non Archive
            String rawArchiveStatus = cleanHtml(cells.get(1));
            // Cell 2: Financial Year
            String rawFinancialYear = cleanHtml(cells.get(2));
            // Cell 3: Insurer Name
            String rawInsurerName = cleanHtml(cells.get(3));
            // Cell 4: UIN
            String rawUin = cleanHtml(cells.get(4));
            // Cell 5: Product Name
            String rawProductName = cleanHtml(cells.get(5));
            // Cell 6: Date of Approval
            String rawApprovalDate = cleanHtml(cells.get(6));
            // Cell 7: Documents (contains link, filename, and advertised size)
            String docCellHtml = cells.get(7);
            String docDownloadUrl = extractHref(docCellHtml);
            String docFilename = extractLinkText(docCellHtml);
            String advertisedSize = extractParagraphText(docCellHtml);
            // Cell 8: Type of Product (if present)
            String rawProductType = cells.size() > 8 ? cleanHtml(cells.get(8)) : "";

            // Skip header row or completely blank spacer rows
            if ("Archive / Non Archive".equalsIgnoreCase(rawArchiveStatus) ||
                    (rawUin.isBlank() && rawProductName.isBlank() && rawInsurerName.isBlank() && docDownloadUrl.isBlank())) {
                continue;
            }

            rowIndex++;
            String normUin = normalizeUin(rawUin);
            String normInsurer = deriveInsurerCode(rawInsurerName, normUin);
            boolean isArchived = "Archived".equalsIgnoreCase(rawArchiveStatus);
            String classification = deriveClassification(rawProductType, rawProductName, normUin, isArchived);

            // Deterministic identity generation
            String stableId = generateStableRecordId(normUin, rawInsurerName, rawProductName, docFilename);

            Map<String, String> rawPayload = new HashMap<>();
            rawPayload.put("archive_status", rawArchiveStatus);
            rawPayload.put("financial_year", rawFinancialYear);
            rawPayload.put("insurer_name", rawInsurerName);
            rawPayload.put("uin", rawUin);
            rawPayload.put("product_name", rawProductName);
            rawPayload.put("approval_date", rawApprovalDate);
            rawPayload.put("document_url", docDownloadUrl);
            rawPayload.put("document_filename", docFilename);
            rawPayload.put("advertised_size", advertisedSize);
            rawPayload.put("product_type", rawProductType);

            IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                    .id(stableId)
                    .sourceUin(rawUin)
                    .normalizedUin(normUin)
                    .insurerName(rawInsurerName)
                    .normalizedInsurerCode(normInsurer)
                    .productName(rawProductName)
                    .financialYear(rawFinancialYear)
                    .approvalDate(rawApprovalDate)
                    .rawProductType(rawProductType)
                    .normalizedCategory(rawProductType.isBlank() ? "UNSPECIFIED" : rawProductType.toUpperCase())
                    .archiveStatus(rawArchiveStatus)
                    .archived(isArchived)
                    .documentDownloadUrl(docDownloadUrl)
                    .documentFilename(docFilename)
                    .advertisedDocumentSize(advertisedSize)
                    .sourceListingUrl(pageUrl)
                    .sourcePage(pageNumber)
                    .sourceRowIndex(rowIndex)
                    .firstSeenAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .lastInventoryRunId(runId)
                    .classificationStatus(classification)
                    .duplicateStatus("UNIQUE")
                    .rawSourcePayload(rawPayload)
                    .build();

            records.add(record);
        }

        return records;
    }

    /**
     * Saves or updates an IrdaiRegistryRecord idempotently with conflict and duplicate detection.
     */
    public synchronized SaveOutcome saveRecordIdempotently(IrdaiRegistryRecord record) {
        String uin = record.getNormalizedUin();

        // 1. If UIN is present, check existing records by UIN
        if (uin != null && !uin.isBlank()) {
            List<IrdaiRegistryRecord> existingWithSameUin = registryRepository.findAllByNormalizedUin(uin);

            if (!existingWithSameUin.isEmpty()) {
                // Check if identical match or conflict
                for (IrdaiRegistryRecord existing : existingWithSameUin) {
                    boolean sameInsurer = isFuzzyMatch(existing.getInsurerName(), record.getInsurerName());
                    boolean sameProduct = isFuzzyMatch(existing.getProductName(), record.getProductName());

                    if (sameInsurer && sameProduct) {
                        // Identical product duplicate observation -> update timestamps and occurrence count
                        existing.setLastSeenAt(Instant.now());
                        existing.setLastInventoryRunId(record.getLastInventoryRunId());
                        existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
                        existing.setDuplicateStatus("DUPLICATE_IDENTICAL");
                        if (record.getDocumentDownloadUrl() != null && !record.getDocumentDownloadUrl().isBlank()) {
                            existing.setDocumentDownloadUrl(record.getDocumentDownloadUrl());
                            existing.setDocumentFilename(record.getDocumentFilename());
                            existing.setAdvertisedDocumentSize(record.getAdvertisedDocumentSize());
                        }
                        existing.setUpdatedAt(Instant.now());
                        registryRepository.save(existing);
                        return SaveOutcome.DUPLICATE;
                    } else {
                        // Conflicting record with different product or insurer for same UIN!
                        // Preserve conflict explicitly in both records
                        String conflictMsg = String.format("Conflict for UIN %s: Existing ('%s', '%s') vs New ('%s', '%s')",
                                uin, existing.getInsurerName(), existing.getProductName(),
                                record.getInsurerName(), record.getProductName());

                        existing.setDuplicateStatus("CONFLICT_DETECTED");
                        if (!existing.getConflictDetails().contains(conflictMsg)) {
                            existing.getConflictDetails().add(conflictMsg);
                        }
                        registryRepository.save(existing);

                        // Save new record with conflict flag and distinct conflict ID
                        record.setDuplicateStatus("CONFLICT_DETECTED");
                        record.getConflictDetails().add(conflictMsg);
                        record.setId(record.getId() + "_CONF_" + Math.abs((record.getInsurerName() + "_" + record.getProductName()).hashCode()));
                        record.setCreatedAt(Instant.now());
                        record.setUpdatedAt(Instant.now());
                        registryRepository.save(record);
                        return SaveOutcome.CONFLICT;
                    }
                }
            }
        }

        // 2. Direct ID lookup for updates or brand new inserts
        Optional<IrdaiRegistryRecord> existingById = registryRepository.findById(record.getId());
        if (existingById.isPresent()) {
            IrdaiRegistryRecord existing = existingById.get();
            existing.setLastSeenAt(Instant.now());
            existing.setLastInventoryRunId(record.getLastInventoryRunId());
            existing.setSourcePage(record.getSourcePage());
            existing.setSourceRowIndex(record.getSourceRowIndex());
            existing.setDocumentDownloadUrl(record.getDocumentDownloadUrl());
            existing.setDocumentFilename(record.getDocumentFilename());
            existing.setAdvertisedDocumentSize(record.getAdvertisedDocumentSize());
            existing.setClassificationStatus(record.getClassificationStatus());
            existing.setUpdatedAt(Instant.now());
            registryRepository.save(existing);
            return SaveOutcome.UPDATED;
        }

        // 3. New record
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
        record.setUpdatedAt(Instant.now());
        registryRepository.save(record);
        return SaveOutcome.CREATED;
    }

    /**
     * Aggregates complete inventory summary and distribution statistics.
     */
    public InventorySummaryDto getInventorySummary() {
        long total = registryRepository.count();
        long archived = registryRepository.countByArchived(true);
        long nonArchived = registryRepository.countByArchived(false);
        long duplicates = registryRepository.countByDuplicateStatus("DUPLICATE_IDENTICAL");
        long conflicts = registryRepository.countByDuplicateStatus("CONFLICT_DETECTED");
        long missingUins = registryRepository.countMissingUins();
        long missingDocs = registryRepository.countMissingDocumentLinks();

        // Calculate unique UINs count
        long uniqueUins = Math.max(0, total - duplicates - conflicts);

        // Classification distribution
        Map<String, Long> classificationDist = new HashMap<>();
        List<String> categories = List.of(
                "INDIVIDUAL", "FAMILY_FLOATER", "GROUP", "PERSONAL_ACCIDENT",
                "TRAVEL", "BENEFIT_BASED", "RETAIL", "RIDER_ADDON", "ARCHIVED", "UNKNOWN_UNCLASSIFIED"
        );
        for (String cat : categories) {
            classificationDist.put(cat, registryRepository.countByClassificationStatus(cat));
        }

        // Financial Year and Insurer distributions via MongoDB query
        Map<String, Long> fyDist = new HashMap<>();
        Map<String, Long> insurerDist = new HashMap<>();
        List<IrdaiRegistryRecord> allRecords = registryRepository.findAll();
        for (IrdaiRegistryRecord r : allRecords) {
            String fy = (r.getFinancialYear() != null && !r.getFinancialYear().isBlank()) ? r.getFinancialYear() : "UNSPECIFIED";
            fyDist.put(fy, fyDist.getOrDefault(fy, 0L) + 1);

            String ins = (r.getNormalizedInsurerCode() != null && !r.getNormalizedInsurerCode().isBlank()) ? r.getNormalizedInsurerCode() : "UNKNOWN";
            insurerDist.put(ins, insurerDist.getOrDefault(ins, 0L) + 1);
        }

        // Get last discovery run metadata
        Optional<PolicyDiscoveryRun> lastRun = runRepository.findTopByOrderByStartedAtDesc();
        String runId = lastRun.map(PolicyDiscoveryRun::getId).orElse("NONE");
        Instant runTime = lastRun.map(PolicyDiscoveryRun::getCompletedAt).orElse(null);
        String runStatus = lastRun.map(PolicyDiscoveryRun::getStatus).orElse("NO_RUN");

        return InventorySummaryDto.builder()
                .totalRecords(total)
                .uniqueUins(uniqueUins)
                .nonArchivedCount(nonArchived)
                .archivedCount(archived)
                .duplicateCount(duplicates)
                .conflictCount(conflicts)
                .missingUinCount(missingUins)
                .missingDocumentLinkCount(missingDocs)
                .classificationDistribution(classificationDist)
                .insurerDistribution(insurerDist)
                .financialYearDistribution(fyDist)
                .lastRunId(runId)
                .lastRunCompletedAt(runTime)
                .lastRunStatus(runStatus)
                .build();
    }

    /**
     * Paginated search and filtering query over the IRDAI registry inventory.
     */
    public Page<IrdaiRegistryRecord> searchRegistry(String keyword, String uin, String insurer, String productName,
                                                   String financialYear, Boolean archived, String classificationStatus,
                                                   Pageable pageable) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("normalizedUin").regex(keyword, "i"),
                    Criteria.where("sourceUin").regex(keyword, "i"),
                    Criteria.where("productName").regex(keyword, "i"),
                    Criteria.where("insurerName").regex(keyword, "i")
            ));
        }

        if (uin != null && !uin.isBlank()) {
            criteriaList.add(Criteria.where("normalizedUin").regex(uin.trim(), "i"));
        }

        if (insurer != null && !insurer.isBlank()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("normalizedInsurerCode").regex(insurer.trim(), "i"),
                    Criteria.where("insurerName").regex(insurer.trim(), "i")
            ));
        }

        if (productName != null && !productName.isBlank()) {
            criteriaList.add(Criteria.where("productName").regex(productName.trim(), "i"));
        }

        if (financialYear != null && !financialYear.isBlank()) {
            criteriaList.add(Criteria.where("financialYear").is(financialYear.trim()));
        }

        if (archived != null) {
            criteriaList.add(Criteria.where("archived").is(archived));
        }

        if (classificationStatus != null && !classificationStatus.isBlank()) {
            criteriaList.add(Criteria.where("classificationStatus").is(classificationStatus.trim()));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, IrdaiRegistryRecord.class);
        query.with(pageable);
        List<IrdaiRegistryRecord> list = mongoTemplate.find(query, IrdaiRegistryRecord.class);

        return new PageImpl<>(list, pageable, total);
    }

    // Helper methods for classification and normalisation

    public String deriveClassification(String productType, String productName, String uin, boolean isArchived) {
        if (isArchived) return "ARCHIVED";

        String pType = productType != null ? productType.toLowerCase() : "";
        String pName = productName != null ? productName.toLowerCase() : "";
        String uinLower = uin != null ? uin.toLowerCase() : "";

        // Rider / Add-on
        if (pType.contains("add on") || pType.contains("add-on") || pType.contains("rider") ||
                pName.contains("add-on") || pName.contains("add on") || pName.contains("rider") || pName.contains("endorsement")) {
            return "RIDER_ADDON";
        }

        // Personal Accident
        if (pName.contains("personal accident") || pName.contains("accident") || pName.contains("janata personal") ||
                uinLower.contains("p-p") || uinLower.contains("pagp") || uinLower.contains("paip") || uinLower.contains("gpap")) {
            return "PERSONAL_ACCIDENT";
        }

        // Travel
        if (pName.contains("travel") || pName.contains("overseas") || pName.contains("pravasi") ||
                uinLower.contains("p-t") || uinLower.contains("tgdp") || uinLower.contains("tip")) {
            return "TRAVEL";
        }

        // Benefit-based / Fixed benefit / Critical Illness / Daily Cash
        if (pName.contains("critical illness") || pName.contains("hospital cash") || pName.contains("daily cash") ||
                pName.contains("benefit") || pName.contains("cancer") || pName.contains("criti")) {
            return "BENEFIT_BASED";
        }

        // Group health
        if (pType.contains("group") || pName.contains("group") || pName.contains("gmc") || pName.contains("corporate") ||
                uinLower.contains("hlgp") || uinLower.contains("group")) {
            return "GROUP";
        }

        // Family / Floater
        if (pName.contains("floater") || pName.contains("family") || pName.contains("medicare")) {
            return "FAMILY_FLOATER";
        }

        // Retail
        if (pType.contains("retail") || pName.contains("retail")) {
            return "RETAIL";
        }

        // Individual health
        if (pType.contains("individual") || pName.contains("individual") || uinLower.contains("hlip")) {
            return "INDIVIDUAL";
        }

        return "UNKNOWN_UNCLASSIFIED";
    }

    public String normalizeUin(String rawUin) {
        if (rawUin == null) return "";
        return rawUin.trim().replaceAll("\\s+", "").toUpperCase();
    }

    public String deriveInsurerCode(String insurerName, String uin) {
        if (uin != null && uin.length() >= 3) {
            String prefix = uin.substring(0, 3).toUpperCase();
            if (List.of("HDF", "SHA", "CHI", "NBH", "MAX", "ADI", "ICI", "BAJ", "BAG", "TAT", "MCI", "NIA", "UII", "NIC", "OIC").contains(prefix)) {
                return mapPrefixToInsurerCode(prefix);
            }
        }

        if (insurerName == null) return "UNKNOWN";
        String lower = insurerName.toLowerCase();
        if (lower.contains("hdfc")) return "HDFC_ERGO";
        if (lower.contains("star")) return "STAR_HEALTH";
        if (lower.contains("care") || lower.contains("religare")) return "CARE_HEALTH";
        if (lower.contains("niva") || lower.contains("max bupa")) return "NIVA_BUPA";
        if (lower.contains("aditya")) return "ADITYA_BIRLA";
        if (lower.contains("icici")) return "ICICI_LOMBARD";
        if (lower.contains("bajaj")) return "BAJAJ_ALLIANZ";
        if (lower.contains("tata")) return "TATA_AIG";
        if (lower.contains("manipal") || lower.contains("cigna")) return "MANIPAL_CIGNA";
        if (lower.contains("new india")) return "NEW_INDIA";
        if (lower.contains("united india")) return "UNITED_INDIA";
        if (lower.contains("national insurance")) return "NATIONAL_INSURANCE";
        if (lower.contains("oriental")) return "ORIENTAL_INSURANCE";
        if (lower.contains("cholamandalam") || lower.contains("chola")) return "CHOLAMANDALAM";
        if (lower.contains("royal sundaram")) return "ROYAL_SUNDARAM";
        if (lower.contains("reliance")) return "RELIANCE_GENERAL";
        if (lower.contains("sbi")) return "SBI_GENERAL";
        if (lower.contains("universal sompo")) return "UNIVERSAL_SOMPO";
        if (lower.contains("future generali")) return "FUTURE_GENERALI";
        if (lower.contains("liberty")) return "LIBERTY_GENERAL";
        if (lower.contains("magma")) return "MAGMA_HDI";
        if (lower.contains("raheja")) return "RAHEJA_QBE";
        if (lower.contains("shriram")) return "SHRIRAM_GENERAL";
        if (lower.contains("navi")) return "NAVI_GENERAL";
        if (lower.contains("go digit") || lower.contains("digit")) return "GODIGIT";
        if (lower.contains("acko")) return "ACKO";
        if (lower.contains("apollo munich")) return "APOLLO_MUNICH";

        return "OTHER_INSURER";
    }

    private String mapPrefixToInsurerCode(String prefix) {
        return switch (prefix) {
            case "HDF" -> "HDFC_ERGO";
            case "SHA" -> "STAR_HEALTH";
            case "CHI" -> "CARE_HEALTH";
            case "NBH", "MAX" -> "NIVA_BUPA";
            case "ADI" -> "ADITYA_BIRLA";
            case "ICI" -> "ICICI_LOMBARD";
            case "BAJ", "BAG" -> "BAJAJ_ALLIANZ";
            case "TAT" -> "TATA_AIG";
            case "MCI" -> "MANIPAL_CIGNA";
            case "NIA" -> "NEW_INDIA";
            case "UII" -> "UNITED_INDIA";
            case "NIC" -> "NATIONAL_INSURANCE";
            case "OIC" -> "ORIENTAL_INSURANCE";
            default -> "INSURER_" + prefix;
        };
    }

    public String generateStableRecordId(String normalizedUin, String insurer, String product, String docFilename) {
        if (normalizedUin != null && !normalizedUin.isBlank()) {
            return "IRDAI_" + normalizedUin;
        }
        int hash = Math.abs((insurer + "_" + product + "_" + docFilename).hashCode());
        return "IRDAI_NO_UIN_" + hash;
    }

    private String buildPageUrl(String baseUrl, int page, int delta) {
        return String.format("%s?p_p_id=%s&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&_%s_delta=%d&_%s_resetCur=false&_%s_cur=%d",
                baseUrl, PORTLET_ID, PORTLET_ID, delta, PORTLET_ID, PORTLET_ID, page);
    }

    private String fetchUrlWithBackoff(String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return response.body();
                } else if (status == 429 || status >= 500) {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("[IRDAI-Registry] HTTP {} on {}. Retrying in {} ms (attempt {}/{})", status, url, backoffMs, attempt, maxRetries);
                    Thread.sleep(backoffMs);
                } else {
                    log.warn("[IRDAI-Registry] HTTP {} unexpected on {}", status, url);
                    return null;
                }
            } catch (Exception e) {
                log.warn("[IRDAI-Registry] Network exception on {}: {}. Attempt {}/{}", url, e.getMessage(), attempt, maxRetries);
                try {
                    Thread.sleep(1500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private int extractTotalResults(String html) {
        Matcher m = TOTAL_RESULTS_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1).replace(",", "").trim());
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private String cleanHtml(String raw) {
        if (raw == null) return "";
        return TAG_STRIP_PATTERN.matcher(raw).replaceAll("").replace("&nbsp;", " ").trim();
    }

    private String extractHref(String html) {
        Matcher m = LINK_HREF_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1).replace("&amp;", "&").trim();
        }
        return "";
    }

    private String extractLinkText(String html) {
        Matcher m = LINK_HREF_PATTERN.matcher(html);
        if (m.find()) {
            return cleanHtml(m.group(2));
        }
        return "";
    }

    private String extractParagraphText(String html) {
        Matcher m = PARAGRAPH_PATTERN.matcher(html);
        if (m.find()) {
            return cleanHtml(m.group(1));
        }
        return "";
    }

    private boolean isFuzzyMatch(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        String na = a.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        String nb = b.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    private PolicyDiscoveryRun initializeRunRecord(boolean resumeIfPossible) {
        if (resumeIfPossible) {
            Optional<PolicyDiscoveryRun> activeRun = runRepository.findTopByOrderByStartedAtDesc();
            if (activeRun.isPresent() && ("IN_PROGRESS".equals(activeRun.get().getStatus()) || "PARTIAL_SUCCESS".equals(activeRun.get().getStatus()))) {
                return activeRun.get();
            }
        }

        PolicyDiscoveryRun run = PolicyDiscoveryRun.builder()
                .triggerType("IRDAI_PORTAL_REGISTRY")
                .startedAt(Instant.now())
                .status("IN_PROGRESS")
                .sourcesScanned(0)
                .candidatesDiscovered(0)
                .candidatesNew(0)
                .candidatesDuplicate(0)
                .failuresCount(0)
                .perSourceSummary(new HashMap<>())
                .logMessages(new ArrayList<>(List.of("Initialized IRDAI registry inventory run")))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return runRepository.save(run);
    }

    public enum SaveOutcome {
        CREATED,
        UPDATED,
        DUPLICATE,
        CONFLICT
    }
}
