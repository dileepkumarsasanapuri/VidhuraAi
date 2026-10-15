package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.service.ClaimEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimEngineService claimEngineService;

    @PostMapping({"/predict", "/evaluate"})
    public ResponseEntity<ApiResponse<ClaimDto.ClaimOutcomeResponse>> predictClaim(
            @Valid @RequestBody ClaimDto.ClaimRequest request) {
        if (request.getParsedHospitalBill() == null || request.getParsedHospitalBill() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.err("Valid hospital bill amount is required"));
        }

        try {
            ClaimDto.ClaimOutcomeResponse outcome = claimEngineService.predictClaimOutcome(request);
            return ResponseEntity.ok(ApiResponse.ok(outcome));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("Claim prediction failed", e.getMessage()));
        }
    }
}
