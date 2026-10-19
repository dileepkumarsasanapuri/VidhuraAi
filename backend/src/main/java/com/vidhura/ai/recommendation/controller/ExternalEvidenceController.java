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
@RequestMapping("/api/v1/external-evidence")
@RequiredArgsConstructor
@Slf4j
public class ExternalEvidenceController {

    private final ExternalEvidenceService externalEvidenceService;

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
}
