package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.service.ClaimSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for the Deterministic Claim Simulator.
 * Provides endpoints for itemized claim simulation, historical scenario inspection,
 * and synthetic educational demo scenarios.
 */
@Slf4j
@RestController
@RequestMapping("/api/claim-simulator")
@RequiredArgsConstructor
@Tag(name = "Claim Simulator", description = "Deterministic, clause-grounded health insurance claim calculation and scenario simulation")
public class ClaimSimulatorController {

    private final ClaimSimulationService claimSimulationService;

    /**
     * POST /api/claim-simulator/simulate
     * Simulates an itemized hospital bill against an approved policy revision.
     */
    @PostMapping("/simulate")
    @Operation(summary = "Simulate an itemized claim against an approved policy revision")
    public ResponseEntity<ApiResponse<ClaimSimulationDto.ClaimSimulationResponse>> simulateClaim(
            @Valid @RequestBody ClaimSimulationDto.ClaimSimulationRequest request,
            @AuthenticationPrincipal User user) {

        log.info("[ClaimSimulatorController] POST /simulate for policy '{}', diagnosis '{}'",
                request.getPolicyId(), request.getDiagnosis());

        ClaimSimulationDto.ClaimSimulationResponse response = claimSimulationService.simulateClaim(request, user);

        return ResponseEntity.ok(ApiResponse.<ClaimSimulationDto.ClaimSimulationResponse>builder()
                .success(true)
                .message("Claim simulation evaluated and persisted successfully.")
                .data(response)
                .build());
    }

    /**
     * GET /api/claim-simulator/scenarios/{id}
     * Retrieves a saved scenario and its immutable historical calculation snapshot.
     * Enforces user ownership and authorization.
     */
    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Retrieve a saved claim scenario and its historical calculation snapshot")
    public ResponseEntity<ApiResponse<ClaimSimulationDto.SavedScenarioDetailDto>> getScenario(
            @PathVariable("id") String id,
            @AuthenticationPrincipal User user) {

        log.info("[ClaimSimulatorController] GET /scenarios/{} requested by user '{}'",
                id, (user != null ? user.getId() : "anonymous"));

        ClaimSimulationDto.SavedScenarioDetailDto detail = claimSimulationService.getScenarioById(id, user);

        return ResponseEntity.ok(ApiResponse.<ClaimSimulationDto.SavedScenarioDetailDto>builder()
                .success(true)
                .message("Claim scenario retrieved successfully.")
                .data(detail)
                .build());
    }

    /**
     * GET /api/claim-simulator/sample-scenarios
     * Returns curated synthetic demo scenarios illustrating key contractual clauses.
     */
    @GetMapping("/sample-scenarios")
    @Operation(summary = "Get synthetic demo scenarios illustrating room rent limits, sublimits, and restoration")
    public ResponseEntity<ApiResponse<List<ClaimSimulationDto.SampleScenarioDto>>> getSampleScenarios() {

        List<ClaimSimulationDto.SampleScenarioDto> samples = claimSimulationService.getSampleScenarios();

        return ResponseEntity.ok(ApiResponse.<List<ClaimSimulationDto.SampleScenarioDto>>builder()
                .success(true)
                .message("Synthetic educational scenarios retrieved successfully.")
                .data(samples)
                .build());
    }

    /**
     * POST /api/claim-simulator/compare
     * Simulates the exact same claim scenario across multiple policies for side-by-side comparison.
     */
    @PostMapping("/compare")
    @Operation(summary = "Compare the same claim scenario across multiple approved policies")
    public ResponseEntity<ApiResponse<ClaimSimulationDto.ClaimComparisonResponseDto>> compareClaimAcrossPolicies(
            @Valid @RequestBody ClaimSimulationDto.ClaimComparisonRequest request,
            @AuthenticationPrincipal User user) {

        log.info("[ClaimSimulatorController] POST /compare for {} policies, diagnosis '{}'",
                request.getPolicyIds().size(), request.getScenario().getDiagnosis());

        ClaimSimulationDto.ClaimComparisonResponseDto response =
                claimSimulationService.compareClaimAcrossPolicies(request, user);

        return ResponseEntity.ok(ApiResponse.<ClaimSimulationDto.ClaimComparisonResponseDto>builder()
                .success(true)
                .message("Cross-policy claim comparison evaluated successfully.")
                .data(response)
                .build());
    }
}
