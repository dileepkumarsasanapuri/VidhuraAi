package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.repository.PolicyDocumentExtractionRepository;
import com.vidhura.ai.recommendation.service.PolicyDocumentIntelligenceService;
import com.vidhura.ai.recommendation.service.PolicyEvidenceReadinessService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Phase 6.3: Policy Document Intelligence & Human Reviewer API.
 *
 * Provides authenticated access to OCR, clause extractions, pipeline metrics,
 * and reviewer approval / rejection workflows with audit logging.
 */
@Slf4j
@RestController
@RequestMapping("/api/policies/intelligence")
@RequiredArgsConstructor
public class PolicyDocumentIntelligenceController {

    private final PolicyDocumentIntelligenceService intelligenceService;
    private final PolicyDocumentExtractionRepository extractionRepository;
    private final PolicyEvidenceReadinessService readinessService;

    /**
     * Retrieves aggregated document intelligence pipeline statistics.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PolicyDocumentIntelligenceService.IntelligencePipelineSummary>> getSummary(
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(intelligenceService.getPipelineSummary()));
    }

    /**
     * Retrieves the prioritized reviewer queue of conflicting extracted evidence items.
     */
    @GetMapping("/conflicts")
    public ResponseEntity<ApiResponse<List<PolicyDocumentIntelligenceService.ConflictQueueItem>>> getConflictQueue(
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(intelligenceService.getConflictQueue()));
    }

    /**
     * Lists extracted policy attributes with optional filtering by document SHA-256 or verification status.
     */
    @GetMapping("/extractions")
    public ResponseEntity<ApiResponse<List<PolicyDocumentExtraction>>> getExtractions(
            @RequestParam(required = false) String documentSha256,
            @RequestParam(required = false) String registryRecordId,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String extractionStatus,
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);

        List<PolicyDocumentExtraction> results;
        if (documentSha256 != null && !documentSha256.isBlank()) {
            results = extractionRepository.findAllByDocumentSha256(documentSha256.trim().toLowerCase());
        } else if (registryRecordId != null && !registryRecordId.isBlank()) {
            results = extractionRepository.findAllByRegistryRecordId(registryRecordId.trim());
        } else if (verificationStatus != null && !verificationStatus.isBlank()) {
            results = extractionRepository.findAllByVerificationStatus(verificationStatus.trim());
        } else if (extractionStatus != null && !extractionStatus.isBlank()) {
            results = extractionRepository.findAllByExtractionStatus(extractionStatus.trim());
        } else {
            results = extractionRepository.findAll();
        }

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * Retrieves a single extraction record by its ID.
     */
    @GetMapping("/extractions/{id}")
    public ResponseEntity<ApiResponse<PolicyDocumentExtraction>> getExtractionById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);

        Optional<PolicyDocumentExtraction> opt = extractionRepository.findById(id);
        return opt.map(ext -> ResponseEntity.ok(ApiResponse.ok(ext)))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.err("Extraction record not found for ID: " + id)));
    }

    /**
     * Triggers page-level text extraction, OCR, clause segmentation, and structured taxonomy extraction for a document.
     */
    @PostMapping("/extract/{documentSha256}")
    public ResponseEntity<ApiResponse<PolicyDocumentIntelligenceService.DocumentExtractionResult>> extractDocument(
            @PathVariable String documentSha256,
            @RequestParam(required = false) String runId,
            @RequestParam(defaultValue = "false") boolean forceReprocess,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);

        PolicyDocumentIntelligenceService.DocumentExtractionResult result =
                intelligenceService.processDocument(documentSha256, runId, forceReprocess);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Triggers batch extraction across eligible acquired document revisions.
     */
    @PostMapping("/batch-extract")
    public ResponseEntity<ApiResponse<PolicyDocumentIntelligenceService.BatchExtractionSummary>> batchExtract(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean forceReprocess,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);

        PolicyDocumentIntelligenceService.BatchExtractionSummary summary =
                intelligenceService.processAcquiredDocuments(limit, forceReprocess);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Authenticated human reviewer action: APPROVE, REJECT, or REQUEST_CORRECTION.
     */
    @PostMapping("/verify/{extractionId}")
    public ResponseEntity<ApiResponse<PolicyDocumentExtraction>> verifyExtraction(
            @PathVariable String extractionId,
            @RequestBody ReviewRequest request,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);

        String reviewerEmail = currentUser != null && currentUser.getEmail() != null
                ? currentUser.getEmail()
                : "reviewer@vidhura.ai";

        PolicyDocumentExtraction updated = intelligenceService.reviewExtraction(
                extractionId,
                request.getAction(),
                request.getNotes(),
                reviewerEmail
        );

        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * Reviewer manual correction endpoint.
     * Updates candidate value, numeric value, unit, and evidence role with full change audit trail.
     */
    @PostMapping("/correct/{extractionId}")
    public ResponseEntity<ApiResponse<PolicyDocumentExtraction>> correctExtraction(
            @PathVariable String extractionId,
            @RequestBody CorrectionRequest request,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);

        String reviewerEmail = currentUser != null && currentUser.getEmail() != null
                ? currentUser.getEmail()
                : "reviewer@vidhura.ai";

        PolicyDocumentExtraction corrected = intelligenceService.correctExtraction(
                extractionId,
                request.getCorrectedValue(),
                request.getCorrectedNumeric(),
                request.getCorrectedUnit(),
                request.getCorrectedEvidenceRole(),
                request.getNotes(),
                reviewerEmail,
                request.isApproveAfterCorrection()
        );

        return ResponseEntity.ok(ApiResponse.ok(corrected));
    }

    /**
     * Deterministic policy-level evidence readiness assessment endpoint.
     */
    @GetMapping("/readiness/{documentIdentifier}")
    public ResponseEntity<ApiResponse<PolicyEvidenceReadinessService.PolicyEvidenceReadinessResult>> getReadiness(
            @PathVariable String documentIdentifier,
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);

        PolicyEvidenceReadinessService.PolicyEvidenceReadinessResult result =
                readinessService.evaluatePolicyReadiness(documentIdentifier);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private void verifyAuthenticated(User user) {
        if (user == null) {
            throw new AccessDeniedException("Access Denied: Authentication required to inspect policy extractions.");
        }
    }

    private void verifyReviewerOrAdmin(User user) {
        if (user == null || user.getRole() == null ||
                (!"ROLE_ADMIN".equals(user.getRole()) && !"ROLE_REVIEWER".equals(user.getRole()))) {
            throw new AccessDeniedException("Access Denied: Administrative or Reviewer privileges required.");
        }
    }

    @Data
    public static class ReviewRequest {
        private String action; // APPROVE, REJECT, REQUEST_CORRECTION
        private String notes;
    }

    @Data
    public static class CorrectionRequest {
        private String correctedValue;
        private Double correctedNumeric;
        private String correctedUnit;
        private String correctedEvidenceRole;
        private String notes;
        private boolean approveAfterCorrection;
    }
}
