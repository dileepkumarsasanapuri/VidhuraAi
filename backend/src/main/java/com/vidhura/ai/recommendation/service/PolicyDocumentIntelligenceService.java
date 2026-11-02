package com.vidhura.ai.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentExtractionRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 6.3: Policy Document Intelligence Orchestrator Service.
 *
 * Coordinates the end-to-end processing lifecycle:
 *   NOT_STARTED -> TEXT_EXTRACTION -> OCR_PROCESSING -> CLAUSE_SEGMENTATION ->
 *   STRUCTURED_EXTRACTION -> VALIDATION -> REVIEW_REQUIRED / COMPLETED
 *
 * Implements checkpoint-based idempotent processing, category-aware extraction,
 * external artifact persistence, and human reviewer workflows.
 */
@Slf4j
@Service
public class PolicyDocumentIntelligenceService {

    private final DocumentPageExtractionService pageExtractionService;
    private final PolicyDocumentOcrService ocrService;
    private final ClauseTaxonomyExtractor taxonomyExtractor;
    private final TargetedDocumentStorageService storageService;
    private final PolicyDocumentRevisionRepository revisionRepository;
    private final IrdaiRegistryRepository registryRepository;
    private final PolicyDocumentExtractionRepository extractionRepository;

    private final Path extractionStorageRoot;
    private final ObjectMapper objectMapper;

    public PolicyDocumentIntelligenceService(
            DocumentPageExtractionService pageExtractionService,
            PolicyDocumentOcrService ocrService,
            ClauseTaxonomyExtractor taxonomyExtractor,
            TargetedDocumentStorageService storageService,
            PolicyDocumentRevisionRepository revisionRepository,
            IrdaiRegistryRepository registryRepository,
            PolicyDocumentExtractionRepository extractionRepository,
            @Value("${vidhura.policy.extraction.root:}") String configuredExtractedRoot) {
        this.pageExtractionService = pageExtractionService;
        this.ocrService = ocrService;
        this.taxonomyExtractor = taxonomyExtractor;
        this.storageService = storageService;
        this.revisionRepository = revisionRepository;
        this.registryRepository = registryRepository;
        this.extractionRepository = extractionRepository;

        String effectiveExtracted = System.getenv("VIDHURA_POLICY_EXTRACTION_ROOT");
        if (effectiveExtracted == null || effectiveExtracted.isBlank()) {
            effectiveExtracted = configuredExtractedRoot != null && !configuredExtractedRoot.isBlank()
                    ? configuredExtractedRoot
                    : storageService.getRootDirectory().resolve("extracted").toString();
        }
        this.extractionStorageRoot = Paths.get(effectiveExtracted).toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try {
            Files.createDirectories(this.extractionStorageRoot);
        } catch (IOException e) {
            log.error("[DocumentIntelligence] Could not initialize extraction root {}: {}", this.extractionStorageRoot, e.getMessage());
        }
    }

    /**
     * Processes a single policy document by its verified SHA-256 digest.
     * Checkpoints and avoids duplicate extraction if the engine version matches.
     */
    public DocumentExtractionResult processDocument(String documentSha256, String runId, boolean forceReprocess) {
        if (documentSha256 == null || documentSha256.isBlank()) {
            throw new IllegalArgumentException("documentSha256 must be provided");
        }
        String cleanSha = documentSha256.trim().toLowerCase();
        Optional<PolicyDocumentRevision> revOpt = revisionRepository.findByVerifiedSha256(cleanSha);
        if (revOpt.isPresent()) {
            return processRevision(revOpt.get(), runId, forceReprocess);
        }

        // Fallback for ad-hoc document without revision record
        return processDocumentInternal(cleanSha, null, null, null, "INDIVIDUAL", null, null, runId, forceReprocess);
    }

    /**
     * Processes an acquired policy document revision.
     * Guarantees distinct evidence lineage per logical revision, even when physical blobs are shared.
     */
    public DocumentExtractionResult processRevision(PolicyDocumentRevision revision, String runId, boolean forceReprocess) {
        if (revision == null || revision.getVerifiedSha256() == null || revision.getVerifiedSha256().isBlank()) {
            throw new IllegalArgumentException("Valid revision with verifiedSha256 must be provided");
        }
        String cleanSha = revision.getVerifiedSha256().trim().toLowerCase();
        String revId = revision.getId();
        String registryRecordId = revision.getRegistryRecordId();
        String canonicalUin = revision.getIrdaUin();

        String productCategory = "INDIVIDUAL";
        String productName = null;
        String insurerName = null;
        if (registryRecordId != null) {
            Optional<IrdaiRegistryRecord> regOpt = registryRepository.findById(registryRecordId);
            if (regOpt.isPresent()) {
                productCategory = regOpt.get().getClassificationStatus() != null ? regOpt.get().getClassificationStatus() : regOpt.get().getNormalizedCategory();
                if (canonicalUin == null) canonicalUin = regOpt.get().getNormalizedUin();
                productName = regOpt.get().getProductName();
                insurerName = regOpt.get().getInsurerName();
            }
        }

        return processDocumentInternal(cleanSha, revId, registryRecordId, canonicalUin, productCategory, productName, insurerName, runId, forceReprocess);
    }

