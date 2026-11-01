package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.service.DocumentAcquisitionPipelineService;
import com.vidhura.ai.recommendation.service.IrdaiRegistryInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Phase 6.1: Read-Only Authenticated IRDAI Health Insurance Products Registry Inventory API.
 *
 * Provides authenticated, read-only access to portal registry records, pagination, search,
 * filters (financial year, archive status, classification), and summary statistics.
 *
 * Mutation and crawler control endpoints are NOT exposed to public consumers.
 */
@Slf4j
@RestController
@RequestMapping("/api/policies/registry")
@RequiredArgsConstructor
public class IrdaiRegistryController {

    private final IrdaiRegistryInventoryService inventoryService;
    private final IrdaiRegistryRepository registryRepository;
    private final DocumentAcquisitionPipelineService acquisitionPipelineService;

    /**
     * Retrieves aggregated inventory summary statistics and distributions.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IrdaiRegistryInventoryService.InventorySummaryDto>> getSummary(
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);
        IrdaiRegistryInventoryService.InventorySummaryDto summary = inventoryService.getInventorySummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Paginated listing, keyword search, and multi-field filtering over registry records.
     */
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<Page<IrdaiRegistryRecord>>> getRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String uin,
            @RequestParam(required = false) String insurer,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String financialYear,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String classificationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sourcePage") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @AuthenticationPrincipal User currentUser) {

        verifyAuthenticated(currentUser);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortBy));

        Page<IrdaiRegistryRecord> results = inventoryService.searchRegistry(
                keyword, uin, insurer, productName, financialYear, archived, classificationStatus, pageable
        );

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * Retrieves a single registry record by ID or UIN.
     */
    @GetMapping("/records/{id}")
    public ResponseEntity<ApiResponse<IrdaiRegistryRecord>> getRecordById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        verifyAuthenticated(currentUser);

        Optional<IrdaiRegistryRecord> record = registryRepository.findById(id);
        if (record.isEmpty()) {
            record = registryRepository.findByNormalizedUin(id.trim().toUpperCase());
        }

        return record.map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.err("Registry record not found for: " + id)));
    }

    /**
     * Phase 6.2: Retrieves document acquisition pipeline summary and storage status.
     */
    @GetMapping("/acquisition/summary")
    public ResponseEntity<ApiResponse<DocumentAcquisitionPipelineService.PipelineSummaryDto>> getAcquisitionSummary(
            @AuthenticationPrincipal User currentUser) {
        verifyAuthenticated(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(acquisitionPipelineService.getPipelineSummary()));
    }

    /**
     * Phase 6.2: Executes representative pilot batch covering 10 distinct regulatory archetypes.
     */
    @PostMapping("/acquisition/pilot")
    public ResponseEntity<ApiResponse<DocumentAcquisitionPipelineService.AcquisitionBatchResult>> runPilotBatch(
            @RequestParam(required = false) String runId,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(acquisitionPipelineService.executePilotBatch(runId)));
    }

    /**
     * Phase 6.2: Executes bounded batch document acquisition across eligible registry records.
     */
    @PostMapping("/acquisition/batch")
    public ResponseEntity<ApiResponse<DocumentAcquisitionPipelineService.AcquisitionBatchResult>> runAcquisitionBatch(
            @RequestParam(defaultValue = "10") int batchSize,
            @RequestParam(defaultValue = "false") boolean retryFailed,
            @RequestParam(required = false) String runId,
            @AuthenticationPrincipal User currentUser) {
        verifyReviewerOrAdmin(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(acquisitionPipelineService.executeBatch(batchSize, retryFailed, runId)));
    }

    private void verifyAuthenticated(User user) {
        // Enforce that only authenticated consumers or reviewers can inspect the registry
        if (user == null) {
            throw new AccessDeniedException("Access Denied: Authentication required to inspect IRDAI registry inventory.");
        }
    }

    private void verifyReviewerOrAdmin(User user) {
        if (user == null || user.getRole() == null ||
                (!"ROLE_ADMIN".equals(user.getRole()) && !"ROLE_REVIEWER".equals(user.getRole()))) {
            throw new AccessDeniedException("Access Denied: Administrative or Reviewer privileges required for acquisition operations.");
        }
    }
}
