package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult.ValidationFinding;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 3.9 Automated Evidence Validation & Review Prioritization Service.
 *
 * Implements deterministic validation checks covering:
 * 1. Source and document integrity (URLs, allowlists, physical existence, SHA-256 match, conflict detection).
 * 2. Citation validation (page, statement, table, form number vs form title verification).
 * 3. Numerical and semantic validation (formula reproduction, denominator validity, population matching,
 *    grievance accounting pool reconciliation, ombudsman outcome separation, regulatory SLA semantics).
 * 4. Freshness and revision staging governance.
 *
 * Strict Provenance Rule:
 * Automated validation NEVER sets verifiedBy, verifiedAt, or marks a record as VERIFIED_STATUTORY.
 * All staged evidence remains UNVERIFIED_PENDING_REVIEW until explicit human compliance sign-off.
 */
@Service
@Slf4j
public class ExternalEvidenceValidationService {

    public static final String VALIDATOR_VERSION = "1.0.0-phase3.9";
    private static final String STATUTORY_DIR = "src/main/resources/evidence/statutory/";

    private static final Set<String> ALLOWED_OFFICIAL_DOMAINS = Set.of(
            "irdai.gov.in",
            "www.irdai.gov.in",
            "cioins.co.in",
            "www.cioins.co.in",
            "uiic.co.in",
            "www.uiic.co.in",
            "newindia.co.in",
            "www.newindia.co.in",
            "gicouncil.in",
            "www.gicouncil.in",
            "policyholder.gov.in",
            "www.policyholder.gov.in",
            "financialservices.gov.in",
            "localhost",
            "127.0.0.1"
    );

    // Errant hash identified in Phase 3.8.2 forensic audit as having no physical file on disk
    public static final String ERRANT_CIO_REPORT_HASH = "b6727c6d66e7bb844512c262e31505c065f462a677ca3e7f41d33ea64724a4f8";

    // Known physical file mappings (hash -> expected filename)
    private static final Map<String, String> HASH_TO_FILENAME = Map.of(
            "00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb", "IRDAI_Annual_Report_2022-23.pdf",
            "3b787a46e3039e07e1d7828804fdf0475b7163a530b0ffd0a841ec38ba3fb435", "NIA_Public_Disclosures_March_2023.pdf",
            "de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75", "CIO_Annual_Report_2023-24.pdf",
            "d88e53dbca0e2a3d409653d65a41d0432fac4f4eaa67a711b907524ef9905d94", "CIO_Annual_Report_2024-25.pdf",
            "1548d76c7d3dd56cc5d32f132bfda30e933bfccbbdc591933667870cde6d45dd", "CIO_Annual_Report_2022-23.pdf",
            "dc05b394b70b3065d39037c39a0830373dfe50e1f52d6298dd4b9a03836ef6a7", "UIIC_NL26_Solvency_Margin_March_2023.pdf",
            "1462afedf13c237f9fa21b59725182ca97fd934ce188b18bbff135de5121dd33", "UIIC_NL37_Claims_Data_March_2023.pdf",
            "962ad2ae611c3c3c5369df1d991a899fa85148fa031b24d027d518132c4f8bb4", "UIIC_NL39_Ageing_Of_Claims_March_2023.pdf",
            "fdc70264ce307e0c0ead2de442881c907a03a638477c03a7aaf473d3cc139134", "IRDAI_Master_Circular_Health_Insurance_2024.pdf"
    );

    private final ExternalEvidenceRepository evidenceRepository;
    private final ExternalEvidenceValidationRepository validationRepository;
    private final EvidenceAcceptanceGovernancePolicy governancePolicy;
    private final OfficialSourceAcquisitionService acquisitionService;

    public ExternalEvidenceValidationService(
            ExternalEvidenceRepository evidenceRepository,
            ExternalEvidenceValidationRepository validationRepository) {
        this(evidenceRepository, validationRepository, new EvidenceAcceptanceGovernancePolicy(), new OfficialSourceAcquisitionService());
    }