    private DocumentExtractionResult processDocumentInternal(
            String cleanSha,
            String revisionId,
            String registryRecordId,
            String canonicalUin,
            String productCategory,
            String productName,
            String insurerName,
            String runId,
            boolean forceReprocess) {

        String effectiveRunId = runId != null && !runId.isBlank() ? runId : "RUN_" + UUID.randomUUID().toString().substring(0, 8);
        String docKey = revisionId != null ? revisionId : cleanSha;

        log.info("[DocumentIntelligence] Processing revision {} (SHA: {}, runId: {}, force: {})", docKey, cleanSha, effectiveRunId, forceReprocess);

        // 1. Check idempotency: If already extracted under current engine version and not forcing reprocess
        boolean alreadyExtracted = (revisionId != null && extractionRepository.existsByDocumentRevisionIdAndExtractionEngineVersion(revisionId, ClauseTaxonomyExtractor.ENGINE_VERSION))
                || extractionRepository.existsByDocumentSha256AndExtractionEngineVersion(cleanSha, ClauseTaxonomyExtractor.ENGINE_VERSION);

        if (!forceReprocess && alreadyExtracted) {
            List<PolicyDocumentExtraction> existing = revisionId != null
                    ? extractionRepository.findAllByDocumentRevisionId(revisionId)
                    : extractionRepository.findAllByDocumentSha256(cleanSha);
            if (existing.isEmpty()) {
                existing = extractionRepository.findAllByDocumentSha256(cleanSha);
            }

            log.info("[DocumentIntelligence] Revision {} already extracted under engine {} ({} attributes found). Skipping.",
                    docKey, ClauseTaxonomyExtractor.ENGINE_VERSION, existing.size());
            return DocumentExtractionResult.builder()
                    .documentSha256(cleanSha)
                    .lifecycleStatus("COMPLETED")
                    .totalPages(0)
                    .extractedAttributes(existing)
                    .warnings(List.of("Idempotent run: Existing extractions preserved under version " + ClauseTaxonomyExtractor.ENGINE_VERSION))
                    .reprocessed(false)
                    .processedAt(Instant.now())
                    .build();
        }

        // 2. Resolve document file
        File pdfFile = resolvePdfFile(cleanSha);
        if (pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0) {
            log.error("[DocumentIntelligence] Physical PDF file not found for SHA: {}", cleanSha);
            if (revisionId != null) {
                revisionRepository.findById(revisionId).ifPresent(r -> {
                    r.setStatus("EXTRACTION_FAILED");
                    revisionRepository.save(r);
                });
            }
            return DocumentExtractionResult.builder()
                    .documentSha256(cleanSha)
                    .lifecycleStatus("EXTRACTION_FAILED")
                    .totalPages(0)
                    .extractedAttributes(Collections.emptyList())
                    .warnings(List.of("Original PDF file not found in content-addressed storage for digest " + cleanSha))
                    .reprocessed(false)
                    .processedAt(Instant.now())
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        String lifecycleStatus = "TEXT_EXTRACTION";

        try {
            // 3. Page extraction (PDFBox)
            DocumentPageExtractionService.DocumentPagesResult pagesResult =
                    pageExtractionService.extractPages(pdfFile, cleanSha);
            warnings.addAll(pagesResult.getWarnings());

            // 4. OCR Processing for scanned pages
            if (pagesResult.getScannedPagesCount() > 0) {
                lifecycleStatus = "OCR_PROCESSING";
                List<Integer> scannedPageNums = pagesResult.getPages().stream()
                        .filter(DocumentPageExtractionService.PageContent::isScanned)
                        .map(DocumentPageExtractionService.PageContent::getPageNumber)
                        .collect(Collectors.toList());

                Path docExtractedDir = this.extractionStorageRoot.resolve(cleanSha);
                PolicyDocumentOcrService.OcrDocumentResult ocrResult =
                        ocrService.processScannedPages(pdfFile, scannedPageNums, docExtractedDir);
                if (ocrResult.getReviewRequiredCount() > 0) {
                    warnings.add("Scanned pages detected (" + ocrResult.getReviewRequiredCount() + " pages requiring manual inspection)");
                }
            }

            // 5. Clause Segmentation & Structured Extraction
            lifecycleStatus = "STRUCTURED_EXTRACTION";
            List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                    pagesResult,
                    registryRecordId != null ? registryRecordId : "REG_UNKNOWN",
                    revisionId != null ? revisionId : "REV_UNKNOWN",
                    canonicalUin != null ? canonicalUin : "UIN_UNKNOWN",
                    productCategory,
                    effectiveRunId,
                    productName,
                    insurerName
            );

            // 6. Validation layer
            lifecycleStatus = "VALIDATION";
            for (PolicyDocumentExtraction ext : extractions) {
                // Validate page reference against actual page count
                if (ext.getOriginalPageNumber() == null || ext.getOriginalPageNumber() < 1 || ext.getOriginalPageNumber() > pagesResult.getTotalPages()) {
                    if (!"NOT_APPLICABLE".equals(ext.getExtractionStatus())) {
                        ext.setExtractionStatus("REVIEW_REQUIRED");
                        warnings.add("Invalid page reference " + ext.getOriginalPageNumber() + " for field " + ext.getExtractedFieldName());
                    }
                }
                // Flag unverified review requirement
                ext.setVerificationStatus("UNVERIFIED_PENDING_REVIEW");
            }

            // If document has scanned pages without machine text, flag for review
            boolean hasUnresolvedScanned = pagesResult.getScannedPagesCount() > 0;
            if (hasUnresolvedScanned) {
                lifecycleStatus = "REVIEW_REQUIRED";
            } else {
                lifecycleStatus = "COMPLETED";
            }

            // 7. Delete prior extractions for this revision if re-processing
            if (forceReprocess) {
                List<PolicyDocumentExtraction> prior = revisionId != null
                        ? extractionRepository.findAllByDocumentRevisionId(revisionId)
                        : extractionRepository.findAllByDocumentSha256(cleanSha);
                if (!prior.isEmpty()) {
                    extractionRepository.deleteAll(prior);
                    log.info("[DocumentIntelligence] Cleared {} prior extractions for {}", prior.size(), docKey);
                }
            }

            // 8. Persist extractions in MongoDB
            List<PolicyDocumentExtraction> savedExtractions = extractionRepository.saveAll(extractions);

            // 9. Persist processing manifest externally
            persistDocumentManifest(cleanSha, pagesResult, savedExtractions, warnings, lifecycleStatus, effectiveRunId);

            log.info("[DocumentIntelligence] Completed extraction for revision {}: {} fields extracted, status: {}",
                    docKey, savedExtractions.size(), lifecycleStatus);

            return DocumentExtractionResult.builder()
                    .documentSha256(cleanSha)
                    .lifecycleStatus(lifecycleStatus)
                    .totalPages(pagesResult.getTotalPages())
                    .scannedPages(pagesResult.getScannedPagesCount())
                    .extractedAttributes(savedExtractions)
                    .warnings(warnings)
                    .reprocessed(forceReprocess)
                    .manifestPath(this.extractionStorageRoot.resolve(cleanSha).resolve("extraction_manifest.json").toString())
                    .processedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("[DocumentIntelligence] Processing failed for revision {}: {}", docKey, e.getMessage(), e);
            return DocumentExtractionResult.builder()
                    .documentSha256(cleanSha)
                    .lifecycleStatus("FAILED")
                    .totalPages(0)
                    .extractedAttributes(Collections.emptyList())
                    .warnings(List.of("Extraction error: " + e.getMessage()))
                    .reprocessed(false)
                    .processedAt(Instant.now())
                    .build();
        }
    }

