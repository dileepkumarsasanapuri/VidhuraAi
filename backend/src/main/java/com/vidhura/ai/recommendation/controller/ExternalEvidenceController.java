package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.service.ExternalEvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller exposing external real-world evidence, insurer statutory metrics,
 * hospital network availability, and authentic/modelled quotes.
 */
@RestController
@RequestMapping({"/api/v1/external-evidence", "/api/external-evidence"})
@RequiredArgsConstructor
@Slf4j
public class ExternalEvidenceController {

    private final ExternalEvidenceService externalEvidenceService;
    private final com.vidhura.ai.recommendation.service.ExternalEvidenceValidationService validationService;

    /**
     * Retrieves verified statutory performance metrics for an insurer (IRDAI Annual Reports, NL-Forms).
     */
    @GetMapping("/metrics/{insurerCode}")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.InsurerPerformanceSummaryDto>> getInsurerMetrics(
            @PathVariable String insurerCode,
            @RequestParam(required = false) String reportingPeriod) {

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary =
                externalEvidenceService.getInsurerPerformanceMetrics(insurerCode, reportingPeriod);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.InsurerPerformanceSummaryDto>builder()
                .success(true)
                .data(summary)
                .build());
    }

    /**
     * Retrieves traceable, source-backed grievance and ombudsman statistics for an insurer (Phase 3.8).
     */
    @GetMapping("/grievances/{insurerCode}")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.InsurerGrievanceIntelligenceDto>> getInsurerGrievances(
            @PathVariable String insurerCode,
            @RequestParam(required = false) String reportingPeriod) {

        ExternalEvidenceDto.InsurerGrievanceIntelligenceDto intelligence =
                externalEvidenceService.getInsurerGrievanceIntelligence(insurerCode, reportingPeriod);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.InsurerGrievanceIntelligenceDto>builder()
                .success(true)
                .data(intelligence)
                .build());
    }

    /**
     * Retrieves codified IRDAI regulatory processing timeline SLA benchmarks (Phase 3.8).
     */
    @GetMapping("/regulatory-slas")
    public ResponseEntity<ApiResponse<java.util.List<ExternalEvidenceDto.RegulatorySlaBenchmarkDto>>> getRegulatorySlas() {
        java.util.List<ExternalEvidenceDto.RegulatorySlaBenchmarkDto> benchmarks =
                externalEvidenceService.getRegulatorySlaBenchmarks();

        return ResponseEntity.ok(ApiResponse.<java.util.List<ExternalEvidenceDto.RegulatorySlaBenchmarkDto>>builder()
                .success(true)
                .data(benchmarks)
                .count(benchmarks.size())
                .build());
    }

    /**
     * Queries network hospitals for an insurer filtered by city or pincode.
     */
    @GetMapping("/hospitals")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.HospitalNetworkSummaryDto>> getHospitalNetwork(
            @RequestParam(required = false) String insurer,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pinCode) {

        ExternalEvidenceDto.HospitalNetworkSummaryDto summary =
                externalEvidenceService.getHospitalNetworkSummary(insurer, city, pinCode);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.HospitalNetworkSummaryDto>builder()
                .success(true)
                .data(summary)
                .count(summary != null ? summary.getTotalNetworkHospitalsInCity() : 0)
                .build());
    }

    /**
     * Resolves authentic quote from verified direct sources or falls back to transparent actuarial estimate.
     */
    @GetMapping("/quotes")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.QuoteDetailDto>> getQuote(
            @RequestParam String policyCode,
            @RequestParam int age,
            @RequestParam BigDecimal sumInsured,
            @RequestParam(required = false) String pinCode,
            @RequestParam(required = false) String familyConstruct) {

        ExternalEvidenceDto.QuoteDetailDto quote = externalEvidenceService.getAuthenticOrModelledQuote(
                policyCode, age, sumInsured, pinCode, familyConstruct);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.QuoteDetailDto>builder()
                .success(true)
                .data(quote)
                .build());
    }

    /**
     * Ingests external evidence with cryptographic SHA-256 validation.
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.EvidenceRecordDto>> ingestEvidence(
            @Valid @RequestBody ExternalEvidenceDto.IngestEvidenceRequest request,
            @AuthenticationPrincipal User user) {

        ExternalEvidenceDto.EvidenceRecordDto record = externalEvidenceService.ingestEvidenceRecord(request, user);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.EvidenceRecordDto>builder()
                .success(true)
                .data(record)
                .message("External evidence record successfully ingested with status: " + record.getVerificationStatus())
                .build());
    }

    /**
     * Lists evidence records for reviewer audit, diff inspection, and verification dashboard.
     */
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<java.util.List<ExternalEvidenceDto.EvidenceRecordDto>>> getEvidenceRecords(
            @RequestParam(required = false) String insurerCode,
            @RequestParam(required = false) String status) {

        java.util.List<ExternalEvidenceDto.EvidenceRecordDto> records =
                externalEvidenceService.getAllEvidenceRecords(insurerCode, status);

        return ResponseEntity.ok(ApiResponse.<java.util.List<ExternalEvidenceDto.EvidenceRecordDto>>builder()
                .success(true)
                .data(records)
                .count(records.size())
                .build());
    }

    /**
     * Reviewer-gated sign-off endpoint for external evidence.
     */
    @PostMapping("/verify/{recordId}")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.EvidenceRecordDto>> verifyEvidence(
            @PathVariable String recordId,
            @Valid @RequestBody ExternalEvidenceDto.VerifyEvidenceRequest request,
            @AuthenticationPrincipal User reviewer) {

        ExternalEvidenceDto.EvidenceRecordDto verified = externalEvidenceService.verifyEvidenceRecord(recordId, request, reviewer);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.EvidenceRecordDto>builder()
                .success(true)
                .data(verified)
                .message("Evidence record successfully verified as: " + verified.getVerificationStatus())
                .build());
    }

    /**
     * Ingests hospital network node (Admin/Reviewer).
     */
    @PostMapping("/hospitals/ingest")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.HospitalNodeDto>> ingestHospital(
            @Valid @RequestBody ExternalEvidenceDto.IngestHospitalRequest request,
            @AuthenticationPrincipal User user) {

        ExternalEvidenceDto.HospitalNodeDto node = externalEvidenceService.registerHospitalNode(request, user);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.HospitalNodeDto>builder()
                .success(true)
                .data(node)
                .message("Hospital network node registered: " + node.getHospitalName())
                .build());
    }

    /**
     * Phase 3.9: Triggers automated validation for an evidence record.
     */
    @PostMapping("/validate/{recordId}")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.ValidationResultDto>> validateEvidenceRecord(
            @PathVariable String recordId) {

        com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult result =
                validationService.validateRecordById(recordId);
        ExternalEvidenceDto.ValidationResultDto dto = validationService.mapToResultDto(result);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.ValidationResultDto>builder()
                .success(true)
                .data(dto)
                .message("Automated validation executed: " + dto.getOverallOutcome() + " (Score: " + dto.getReviewPriorityScore() + ")")
                .build());
    }

    /**
     * Phase 3.9: Retrieves the latest validation result for an evidence record.
     */
    @GetMapping("/validation/{recordId}")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.ValidationResultDto>> getEvidenceValidationResult(
            @PathVariable String recordId) {

        java.util.Optional<com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult> resultOpt =
                validationService.getLatestValidationResult(recordId);

        return resultOpt.map(res -> ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.ValidationResultDto>builder()
                .success(true)
                .data(validationService.mapToResultDto(res))
                .build()))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.ValidationResultDto>builder()
                        .success(false)
                        .message("No validation result found for record: " + recordId)
                        .build()));
    }

    /**
     * Phase 3.9: Scans and validates all evidence records in batch.
     */
    @PostMapping("/validate-all")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.BatchValidationSummaryDto>> validateAllEvidence() {
        ExternalEvidenceDto.BatchValidationSummaryDto summary = validationService.validateAllRecords();

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.BatchValidationSummaryDto>builder()
                .success(true)
                .data(summary)
                .message("Batch validation completed across " + summary.getTotalRecordsScanned() + " evidence records.")
                .build());
    }

    /**
     * Phase 3.9, 5.3 & 5.4: Retrieves the reviewer-prioritization queue with severity, outcome, approval mode, publication and freshness filters.
     */
    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<java.util.List<ExternalEvidenceDto.ReviewQueueItemDto>>> getReviewQueue(
            @RequestParam(required = false) String insurerCode,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String validationOutcome,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String approvalMode,
            @RequestParam(required = false) String publicationStatus,
            @RequestParam(required = false) String freshnessStatus) {

        java.util.List<ExternalEvidenceDto.ReviewQueueItemDto> queue =
                validationService.getPrioritizedReviewQueue(insurerCode, severity, validationOutcome, category, approvalMode, publicationStatus, freshnessStatus);

        return ResponseEntity.ok(ApiResponse.<java.util.List<ExternalEvidenceDto.ReviewQueueItemDto>>builder()
                .success(true)
                .data(queue)
                .count(queue.size())
                .build());
    }

    public ResponseEntity<ApiResponse<java.util.List<ExternalEvidenceDto.ReviewQueueItemDto>>> getReviewQueue(
            String insurerCode, String severity, String validationOutcome, String category) {
        return getReviewQueue(insurerCode, severity, validationOutcome, category, null, null, null);
    }

    /**
     * Phase 5.3: Revalidates an evidence record against current governance rules and updates decision.
     */
    @PostMapping("/revalidate/{recordId}")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.ValidationResultDto>> revalidateEvidence(
            @PathVariable String recordId) {

        com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult result =
                validationService.revalidateRecord(recordId);
        ExternalEvidenceDto.ValidationResultDto dto = validationService.mapToResultDto(result);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.ValidationResultDto>builder()
                .success(true)
                .data(dto)
                .message("Evidence record revalidated under governance rules: " + dto.getOverallOutcome())
                .build());
    }

    /**
     * Phase 5.4: Acquires or refreshes the physical source artifact for an evidence record.
     */
    @PostMapping("/acquire/{recordId}")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.ReviewQueueItemDto>> acquireArtifact(
            @PathVariable String recordId) {

        com.vidhura.ai.recommendation.model.ExternalEvidenceRecord updated =
                validationService.acquireArtifactForRecord(recordId);

        java.util.List<ExternalEvidenceDto.ReviewQueueItemDto> queue =
                validationService.getPrioritizedReviewQueue(null, null, null, null, null, null, null);
        ExternalEvidenceDto.ReviewQueueItemDto itemDto = queue.stream()
                .filter(item -> recordId.equals(item.getEvidenceRecordId()))
                .findFirst()
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.ReviewQueueItemDto>builder()
                .success(true)
                .data(itemDto)
                .message("Artifact acquired & validated: freshness=" + updated.getFreshnessStatus() + ", backed=" + updated.isArtifactBacked())
                .build());
    }

    /**
     * Phase 5.4: Retrieves freshness lifecycle summary across all evidence records.
     */
    @GetMapping("/freshness-summary")
    public ResponseEntity<ApiResponse<ExternalEvidenceDto.FreshnessSummaryDto>> getFreshnessSummary() {
        ExternalEvidenceDto.FreshnessSummaryDto summary = validationService.getFreshnessSummary();

        return ResponseEntity.ok(ApiResponse.<ExternalEvidenceDto.FreshnessSummaryDto>builder()
                .success(true)
                .data(summary)
                .build());
    }

    /**
     * Phase 5.5: Triggers an automated freshness monitoring cycle on-demand.
     */
    @PostMapping("/freshness/execute-cycle")
    @PreAuthorize("hasAnyRole('ROLE_REVIEWER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<com.vidhura.ai.recommendation.service.EvidenceFreshnessSchedulerService.FreshnessMonitoringReport>> triggerFreshnessCycle(
            @org.springframework.beans.factory.annotation.Autowired com.vidhura.ai.recommendation.service.EvidenceFreshnessSchedulerService schedulerService) {

        var report = schedulerService.executeFreshnessCycle();
        return ResponseEntity.ok(ApiResponse.<com.vidhura.ai.recommendation.service.EvidenceFreshnessSchedulerService.FreshnessMonitoringReport>builder()
                .success(true)
                .data(report)
                .message("Freshness monitoring cycle completed.")
                .build());
    }
}