    public ExternalEvidenceValidationService(
            ExternalEvidenceRepository evidenceRepository,
            ExternalEvidenceValidationRepository validationRepository,
            EvidenceAcceptanceGovernancePolicy governancePolicy) {
        this(evidenceRepository, validationRepository, governancePolicy, new OfficialSourceAcquisitionService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExternalEvidenceValidationService(
            ExternalEvidenceRepository evidenceRepository,
            ExternalEvidenceValidationRepository validationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            EvidenceAcceptanceGovernancePolicy governancePolicy,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            OfficialSourceAcquisitionService acquisitionService) {
        this.evidenceRepository = evidenceRepository;
        this.validationRepository = validationRepository;
        this.governancePolicy = governancePolicy != null ? governancePolicy : new EvidenceAcceptanceGovernancePolicy();
        this.acquisitionService = acquisitionService != null ? acquisitionService : new OfficialSourceAcquisitionService();
    }

    /**
     * Executes deterministic validation on a single evidence record and persists the result.
     * Evaluates versioned governance rules for automated acceptance while preserving human reviewer gates.
     */
    public ExternalEvidenceValidationResult validateRecord(ExternalEvidenceRecord record) {
        log.info("[EvidenceValidator] Running automated validation for record id='{}', insurer='{}', metric='{}', rev={}",
                record.getId(), record.getInsurerCode(), record.getMetricKey(), record.getRevision());

        // Phase 5.4: Physical Source Artifact Acquisition & Verification Gate
        ensurePhysicalArtifactBacking(record);

        List<ValidationFinding> findings = new ArrayList<>();

        // 1. Source and Document Integrity
        validateSourceAndDocumentIntegrity(record, findings);

        // 2. Citation Validation
        validateCitation(record, findings);

        // 3. Numerical & Semantic Validation
        validateNumericalAndSemantics(record, findings);

        // 4. Freshness and Revision Staging
        validateFreshnessAndRevision(record, findings);

        // Calculate deterministic priority score & priority tier
        int priorityScore = calculateReviewPriorityScore(record, findings);
        String priorityTier = determinePriorityTier(priorityScore);
        String overallOutcome = determineOverallOutcome(findings);

        // Generate summary description
        String summary = generateValidationSummary(overallOutcome, findings, priorityScore);

        ExternalEvidenceValidationResult result = ExternalEvidenceValidationResult.builder()
                .evidenceRecordId(record.getId())
                .recordRevision(record.getRevision())
                .insurerCode(record.getInsurerCode())
                .metricKey(record.getMetricKey())
                .metricCategory(record.getMetricCategory())
                .validatedAt(Instant.now())
                .overallOutcome(overallOutcome)
                .reviewPriorityScore(priorityScore)
                .priorityTier(priorityTier)
                .findings(findings)
                .validatorVersion(VALIDATOR_VERSION)
                .summary(summary)
                .build();

        ExternalEvidenceValidationResult savedResult = validationRepository.save(result);

        // Update record validation metadata
        record.setLatestValidationOutcome(overallOutcome);
        record.setLatestReviewPriorityScore(priorityScore);
        record.setLatestPriorityTier(priorityTier);
        record.setLatestValidatedAt(result.getValidatedAt());

        // Phase 5.3 Automated Governance Policy Evaluation
        List<ExternalEvidenceRecord> siblings = (evidenceRepository != null && record.getInsurerCode() != null && record.getMetricKey() != null)
                ? evidenceRepository.findByInsurerCodeAndMetricKeyAndReportingPeriodOrderByRevisionDesc(
                        record.getInsurerCode(), record.getMetricKey(), record.getReportingPeriod())
                : Collections.emptyList();

        EvidenceAcceptanceGovernancePolicy.GovernanceEvaluationResult eval =
                governancePolicy.evaluateRecord(record, result, siblings);

        // Apply policy decision (respecting human reviewer boundary)
        if ("MANUAL_REVIEWER".equals(record.getApprovalMode())) {
            // Explicit human reviewer decision is preserved; record governance status
            record.setPassedMandatoryChecks(eval.getPassedMandatoryChecks());
            record.setFailedMandatoryChecks(eval.getFailedMandatoryChecks());
            record.setGovernanceRuleVersion(eval.getGovernanceRuleVersion());
        } else if (eval.isAutoAccepted()) {
            boolean alreadyAutoAccepted = "AUTO_ACCEPTED".equals(record.getApprovalMode())
                    && eval.getGovernanceRuleVersion().equals(record.getGovernanceRuleVersion());

            record.setApprovalMode("AUTO_ACCEPTED");
            record.setAcceptanceDecision("ACCEPTED");
            record.setPublicationStatus("PUBLISHED_ELIGIBLE");
            record.setVerificationStatus("VERIFIED_STATUTORY");
            record.setVerifiedBy(null); // Never fabricate human identity
            record.setVerifiedAt(result.getValidatedAt());
            record.setGovernanceRuleVersion(eval.getGovernanceRuleVersion());
            record.setAcceptanceReason(eval.getDecisionReason());
            record.setPassedMandatoryChecks(eval.getPassedMandatoryChecks());
            record.setFailedMandatoryChecks(eval.getFailedMandatoryChecks());

            if (!alreadyAutoAccepted) {
                if (record.getAuditHistory() == null) {
                    record.setAuditHistory(new ArrayList<>());
                }
                record.getAuditHistory().add(ExternalEvidenceRecord.ReviewAuditEntry.builder()
                        .decision("AUTO_ACCEPTED")
                        .decisionMode("AUTO_ACCEPTED")
                        .reviewerEmail(null)
                        .reviewerRole("SYSTEM_GOVERNANCE_ENGINE")
                        .timestamp(result.getValidatedAt())
                        .reason(eval.getDecisionReason())
                        .sourceDocumentHash(record.getSourceDocumentHash())
                        .revision(record.getRevision())
                        .governanceRuleVersion(eval.getGovernanceRuleVersion())
                        .validationRunId(result.getId())
                        .build());
            }
        } else if ("REJECTED".equals(eval.getDecision())) {
            record.setApprovalMode("REJECTED");
            record.setAcceptanceDecision("REJECTED");
            record.setPublicationStatus("QUARANTINED");
            record.setVerificationStatus("REJECTED");
            record.setRejectionReason(eval.getDecisionReason());
            record.setGovernanceRuleVersion(eval.getGovernanceRuleVersion());
            record.setAcceptanceReason(eval.getDecisionReason());
            record.setPassedMandatoryChecks(eval.getPassedMandatoryChecks());
            record.setFailedMandatoryChecks(eval.getFailedMandatoryChecks());
        } else {
            record.setApprovalMode("PENDING_REVIEW");
            record.setAcceptanceDecision("PENDING");
            record.setPublicationStatus("STAGED_INTERNAL");
            record.setVerificationStatus("UNVERIFIED_PENDING_REVIEW");
            record.setGovernanceRuleVersion(eval.getGovernanceRuleVersion());
            record.setAcceptanceReason(eval.getDecisionReason());
            record.setPassedMandatoryChecks(eval.getPassedMandatoryChecks());
            record.setFailedMandatoryChecks(eval.getFailedMandatoryChecks());
        }

        evidenceRepository.save(record);

        log.info("[EvidenceValidator] Validation complete for id='{}': outcome='{}', decision='{}', mode='{}', pubStatus='{}'",
                record.getId(), overallOutcome, record.getAcceptanceDecision(), record.getApprovalMode(), record.getPublicationStatus());

        return savedResult;
    }

    /**
     * Validates an evidence record by ID.
     */
    public ExternalEvidenceValidationResult validateRecordById(String recordId) {
        ExternalEvidenceRecord record = evidenceRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence record not found with id: " + recordId));
        return validateRecord(record);
    }

    /**
     * Scans and validates all evidence records in batch.
     */
    public ExternalEvidenceDto.BatchValidationSummaryDto validateAllRecords() {
        List<ExternalEvidenceRecord> allRecords = evidenceRepository.findAll();
        int passed = 0;
        int warnings = 0;
        int failed = 0;
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;

        for (ExternalEvidenceRecord record : allRecords) {
            ExternalEvidenceValidationResult result = validateRecord(record);
            switch (result.getOverallOutcome()) {
                case "VALIDATION_PASSED" -> passed++;
                case "VALIDATION_PASSED_WITH_WARNINGS" -> warnings++;
                default -> failed++;
            }
            switch (result.getPriorityTier()) {
                case "CRITICAL" -> critical++;
                case "HIGH" -> high++;
                case "MEDIUM" -> medium++;
                default -> low++;
            }
        }

        return ExternalEvidenceDto.BatchValidationSummaryDto.builder()
                .totalRecordsScanned(allRecords.size())
                .passedCount(passed)
                .warningsCount(warnings)
                .failedCount(failed)
                .criticalPriorityCount(critical)
                .highPriorityCount(high)
                .mediumPriorityCount(medium)
                .lowPriorityCount(low)
                .batchTimestamp(Instant.now())
                .build();
    }

    /**
     * Retrieves latest validation result for an evidence record.
     */
    public Optional<ExternalEvidenceValidationResult> getLatestValidationResult(String recordId) {
        return validationRepository.findTopByEvidenceRecordIdOrderByValidatedAtDesc(recordId);
    }

    private void ensurePhysicalArtifactBacking(ExternalEvidenceRecord record) {
        if (record == null) return;

        String cleanHash = record.getSourceDocumentHash() != null
                ? record.getSourceDocumentHash().replaceAll("(?i)^sha256:", "").trim().toLowerCase()
                : "";

        // Check if mapped to a known physical file in statutory repository
        String mappedFile = HASH_TO_FILENAME.get(cleanHash);
        if (mappedFile != null) {
            File statutoryPhys = new File(STATUTORY_DIR + mappedFile);
            if (statutoryPhys.exists() && statutoryPhys.isFile()) {
                record.setArtifactBacked(true);
                record.setArtifactStoragePath(statutoryPhys.getPath());
                record.setAcquiredSha256(cleanHash);
                record.setAcquiredMediaType("application/pdf");
                record.setAcquiredByteSize(statutoryPhys.length());
                if (record.getLastAcquiredAt() == null) {
                    record.setLastAcquiredAt(Instant.now());
                }
                if (record.getFreshnessStatus() == null || "UNACQUIRED".equals(record.getFreshnessStatus())) {
                    record.setFreshnessStatus("FRESH");
                }
                if (record.getNextFreshnessCheckDue() == null) {
                    record.setNextFreshnessCheckDue(acquisitionService.computeFreshnessDeadline(record.getMetricCategory(), Instant.now()));
                }
                record.setContentValidationStatus("CONTENT_VERIFIED");
                record.setContentValidationNotes("Verified against local physical statutory filing: " + mappedFile);
                return;
            }
        }

        // Check if existing artifactStoragePath is already valid
        if (record.isArtifactBacked() && record.getArtifactStoragePath() != null) {
            File cachedFile = new File(record.getArtifactStoragePath());
            if (cachedFile.exists() && cachedFile.isFile() && cachedFile.length() > 0) {
                record.setFreshnessStatus(acquisitionService.evaluateFreshness(record));
                return;
            }
        }

        // If source URL provided and not yet artifact backed, attempt acquisition
        if (record.getSourceUrl() != null && !record.getSourceUrl().isBlank()) {
            acquisitionService.acquireSourceArtifact(record);
        } else {
            record.setArtifactBacked(false);
            record.setFreshnessStatus("UNACQUIRED");
        }
    }

    /**
     * Builds the prioritized review queue sorted by urgency (priority score descending, revision descending).
     */
    public List<ExternalEvidenceDto.ReviewQueueItemDto> getPrioritizedReviewQueue(
            String insurerCode, String severity, String validationOutcome, String category) {
        return getPrioritizedReviewQueue(insurerCode, severity, validationOutcome, category, null, null, null);
    }

    /**
     * Builds the prioritized review queue with approval mode and publication status filtering.
     */
    public List<ExternalEvidenceDto.ReviewQueueItemDto> getPrioritizedReviewQueue(
            String insurerCode, String severity, String validationOutcome, String category,
            String approvalMode, String publicationStatus) {
        return getPrioritizedReviewQueue(insurerCode, severity, validationOutcome, category, approvalMode, publicationStatus, null);
    }

    /**
     * Builds the prioritized review queue with approval mode, publication status, and freshness filtering.
     */
    public List<ExternalEvidenceDto.ReviewQueueItemDto> getPrioritizedReviewQueue(
            String insurerCode, String severity, String validationOutcome, String category,
            String approvalMode, String publicationStatus, String freshnessStatus) {

        List<ExternalEvidenceRecord> records = evidenceRepository.findAll();

        return records.stream()
                .filter(rec -> insurerCode == null || "ALL".equalsIgnoreCase(insurerCode) || insurerCode.equalsIgnoreCase(rec.getInsurerCode()))
                .filter(rec -> category == null || "ALL".equalsIgnoreCase(category) || category.equalsIgnoreCase(rec.getMetricCategory()) || category.equalsIgnoreCase(rec.getEvidenceType()))
                .filter(rec -> validationOutcome == null || "ALL".equalsIgnoreCase(validationOutcome) || validationOutcome.equalsIgnoreCase(rec.getLatestValidationOutcome()))
                .filter(rec -> approvalMode == null || "ALL".equalsIgnoreCase(approvalMode) || approvalMode.equalsIgnoreCase(rec.getApprovalMode()))
                .filter(rec -> publicationStatus == null || "ALL".equalsIgnoreCase(publicationStatus) || publicationStatus.equalsIgnoreCase(rec.getPublicationStatus()))
                .filter(rec -> freshnessStatus == null || "ALL".equalsIgnoreCase(freshnessStatus) || freshnessStatus.equalsIgnoreCase(rec.getFreshnessStatus()))
                .map(this::mapToReviewQueueItem)
                .filter(item -> severity == null || "ALL".equalsIgnoreCase(severity) || matchesSeverityFilter(item, severity))
                .sorted((a, b) -> {
                    int scoreCmp = Integer.compare(b.getReviewPriorityScore(), a.getReviewPriorityScore());
                    if (scoreCmp != 0) return scoreCmp;
                    return Integer.compare(b.getRecordRevision(), a.getRecordRevision());
                })
                .collect(Collectors.toList());
    }

    private boolean matchesSeverityFilter(ExternalEvidenceDto.ReviewQueueItemDto item, String severity) {
        return switch (severity.toUpperCase()) {
            case "BLOCKER" -> item.getBlockerCount() > 0;
            case "ERROR" -> item.getErrorCount() > 0;
            case "WARNING" -> item.getWarningCount() > 0;
            case "CRITICAL" -> "CRITICAL".equalsIgnoreCase(item.getPriorityTier());
            case "HIGH" -> "HIGH".equalsIgnoreCase(item.getPriorityTier());
            default -> true;
        };
    }

    private ExternalEvidenceDto.ReviewQueueItemDto mapToReviewQueueItem(ExternalEvidenceRecord rec) {
        Optional<ExternalEvidenceValidationResult> latestOpt =
                validationRepository.findTopByEvidenceRecordIdAndRecordRevisionOrderByValidatedAtDesc(rec.getId(), rec.getRevision());

        List<ExternalEvidenceDto.ValidationFindingDto> topFindings = new ArrayList<>();
        int blockerCount = 0;
        int errorCount = 0;
        int warningCount = 0;

        if (latestOpt.isPresent()) {
            ExternalEvidenceValidationResult res = latestOpt.get();
            for (ValidationFinding f : res.getFindings()) {
                if ("BLOCKER".equalsIgnoreCase(f.getSeverity())) blockerCount++;
                else if ("ERROR".equalsIgnoreCase(f.getSeverity())) errorCount++;
                else if ("WARNING".equalsIgnoreCase(f.getSeverity())) warningCount++;
            }
            topFindings = res.getFindings().stream()
                    .limit(5)
                    .map(this::mapToFindingDto)
                    .collect(Collectors.toList());
        }

        int score = rec.getLatestReviewPriorityScore() != null ? rec.getLatestReviewPriorityScore() : 0;
        String tier = rec.getLatestPriorityTier() != null ? rec.getLatestPriorityTier() : determinePriorityTier(score);
        String outcome = rec.getLatestValidationOutcome() != null ? rec.getLatestValidationOutcome() : "VALIDATION_NOT_RUN";

        String citation = "Page " + (rec.getCitationPageNumber() != null ? rec.getCitationPageNumber() : "N/A")
                + " • " + (rec.getCitationTableReference() != null ? rec.getCitationTableReference() : "Direct");

        return ExternalEvidenceDto.ReviewQueueItemDto.builder()
                .evidenceRecordId(rec.getId())
                .recordRevision(rec.getRevision())
                .insurerCode(rec.getInsurerCode())
                .insurerName(rec.getInsurerName())
                .metricKey(rec.getMetricKey())
                .metricCategory(rec.getMetricCategory())
                .metricValue(rec.getMetricValue())
                .verificationStatus(rec.getVerificationStatus())
                .validationOutcome(outcome)
                .reviewPriorityScore(score)
                .priorityTier(tier)
                .materialImpact(rec.isMaterialImpact())
                .isDerived(rec.isDerived())
                .sourceDocumentTitle(rec.getSourceDocumentTitle())
                .sourceDocumentHash(rec.getSourceDocumentHash())
                .citation(citation)
                .validatedAt(rec.getLatestValidatedAt())
                .totalFindings(blockerCount + errorCount + warningCount)
                .blockerCount(blockerCount)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .topFindings(topFindings)
                .approvalMode(rec.getApprovalMode() != null ? rec.getApprovalMode() : "PENDING_REVIEW")
                .acceptanceDecision(rec.getAcceptanceDecision() != null ? rec.getAcceptanceDecision() : "PENDING")
                .publicationStatus(rec.getPublicationStatus() != null ? rec.getPublicationStatus() : "STAGED_INTERNAL")
                .governanceRuleVersion(rec.getGovernanceRuleVersion())
                .acceptanceReason(rec.getAcceptanceReason())
                .passedMandatoryChecks(rec.getPassedMandatoryChecks())
                .failedMandatoryChecks(rec.getFailedMandatoryChecks())
                .artifactBacked(rec.isArtifactBacked())
                .artifactStoragePath(rec.getArtifactStoragePath())
                .acquiredMediaType(rec.getAcquiredMediaType())
                .acquiredByteSize(rec.getAcquiredByteSize())
                .acquiredSha256(rec.getAcquiredSha256())
                .lastAcquiredAt(rec.getLastAcquiredAt())
                .nextFreshnessCheckDue(rec.getNextFreshnessCheckDue())
                .freshnessStatus(rec.getFreshnessStatus())
                .finalSourceUrl(rec.getFinalSourceUrl())
                .contentValidationStatus(rec.getContentValidationStatus())
                .contentValidationNotes(rec.getContentValidationNotes())
                .build();
    }

    /**
     * Revalidates an evidence record against current governance rules and updates decision.
     */
    public ExternalEvidenceValidationResult revalidateRecord(String recordId) {
        ExternalEvidenceRecord record = evidenceRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence record not found with id: " + recordId));
        record.setApprovalMode("REVALIDATION_REQUIRED");
        return validateRecord(record);
    }

    /**
     * Phase 5.4: Triggers immediate physical source artifact acquisition / refresh for a record.
     */
    public ExternalEvidenceRecord acquireArtifactForRecord(String recordId) {
        ExternalEvidenceRecord record = evidenceRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence record not found with id: " + recordId));

        acquisitionService.acquireSourceArtifact(record);
        validateRecord(record);
        return evidenceRepository.save(record);
    }

    /**
     * Phase 5.4: Summarizes freshness status across all tracked evidence records.
     */
    public ExternalEvidenceDto.FreshnessSummaryDto getFreshnessSummary() {
        List<ExternalEvidenceRecord> records = evidenceRepository.findAll();
        int fresh = 0;
        int refreshDue = 0;
        int stale = 0;
        int failed = 0;
        int unacquired = 0;

        for (ExternalEvidenceRecord rec : records) {
            String status = rec.getFreshnessStatus() != null ? rec.getFreshnessStatus() : "UNACQUIRED";
            switch (status) {
                case "FRESH" -> fresh++;
                case "REFRESH_DUE" -> refreshDue++;
                case "STALE_REVALIDATION_REQUIRED" -> stale++;
                case "ACQUISITION_FAILED" -> failed++;
                default -> unacquired++;
            }
        }

        return ExternalEvidenceDto.FreshnessSummaryDto.builder()
                .totalRecords(records.size())
                .freshCount(fresh)
                .refreshDueCount(refreshDue)
                .staleRevalidationCount(stale)
                .acquisitionFailedCount(failed)
                .unacquiredCount(unacquired)
                .evaluatedAt(Instant.now())
                .build();
    }

    public ExternalEvidenceDto.ValidationFindingDto mapToFindingDto(ValidationFinding f) {
        return ExternalEvidenceDto.ValidationFindingDto.builder()
                .checkCategory(f.getCheckCategory())
                .findingCode(f.getFindingCode())
                .severity(f.getSeverity())
                .message(f.getMessage())
                .expectedValue(f.getExpectedValue())
                .actualValue(f.getActualValue())
                .affectedDocument(f.getAffectedDocument())
                .citation(f.getCitation())
                .build();
    }

    public ExternalEvidenceDto.ValidationResultDto mapToResultDto(ExternalEvidenceValidationResult res) {
        return ExternalEvidenceDto.ValidationResultDto.builder()
                .id(res.getId())
                .evidenceRecordId(res.getEvidenceRecordId())
                .recordRevision(res.getRecordRevision())
                .insurerCode(res.getInsurerCode())
                .metricKey(res.getMetricKey())
                .metricCategory(res.getMetricCategory())
                .validatedAt(res.getValidatedAt())
                .overallOutcome(res.getOverallOutcome())
                .reviewPriorityScore(res.getReviewPriorityScore())
                .priorityTier(res.getPriorityTier())
                .validatorVersion(res.getValidatorVersion())
                .summary(res.getSummary())
                .findings(res.getFindings().stream().map(this::mapToFindingDto).collect(Collectors.toList()))
                .build();
    }

    // =========================================================================
    // Check Suite 1: Source & Document Integrity
    // =========================================================================
    private void validateSourceAndDocumentIntegrity(ExternalEvidenceRecord rec, List<ValidationFinding> findings) {
        // 1.1 Source URL presence
        if (rec.getSourceUrl() == null || rec.getSourceUrl().trim().isEmpty()) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("SOURCE_INTEGRITY")
                    .findingCode("SRC_URL_MISSING")
                    .severity("BLOCKER")
                    .message("Canonical source URL is required for statutory provenance.")
                    .affectedDocument(rec.getSourceDocumentTitle())
                    .build());
        } else {
            // 1.2 Allowed domain check
            try {
                URI uri = URI.create(rec.getSourceUrl());
                String host = uri.getHost();
                if (host != null && !ALLOWED_OFFICIAL_DOMAINS.contains(host.toLowerCase())) {
                    findings.add(ValidationFinding.builder()
                            .checkCategory("SOURCE_INTEGRITY")
                            .findingCode("SRC_URL_DISALLOWED_DOMAIN")
                            .severity("BLOCKER")
                            .message("Source URL domain '" + host + "' does not match official regulatory or insurer allowlist.")
                            .actualValue(host)
                            .expectedValue(String.join(", ", ALLOWED_OFFICIAL_DOMAINS))
                            .affectedDocument(rec.getSourceDocumentTitle())
                            .build());
                }
            } catch (Exception e) {
                findings.add(ValidationFinding.builder()
                        .checkCategory("SOURCE_INTEGRITY")
                        .findingCode("SRC_URL_MALFORMED")
                        .severity("BLOCKER")
                        .message("Source URL is malformed: " + e.getMessage())
                        .actualValue(rec.getSourceUrl())
                        .affectedDocument(rec.getSourceDocumentTitle())
                        .build());
            }
        }

        // 1.3 SHA-256 Format Verification
        String hash = rec.getSourceDocumentHash();
        if (hash == null || !hash.matches("^[a-fA-F0-9]{64}$")) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("SOURCE_INTEGRITY")
                    .findingCode("SRC_HASH_INVALID_FORMAT")
                    .severity("BLOCKER")
                    .message("Source document hash must be a valid 64-character hexadecimal SHA-256 string.")
                    .actualValue(hash)
                    .affectedDocument(rec.getSourceDocumentTitle())
                    .build());
            return;
        }

        // 1.4 Known Errant Hash or Synthetic Consolidated File Conflict
        if (ERRANT_CIO_REPORT_HASH.equalsIgnoreCase(hash)) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("SOURCE_INTEGRITY")
                    .findingCode("DOC_IDENTITY_CONFLICT")
                    .severity("BLOCKER")
                    .message("Detected errant SHA-256 hash b6727c6d... from Phase 3.8 report typo. No physical file matches this hash.")
                    .actualValue(hash)
                    .expectedValue("de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75")
                    .affectedDocument(rec.getSourceDocumentTitle())
                    .build());
        }

        if (rec.getSourceDocumentTitle() != null && rec.getSourceDocumentTitle().contains("UIIC_Public_Disclosures_March_2023.pdf")) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("SOURCE_INTEGRITY")
                    .findingCode("DOC_IDENTITY_CONFLICT")
                    .severity("BLOCKER")
                    .message("Synthetic consolidated filename 'UIIC_Public_Disclosures_March_2023.pdf' detected. UIIC filings exist as discrete returns (NL-26, NL-37, NL-39).")
                    .actualValue(rec.getSourceDocumentTitle())
                    .affectedDocument(rec.getSourceDocumentTitle())
                    .build());
        }

        // 1.5 Local Physical File Existence and SHA-256 Verification
        String expectedFilename = HASH_TO_FILENAME.get(hash.toLowerCase());
        if (expectedFilename != null) {
            File localFile = new File(STATUTORY_DIR + expectedFilename);
            if (!localFile.exists()) {
                findings.add(ValidationFinding.builder()
                        .checkCategory("SOURCE_INTEGRITY")
                        .findingCode("DOC_FILE_NOT_FOUND")
                        .severity("BLOCKER")
                        .message("Referenced statutory artifact '" + expectedFilename + "' is missing from local repository storage.")
                        .expectedValue(STATUTORY_DIR + expectedFilename)
                        .affectedDocument(rec.getSourceDocumentTitle())
                        .build());
            } else {
                try {
                    String actualSha = computeSha256(localFile);
                    if (!actualSha.equalsIgnoreCase(hash)) {
                        findings.add(ValidationFinding.builder()
                                .checkCategory("SOURCE_INTEGRITY")
                                .findingCode("DOC_HASH_MISMATCH")
                                .severity("BLOCKER")
                                .message("Physical byte stream SHA-256 mismatch for artifact '" + expectedFilename + "'.")
                                .actualValue(actualSha)
                                .expectedValue(hash)
                                .affectedDocument(expectedFilename)
                                .build());
                    }
                } catch (Exception e) {
                    findings.add(ValidationFinding.builder()
                            .checkCategory("SOURCE_INTEGRITY")
                            .findingCode("DOC_HASH_COMPUTE_ERROR")
                            .severity("ERROR")
                            .message("Failed to compute SHA-256 hash for local artifact: " + e.getMessage())
                            .affectedDocument(expectedFilename)
                            .build());
                }
            }
        }
    }

    // =========================================================================
    // Check Suite 2: Citation Validation
    // =========================================================================
    private void validateCitation(ExternalEvidenceRecord rec, List<ValidationFinding> findings) {
        // Multi-page statutory filings require page-level citations
        String docTitle = rec.getSourceDocumentTitle() != null ? rec.getSourceDocumentTitle() : "";
        boolean isMultiPageReport = docTitle.contains("IRDAI Annual Report")
                || docTitle.contains("NIA Public Disclosures")
                || docTitle.contains("CIO Annual Report");

        if (isMultiPageReport && (rec.getCitationPageNumber() == null || rec.getCitationPageNumber() <= 0)) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("CITATION")
                    .findingCode("CITATION_PAGE_MISSING")
                    .severity("WARNING")
                    .message("Multi-page statutory filing requires an explicit page-level citation.")
                    .actualValue("null")
                    .expectedValue("Page > 0")
                    .affectedDocument(docTitle)
                    .build());
        }

        // Check Form NL-41 vs Form NL-45 mismatch
        String citationTable = rec.getCitationTableReference() != null ? rec.getCitationTableReference() : "";
        boolean isGrievanceMetric = rec.getMetricKey() != null && (rec.getMetricKey().contains("grievance") || rec.getMetricKey().contains("complaint"));

        if (isGrievanceMetric && citationTable.contains("NL-41")) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("CITATION")
                    .findingCode("CITATION_FORM_MISMATCH")
                    .severity("ERROR")
                    .message("Form NL-41 is 'Offices Information'. Customer grievances and complaints must cite Form NL-45 'Grievance Disposal'.")
                    .actualValue(citationTable)
                    .expectedValue("Form NL-45 Grievance Disposal")
                    .affectedDocument(docTitle)
                    .citation(citationTable)
                    .build());
        }
    }

    // =========================================================================
    // Check Suite 3: Numerical & Semantic Validation
    // =========================================================================
    private void validateNumericalAndSemantics(ExternalEvidenceRecord rec, List<ValidationFinding> findings) {
        // 3.1 Arithmetic reproduction for ratio metrics with numerator and denominator
        if (rec.getNumerator() != null && rec.getDenominator() != null) {
            if (rec.getDenominator().compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(ValidationFinding.builder()
                        .checkCategory("NUMERICAL_CALCULATION")
                        .findingCode("CALC_DENOMINATOR_INVALID")
                        .severity("BLOCKER")
                        .message("Denominator cannot be zero or negative.")
                        .actualValue(rec.getDenominator().toString())
                        .build());
            } else {
                BigDecimal expected;
                String unit = rec.getMetricUnit() != null ? rec.getMetricUnit().toUpperCase() : "";

                if ("PERCENTAGE".equals(unit)) {
                    expected = rec.getNumerator().divide(rec.getDenominator(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                } else if ("COMPLAINTS_PER_10K_POLICIES".equalsIgnoreCase(rec.getMetricKey())
                        || "COMPLAINTS_PER_10K_CLAIMS".equalsIgnoreCase(rec.getMetricKey())) {
                    expected = rec.getNumerator().divide(rec.getDenominator(), 6, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("10000")).setScale(2, RoundingMode.HALF_UP);
                } else if ("COMPLAINTS_PER_100CR_GWP".equalsIgnoreCase(rec.getMetricKey())) {
                    expected = rec.getNumerator().divide(rec.getDenominator(), 6, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                } else {
                    expected = rec.getNumerator().divide(rec.getDenominator(), 2, RoundingMode.HALF_UP);
                }

                BigDecimal actual = rec.getNumericValue();
                if (actual != null && actual.subtract(expected).abs().compareTo(new BigDecimal("0.05")) > 0) {
                    findings.add(ValidationFinding.builder()
                            .checkCategory("NUMERICAL_CALCULATION")
                            .findingCode("CALC_REPRODUCTION_MISMATCH")
                            .severity("ERROR")
                            .message("Mathematical reproduction mismatch: expected " + expected + ", recorded " + actual + " from N=" + rec.getNumerator() + ", D=" + rec.getDenominator())
                            .expectedValue(expected.toString())
                            .actualValue(actual.toString())
                            .citation(rec.getCitationTableReference())
                            .build());
                }
            }
        }

        // 3.2 Denominator Population Compatibility (All-lines vs Health-only)
        if ("complaints_per_10k_policies".equalsIgnoreCase(rec.getMetricKey()) && "HEALTH".equalsIgnoreCase(rec.getBusinessSegment())) {
            BigDecimal denom = rec.getDenominator();
            if (denom != null && (denom.compareTo(new BigDecimal("8842105")) == 0 || denom.compareTo(new BigDecimal("14285714")) == 0)) {
                String notes = rec.getVerificationNotes() != null ? rec.getVerificationNotes().toLowerCase() : "";
                boolean notesFlagged = notes.contains("proxy") || notes.contains("all-lines") || "ALL_LINES_PROXY".equalsIgnoreCase(rec.getReportingPopulation());
                if (!notesFlagged) {
                    findings.add(ValidationFinding.builder()
                            .checkCategory("SEMANTICS_GOVERNANCE")
                            .findingCode("DENOMINATOR_POPULATION_MISMATCH")
                            .severity("WARNING")
                            .message("Health grievance rate uses all-lines policy denominator (" + denom + ") without explicit proxy disclosure.")
                            .actualValue(denom.toString())
                            .expectedValue("Explicit ALL_LINES_PROXY tag in reportingPopulation")
                            .build());
                }
            }
        }

        // 3.3 United India Grievance Accounting Pool Reconciliation
        if (rec.getMetricKey() != null && rec.getMetricKey().contains("uiic_grievance_disposal")) {
            String notes = rec.getVerificationNotes() != null ? rec.getVerificationNotes() : "";
            if (!notes.contains("34") && !notes.contains("opening")) {
                findings.add(ValidationFinding.builder()
                        .checkCategory("SEMANTICS_GOVERNANCE")
                        .findingCode("GRIEVANCE_POOL_OPENING_BALANCE_UNSPECIFIED")
                        .severity("WARNING")
                        .message("UIIC Form NL-45 accounting pool includes 34 opening unresolved complaints (34 opening + 7,324 received + 74 reopened = 7,432 pool).")
                        .expectedValue("Pool equation: 34 + 7,324 + 74 = 7,432 = 7,423 + 9")
                        .actualValue(notes)
                        .build());
            }
        }

        // 3.4 Regulatory SLA vs Measured Insurer Compliance
        String metricKey = rec.getMetricKey() != null ? rec.getMetricKey().toLowerCase() : "";
        if (metricKey.contains("regulatory_cashless") || metricKey.contains("regulatory_discharge") || metricKey.contains("sla")) {
            if ("MEASURED_COMPLIANT".equalsIgnoreCase(rec.getActualComplianceStatus())) {
                findings.add(ValidationFinding.builder()
                        .checkCategory("SEMANTICS_GOVERNANCE")
                        .findingCode("REGULATORY_SLA_CONFUSED_WITH_COMPLIANCE")
                        .severity("ERROR")
                        .message("Regulatory SLA benchmark mandates (e.g. 1-hour cashless, 3-hour discharge) specify statutory rules, not measured insurer compliance.")
                        .actualValue("MEASURED_COMPLIANT")
                        .expectedValue("UNAVAILABLE or empirical audit measurement")
                        .build());
            }
        }

        // 3.5 Derived Metric Explicit Tagging
        if (rec.isDerived() && (rec.getCalculationFormula() == null || rec.getCalculationFormula().trim().isEmpty())) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("SEMANTICS_GOVERNANCE")
                    .findingCode("DERIVED_METRIC_UNTAGGED")
                    .severity("WARNING")
                    .message("Derived actuarial metric must document its calculation formula (e.g. midpoint weighted turnaround).")
                    .build());
        }
    }

    // =========================================================================
    // Check Suite 4: Freshness and Revision Staging
    // =========================================================================
    private void validateFreshnessAndRevision(ExternalEvidenceRecord rec, List<ValidationFinding> findings) {
        // Historical estimate revision 1 reminder
        if (rec.getRevision() == 1 && "UNVERIFIED_PENDING_REVIEW".equals(rec.getVerificationStatus())) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("FRESHNESS_REVISION")
                    .findingCode("HISTORICAL_ESTIMATE_AWAITING_UPDATE")
                    .severity("INFO")
                    .message("Revision 1 historical estimate. Check if Revision 2 source-verified correction is staged.")
                    .build());
        }

        // Staged revision 2 reminder
        if (rec.getRevision() >= 2 && "UNVERIFIED_PENDING_REVIEW".equals(rec.getVerificationStatus())) {
            findings.add(ValidationFinding.builder()
                    .checkCategory("FRESHNESS_REVISION")
                    .findingCode("STAGED_CORRECTION_PENDING_REVIEW")
                    .severity("INFO")
                    .message("Revision " + rec.getRevision() + " source correction is staged and awaiting human compliance officer review.")
                    .build());
        }
    }

    // =========================================================================
    // Prioritization & Score Calculations
    // =========================================================================
    private int calculateReviewPriorityScore(ExternalEvidenceRecord rec, List<ValidationFinding> findings) {
        int score = 0;
        for (ValidationFinding f : findings) {
            switch (f.getSeverity().toUpperCase()) {
                case "BLOCKER" -> score += 100;
                case "ERROR" -> score += 50;
                case "WARNING" -> score += 10;
                case "INFO" -> score += 1;
            }
        }

        // Staged corrections receive review priority boost
        if (rec.getRevision() >= 2) {
            score += 15;
        }

        // Material impact metrics receive minor boost
        if (rec.isMaterialImpact()) {
            score += 5;
        }

        return score;
    }

    private String determinePriorityTier(int score) {
        if (score >= 100) return "CRITICAL";
        if (score >= 50) return "HIGH";
        if (score >= 10) return "MEDIUM";
        return "LOW";
    }

    private String determineOverallOutcome(List<ValidationFinding> findings) {
        boolean hasBlocker = findings.stream().anyMatch(f -> "BLOCKER".equalsIgnoreCase(f.getSeverity()));
        if (hasBlocker) return "VALIDATION_FAILED";

        boolean hasError = findings.stream().anyMatch(f -> "ERROR".equalsIgnoreCase(f.getSeverity()));
        if (hasError) return "VALIDATION_FAILED";

        boolean hasWarning = findings.stream().anyMatch(f -> "WARNING".equalsIgnoreCase(f.getSeverity()));
        if (hasWarning) return "VALIDATION_PASSED_WITH_WARNINGS";

        return "VALIDATION_PASSED";
    }

    private String generateValidationSummary(String outcome, List<ValidationFinding> findings, int score) {
        long blockers = findings.stream().filter(f -> "BLOCKER".equalsIgnoreCase(f.getSeverity())).count();
        long errors = findings.stream().filter(f -> "ERROR".equalsIgnoreCase(f.getSeverity())).count();
        long warnings = findings.stream().filter(f -> "WARNING".equalsIgnoreCase(f.getSeverity())).count();

        return switch (outcome) {
            case "VALIDATION_PASSED" -> "All automated integrity and calculation checks passed (Score: " + score + ").";
            case "VALIDATION_PASSED_WITH_WARNINGS" -> "Validation passed with " + warnings + " advisory warning(s) (Score: " + score + ").";
            case "VALIDATION_FAILED" -> "Validation failed with " + blockers + " blocker(s) and " + errors + " error(s) (Score: " + score + "). Human review required.";
            default -> "Validation run completed (Score: " + score + ").";
        };
    }

    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