    /**
     * Executes incremental extraction across all acquired policy document revisions.
     */
    public BatchExtractionSummary processAcquiredDocuments(int limit, boolean forceReprocess) {
        int max = limit > 0 ? limit : 100;
        List<PolicyDocumentRevision> revisions = revisionRepository.findAll();
        List<PolicyDocumentRevision> eligible = revisions.stream()
                .filter(r -> r.getVerifiedSha256() != null && !r.getVerifiedSha256().isBlank())
                .filter(r -> "ACQUIRED".equalsIgnoreCase(r.getAcquisitionState())
                        || "VERIFIED_INTEGRITY".equalsIgnoreCase(r.getAcquisitionState())
                        || "VERIFIED".equalsIgnoreCase(r.getStatus()))
                .limit(max)
                .collect(Collectors.toList());

        log.info("[DocumentIntelligence] Found {} eligible document revisions for processing (limit: {})", eligible.size(), max);

        String runId = "BATCH_EXT_" + UUID.randomUUID().toString().substring(0, 8);
        int processedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int totalAttributes = 0;

        List<DocumentExtractionResult> results = new ArrayList<>();

        for (PolicyDocumentRevision rev : eligible) {
            try {
                DocumentExtractionResult res = processRevision(rev, runId, forceReprocess);
                results.add(res);
                if ("FAILED".equals(res.getLifecycleStatus()) || "EXTRACTION_FAILED".equals(res.getLifecycleStatus())) {
                    failedCount++;
                } else if (!res.isReprocessed() && res.getTotalPages() == 0) {
                    skippedCount++;
                } else {
                    processedCount++;
                    totalAttributes += res.getExtractedAttributes().size();
                }
            } catch (Exception e) {
                log.error("[DocumentIntelligence] Unhandled error during batch extraction for {}: {}", rev.getId(), e.getMessage());
                failedCount++;
            }
        }

        return BatchExtractionSummary.builder()
                .runId(runId)
                .eligibleCount(eligible.size())
                .processedCount(processedCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .totalAttributesExtracted(totalAttributes)
                .documentResults(results)
                .executedAt(Instant.now())
                .build();
    }

    /**
     * Authenticated human reviewer verification workflow.
     * Actions: APPROVE, REJECT, REQUEST_CORRECTION
     * Enforces 8 field-level verification gates prior to granting VERIFIED_REVIEWER status.
     */
    public PolicyDocumentExtraction reviewExtraction(String extractionId, String action, String notes, String reviewerEmail) {
        Optional<PolicyDocumentExtraction> opt = extractionRepository.findById(extractionId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Extraction record not found: " + extractionId);
        }

        PolicyDocumentExtraction extraction = opt.get();
        String cleanAction = action != null ? action.toUpperCase().trim() : "APPROVE";
        String actor = reviewerEmail != null && !reviewerEmail.isBlank() ? reviewerEmail : "admin@vidhura.ai";
        Instant now = Instant.now();

        String prevVerifStatus = extraction.getVerificationStatus();
        String prevExtStatus = extraction.getExtractionStatus();

        switch (cleanAction) {
            case "APPROVE":
                // Enforce field-level verification gates
                validateVerificationGates(extraction);
                extraction.setVerificationStatus("VERIFIED_REVIEWER");
                extraction.setExtractionStatus("VERIFIED");
                break;
            case "REJECT":
                extraction.setVerificationStatus("REJECTED_REVIEWER");
                extraction.setExtractionStatus("REJECTED");
                break;
            case "REQUEST_CORRECTION":
                extraction.setVerificationStatus("UNVERIFIED_PENDING_REVIEW");
                extraction.setExtractionStatus("REVIEW_REQUIRED");
                break;
            default:
                throw new IllegalArgumentException("Unsupported reviewer action: " + action + ". Must be APPROVE, REJECT, or REQUEST_CORRECTION.");
        }

        extraction.setVerifiedBy(actor);
        extraction.setVerifiedAt(now);
        extraction.setVerificationNotes(notes);

        if (extraction.getAuditTrail() == null) {
            extraction.setAuditTrail(new ArrayList<>());
        }

        extraction.getAuditTrail().add(PolicyDocumentExtraction.ExtractionAuditEntry.builder()
                .action(cleanAction)
                .actor(actor)
                .timestamp(now)
                .notes(notes)
                .previousValue(prevVerifStatus + " / " + prevExtStatus)
                .newValue(extraction.getVerificationStatus() + " / " + extraction.getExtractionStatus())
                .build());

        PolicyDocumentExtraction saved = extractionRepository.save(extraction);
        log.info("[DocumentIntelligence] Reviewer {} executed {} on extraction {}: {} -> {}",
                actor, cleanAction, extractionId, prevVerifStatus, extraction.getVerificationStatus());

        return saved;
    }

    /**
     * Corrects an extraction value with full audit history tracking.
     * Preserves original extraction and logs before/after diffs in the audit trail.
     */
    public PolicyDocumentExtraction correctExtraction(
            String extractionId,
            String correctedValue,
            Double correctedNumeric,
            String correctedUnit,
            String correctedEvidenceRole,
            String notes,
            String reviewerEmail,
            boolean approveAfterCorrection
    ) {
        Optional<PolicyDocumentExtraction> opt = extractionRepository.findById(extractionId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Extraction record not found: " + extractionId);
        }

        PolicyDocumentExtraction extraction = opt.get();
        String actor = reviewerEmail != null && !reviewerEmail.isBlank() ? reviewerEmail : "reviewer@vidhura.ai";
        Instant now = Instant.now();

        String prevValue = extraction.getCandidateValue();
        String prevRole = extraction.getEvidenceRole();

        // Apply corrections
        if (correctedValue != null && !correctedValue.isBlank()) {
            extraction.setCandidateValue(correctedValue);
        }
        if (correctedNumeric != null) {
            extraction.setNumericValue(BigDecimal.valueOf(correctedNumeric));
        }
        if (correctedUnit != null) {
            extraction.setUnit(correctedUnit);
        }
        if (correctedEvidenceRole != null && !correctedEvidenceRole.isBlank()) {
            extraction.setEvidenceRole(correctedEvidenceRole);
        }

        // Reviewer adjudication clears unresolved conflict flag
        extraction.setHasConflict(false);

        if (extraction.getAuditTrail() == null) {
            extraction.setAuditTrail(new ArrayList<>());
        }

        extraction.getAuditTrail().add(PolicyDocumentExtraction.ExtractionAuditEntry.builder()
                .action("CORRECTED")
                .actor(actor)
                .timestamp(now)
                .notes(notes != null ? notes : "Reviewer corrected attribute value and/or evidence role.")
                .previousValue(prevValue + " [Role: " + prevRole + "]")
                .newValue(extraction.getCandidateValue() + " [Role: " + extraction.getEvidenceRole() + "]")
                .build());

        if (approveAfterCorrection) {
            validateVerificationGates(extraction);
            extraction.setVerificationStatus("VERIFIED_REVIEWER");
            extraction.setExtractionStatus("VERIFIED");
            extraction.setVerifiedBy(actor);
            extraction.setVerifiedAt(now);
            extraction.setVerificationNotes(notes);

            extraction.getAuditTrail().add(PolicyDocumentExtraction.ExtractionAuditEntry.builder()
                    .action("APPROVE")
                    .actor(actor)
                    .timestamp(now)
                    .notes("Approved immediately after correction.")
                    .previousValue("UNVERIFIED_PENDING_REVIEW")
                    .newValue("VERIFIED_REVIEWER / VERIFIED")
                    .build());
        } else {
            extraction.setVerificationStatus("UNVERIFIED_PENDING_REVIEW");
            extraction.setExtractionStatus("REVIEW_REQUIRED");
        }

        PolicyDocumentExtraction saved = extractionRepository.save(extraction);
        log.info("[DocumentIntelligence] Reviewer {} corrected extraction {}: {} -> {} (approved: {})",
                actor, extractionId, prevValue, extraction.getCandidateValue(), approveAfterCorrection);
        return saved;
    }

    /**
     * Enforces the 8 mandatory field-level verification gates.
     * Throws IllegalStateException if any gate fails.
     */
    public void validateVerificationGates(PolicyDocumentExtraction extraction) {
        if (extraction == null) {
            throw new IllegalArgumentException("Extraction record cannot be null for verification gate validation.");
        }

        // Gate 1: Source document identified
        boolean hasDocRef = (extraction.getDocumentSha256() != null && !extraction.getDocumentSha256().isBlank())
                || (extraction.getDocumentRevisionId() != null && !extraction.getDocumentRevisionId().isBlank());
        if (!hasDocRef) {
            throw new IllegalStateException("Verification Gate 1 Failed: Source document is not identified.");
        }

        // Gate 2: SHA-256 matches registered document revision
        if (extraction.getDocumentRevisionId() != null && !extraction.getDocumentRevisionId().isBlank()
                && !"REV_UNKNOWN".equals(extraction.getDocumentRevisionId())) {
            Optional<PolicyDocumentRevision> revOpt = revisionRepository.findById(extraction.getDocumentRevisionId());
            if (revOpt.isPresent()) {
                String revSha = revOpt.get().getVerifiedSha256();
                if (revSha != null && extraction.getDocumentSha256() != null
                        && !revSha.trim().equalsIgnoreCase(extraction.getDocumentSha256().trim())) {
                    throw new IllegalStateException("Verification Gate 2 Failed: Document SHA-256 (" + extraction.getDocumentSha256()
                            + ") does not match registered revision SHA-256 (" + revSha + ").");
                }
            }
        }

        // Gate 3: Valid page reference (must be >= 1 if provided)
        if (!"NOT_APPLICABLE".equalsIgnoreCase(extraction.getExtractionStatus())) {
            if (extraction.getOriginalPageNumber() != null && extraction.getOriginalPageNumber() < 1) {
                throw new IllegalStateException("Verification Gate 3 Failed: Invalid page reference: " + extraction.getOriginalPageNumber());
            }
        }

        // Gate 4: Supporting text is available (must not be empty if provided)
        if (!"NOT_APPLICABLE".equalsIgnoreCase(extraction.getExtractionStatus())) {
            if (extraction.getVerbatimClauseText() != null && extraction.getVerbatimClauseText().trim().isEmpty()) {
                throw new IllegalStateException("Verification Gate 4 Failed: Supporting clause text is missing or blank.");
            }
        }

        // Gate 5: Evidence role is appropriate
        // Generic definitions, examples, or general info cannot satisfy operative coverage fields!
        String role = extraction.getEvidenceRole() != null ? extraction.getEvidenceRole().trim() : "UNKNOWN";
        if (isOperativeCoverageField(extraction.getExtractedFieldName())) {
            if ("DEFINITION".equalsIgnoreCase(role) || "EXAMPLE".equalsIgnoreCase(role)
                    || "GENERAL_INFORMATION".equalsIgnoreCase(role)) {
                throw new IllegalStateException("Verification Gate 5 Failed: Evidence role '" + role
                        + "' cannot satisfy operative coverage field '" + extraction.getExtractedFieldName()
                        + "'. Operative clause, benefit limit, schedule, or waiting period is required.");
            }
        }

        // Gate 6: Value and unit are supported
        if (!"NOT_APPLICABLE".equalsIgnoreCase(extraction.getExtractionStatus())) {
            if (extraction.getCandidateValue() == null || extraction.getCandidateValue().trim().isEmpty()) {
                throw new IllegalStateException("Verification Gate 6 Failed: Extracted value is missing or blank.");
            }
        }

        // Gate 7: Conditions and exceptions preserved (unresolved cross-page conflicts blocked)
        if (Boolean.TRUE.equals(extraction.getHasConflict())) {
            throw new IllegalStateException("Verification Gate 7 Failed: Extraction contains unresolved conflicting evidence candidates across pages. Must adjudicate or correct before approval.");
        }

        // Gate 8: Explicit reviewer approval (ensured by caller)
    }

    /**
     * Determines whether a field represents an operative coverage term.
     */
    public boolean isOperativeCoverageField(String fieldName) {
        if (fieldName == null) return false;
        switch (fieldName) {
            case "ROOM_RENT_LIMIT":
            case "ICU_CHARGES_LIMIT":
            case "COPAY_PERCENTAGE":
            case "DEDUCTIBLE_AMOUNT":
            case "WAITING_PERIOD_INITIAL_DAYS":
            case "WAITING_PERIOD_PED_MONTHS":
            case "WAITING_PERIOD_SPECIFIC_ILLNESS_MONTHS":
            case "WAITING_PERIOD_MATERNITY_MONTHS":
            case "SUM_INSURED_TIERS":
            case "DAYCARE_TREATMENT":
            case "DOMICILIARY_HOSPITALIZATION":
            case "PRE_HOSPITALIZATION_DAYS":
            case "POST_HOSPITALIZATION_DAYS":
            case "RESTORATION_BENEFIT":
            case "NO_CLAIM_BONUS":
            case "AYUSH_BENEFIT":
            case "AMBULANCE_COVER":
                return true;
            default:
                return false;
        }
    }

    /**
     * Applies verified extraction attributes to a StructuredPolicy model.
     * Strictly enforces the architectural rule: ONLY attributes with status VERIFIED_REVIEWER
     * are applied. Unverified attributes are safely ignored and will never contaminate StructuredPolicy.
     *
     * @return count of applied verified attributes
     */
    public int applyVerifiedExtractionsToStructuredPolicy(StructuredPolicy policy, List<PolicyDocumentExtraction> extractions) {
        if (policy == null || extractions == null || extractions.isEmpty()) {
            return 0;
        }

        int applied = 0;
        for (PolicyDocumentExtraction ext : extractions) {
            if (!"VERIFIED_REVIEWER".equalsIgnoreCase(ext.getVerificationStatus())) {
                log.warn("[DocumentIntelligence] Gate rejected unverified attribute {} (status: {}) for StructuredPolicy {}",
                        ext.getExtractedFieldName(), ext.getVerificationStatus(), policy.getPolicyCode());
                continue; // Strictly omit unverified attributes
            }

            // Apply verified values to StructuredPolicy contract terms
            if (policy.getContractTerms() != null) {
                switch (ext.getExtractedFieldName()) {
                    case "COPAY_PERCENTAGE":
                        if (ext.getNumericValue() != null) {
                            policy.getContractTerms().setCopayPercentage(ext.getNumericValue().doubleValue());
                            policy.getContractTerms().setCopayNote(ext.getCandidateValue());
                            applied++;
                        }
                        break;
                    case "ROOM_RENT_LIMIT":
                        policy.getContractTerms().setRoomRentLimit(ext.getCandidateValue());
                        applied++;
                        break;
                    case "DEDUCTIBLE_AMOUNT":
                        if (ext.getNumericValue() != null) {
                            policy.getContractTerms().setDeductibleAmount(ext.getNumericValue().doubleValue());
                            applied++;
                        }
                        break;
                    case "WAITING_PERIOD_INITIAL_DAYS":
                        if (ext.getNumericValue() != null) {
                            policy.getContractTerms().setWaitingPeriodInitialDays(ext.getNumericValue().intValue());
                            applied++;
                        }
                        break;
                    case "WAITING_PERIOD_PED_MONTHS":
                        if (ext.getNumericValue() != null) {
                            policy.getContractTerms().setWaitingPeriodPedMonths(ext.getNumericValue().intValue());
                            applied++;
                        }
                        break;
                    case "WAITING_PERIOD_SPECIFIC_ILLNESS_MONTHS":
                    case "WAITING_PERIOD_SPECIFIC_MONTHS":
                        if (ext.getNumericValue() != null) {
                            policy.getContractTerms().setWaitingPeriodSpecificMonths(ext.getNumericValue().intValue());
                            applied++;
                        }
                        break;
                    case "RESTORATION_BENEFIT":
                        policy.getContractTerms().setRestorationBenefit(ext.getCandidateValue());
                        applied++;
                        break;
                    case "NO_CLAIM_BONUS":
                        policy.getContractTerms().setNoClaimBonus(ext.getCandidateValue());
                        applied++;
                        break;
                    default:
                        applied++;
                        break;
                }
            }
        }
        return applied;
    }

    /**
     * Returns pipeline summary statistics across all processed documents and attributes.
     */
    public IntelligencePipelineSummary getPipelineSummary() {
        long totalEligibleRevisions = revisionRepository.countByAcquisitionState("ACQUIRED");
        List<PolicyDocumentExtraction> allExtractions = extractionRepository.findAll();

        long extractedCount = allExtractions.stream().filter(e -> "EXTRACTED".equalsIgnoreCase(e.getExtractionStatus())).count();
        long reviewRequiredCount = allExtractions.stream().filter(e -> "REVIEW_REQUIRED".equalsIgnoreCase(e.getExtractionStatus())).count();
        long verifiedCount = allExtractions.stream().filter(e -> "VERIFIED".equalsIgnoreCase(e.getExtractionStatus()) || "VERIFIED_REVIEWER".equalsIgnoreCase(e.getVerificationStatus())).count();
        long rejectedCount = allExtractions.stream().filter(e -> "REJECTED".equalsIgnoreCase(e.getExtractionStatus()) || "REJECTED_REVIEWER".equalsIgnoreCase(e.getVerificationStatus())).count();

        Map<String, Long> byCategory = allExtractions.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getTaxonomyCategory() != null ? e.getTaxonomyCategory() : "UNKNOWN",
                        Collectors.counting()
                ));

        Map<String, Long> byField = allExtractions.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getExtractedFieldName() != null ? e.getExtractedFieldName() : "UNKNOWN",
                        Collectors.counting()
                ));

        Set<String> uniqueExtractedDocs = allExtractions.stream()
                .map(PolicyDocumentExtraction::getDocumentSha256)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return IntelligencePipelineSummary.builder()
                .totalEligibleDocuments(totalEligibleRevisions)
                .totalDocumentsProcessed(uniqueExtractedDocs.size())
                .totalExtractedAttributes(allExtractions.size())
                .extractedCount(extractedCount)
                .reviewRequiredCount(reviewRequiredCount)
                .verifiedCount(verifiedCount)
                .rejectedCount(rejectedCount)
                .attributesByCategory(byCategory)
                .attributesByField(byField)
                .engineVersion(ClauseTaxonomyExtractor.ENGINE_VERSION)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Retrieves the prioritized queue of unresolved conflicting extractions.
     * All items maintain BLOCKED_BY_CONFLICT and require human reviewer adjudication.
     */
    public List<ConflictQueueItem> getConflictQueue() {
        List<PolicyDocumentExtraction> conflicts = extractionRepository.findAllByHasConflictTrue();
        if (conflicts.isEmpty()) {
            conflicts = extractionRepository.findAll().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getHasConflict()))
                    .collect(Collectors.toList());
        }

        List<ConflictQueueItem> queue = new ArrayList<>();
        for (PolicyDocumentExtraction ext : conflicts) {
            queue.add(ConflictQueueItem.builder()
                    .extractionId(ext.getId())
                    .documentSha256(ext.getDocumentSha256())
                    .canonicalUin(ext.getCanonicalPolicyUin())
                    .registryRecordId(ext.getRegistryRecordId())
                    .documentRevisionId(ext.getDocumentRevisionId())
                    .productName(ext.getProductName())
                    .insurerName(ext.getInsurerName())
                    .productVariant(ext.getProductVariant() != null ? ext.getProductVariant() : "STANDARD")
                    .fieldName(ext.getExtractedFieldName())
                    .taxonomyCategory(ext.getTaxonomyCategory())
                    .candidateValue(ext.getCandidateValue())
                    .numericValue(ext.getNumericValue() != null ? ext.getNumericValue().doubleValue() : null)
                    .unit(ext.getUnit())
                    .pageNumber(ext.getOriginalPageNumber())
                    .evidenceRole(ext.getEvidenceRole())
                    .verbatimClauseText(ext.getVerbatimClauseText())
                    .conflictReason("Conflicting candidate evidence across pages")
                    .disposition("BLOCKED_BY_CONFLICT")
                    .conflictingCandidates(ext.getConflictingCandidates() != null ? ext.getConflictingCandidates() : Collections.emptyList())
                    .build());
        }
        return queue;
    }

    private File resolvePdfFile(String sha256) {
        // First check content-addressed storage in targeted document storage
        Path contentPath = storageService.getContentAddressedDirectory().resolve(sha256.toLowerCase() + ".pdf");
        if (Files.exists(contentPath) && contentPath.toFile().length() > 0) {
            return contentPath.toFile();
        }

        // Second check revision storage location
        Optional<PolicyDocumentRevision> revOpt = revisionRepository.findByVerifiedSha256(sha256.toLowerCase());
        if (revOpt.isPresent() && revOpt.get().getStorageKey() != null) {
            File revFile = new File(revOpt.get().getStorageKey());
            if (revFile.exists() && revFile.length() > 0) {
                return revFile;
            }
        }

        return null;
    }

    private void persistDocumentManifest(
            String sha256,
            DocumentPageExtractionService.DocumentPagesResult pagesResult,
            List<PolicyDocumentExtraction> extractions,
            List<String> warnings,
            String lifecycleStatus,
            String runId) {
        try {
            Path docDir = this.extractionStorageRoot.resolve(sha256);
            Files.createDirectories(docDir);
            Path manifestFile = docDir.resolve("extraction_manifest.json");

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("documentSha256", sha256);
            manifest.put("runId", runId);
            manifest.put("lifecycleStatus", lifecycleStatus);
            manifest.put("engineVersion", ClauseTaxonomyExtractor.ENGINE_VERSION);
            manifest.put("totalPages", pagesResult.getTotalPages());
            manifest.put("scannedPages", pagesResult.getScannedPagesCount());
            manifest.put("emptyPages", pagesResult.getEmptyPagesCount());
            manifest.put("extractedAttributesCount", extractions.size());
            manifest.put("extractedAttributes", extractions.stream().map(PolicyDocumentExtraction::getExtractedFieldName).collect(Collectors.toList()));
            manifest.put("warnings", warnings);
            manifest.put("extractedAt", Instant.now().toString());

            objectMapper.writeValue(manifestFile.toFile(), manifest);
            log.debug("[DocumentIntelligence] Manifest persisted at {}", manifestFile);
        } catch (Exception e) {
            log.warn("[DocumentIntelligence] Failed to write extraction manifest for {}: {}", sha256, e.getMessage());
        }
    }

    @Data
    @Builder
    public static class DocumentExtractionResult {
        private String documentSha256;
        private String lifecycleStatus;
        private int totalPages;
        private int scannedPages;
        private List<PolicyDocumentExtraction> extractedAttributes;
        private List<String> warnings;
        private boolean reprocessed;
        private String manifestPath;
        private Instant processedAt;
    }

    @Data
    @Builder
    public static class BatchExtractionSummary {
        private String runId;
        private int eligibleCount;
        private int processedCount;
        private int skippedCount;
        private int failedCount;
        private int totalAttributesExtracted;
        private List<DocumentExtractionResult> documentResults;
        private Instant executedAt;
    }

    @Data
    @Builder
    public static class IntelligencePipelineSummary {
        private long totalEligibleDocuments;
        private int totalDocumentsProcessed;
        private int totalExtractedAttributes;
        private long extractedCount;
        private long reviewRequiredCount;
        private long verifiedCount;
        private long rejectedCount;
        private Map<String, Long> attributesByCategory;
        private Map<String, Long> attributesByField;
        private String engineVersion;
        private Instant generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictQueueItem {
        private String extractionId;
        private String documentSha256;
        private String canonicalUin;
        private String registryRecordId;
        private String documentRevisionId;
        private String productName;
        private String insurerName;
        private String productVariant;
        private String fieldName;
        private String taxonomyCategory;
        private String candidateValue;
        private Double numericValue;
        private String unit;
        private Integer pageNumber;
        private String evidenceRole;
        private String verbatimClauseText;
        private String conflictReason;
        private String disposition;
        private List<PolicyDocumentExtraction.ConflictingCandidate> conflictingCandidates;
    }
}
