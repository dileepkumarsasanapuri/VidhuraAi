package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.recommendation.dto.PolicyIntelligenceProfileDto;
import com.vidhura.ai.recommendation.service.PolicyIntelligenceProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller exposing the unified Policy Intelligence Profile aggregator endpoint.
 *
 * Provides a single comprehensive read endpoint that synthesizes:
 * 1. Contractual ground-truth specifications & limits
 * 2. Statutory insurer performance metrics (IRDAI NL-Forms)
 * 3. Hospital network nodes & cashless accessibility
 * 4. Authentic vs modelled quotes (with 18% GST breakdown)
 * 5. Out-of-pocket benchmark claim simulation
 * 6. Evidence quality audit scorecard
 */
@RestController
@RequestMapping({"/api/v1/policies", "/api/policies", "/api/recommend/policies"})
@RequiredArgsConstructor
@Slf4j
public class PolicyIntelligenceProfileController {

    private final PolicyIntelligenceProfileService profileService;

    /**
     * Retrieves the complete 360-degree Policy Intelligence Profile.
     */
    @GetMapping("/{policyCode}/intelligence-profile")
    public ResponseEntity<ApiResponse<PolicyIntelligenceProfileDto>> getPolicyIntelligenceProfile(
            @PathVariable String policyCode,
            @RequestParam(defaultValue = "35") int age,
            @RequestParam(defaultValue = "1000000") BigDecimal sumInsured,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pinCode,
            @RequestParam(defaultValue = "INDIVIDUAL") String familyConstruct,
            @RequestParam(required = false) Double suitabilityScore) {

        log.info("[PolicyIntelligenceProfileController] Request for policy='{}', age={}, SI={}, city='{}', suitabilityScore={}",
                policyCode, age, sumInsured, city, suitabilityScore);

        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                policyCode, age, sumInsured, city, pinCode, familyConstruct, suitabilityScore);

        return ResponseEntity.ok(ApiResponse.<PolicyIntelligenceProfileDto>builder()
                .success(true)
                .data(profile)
                .build());
    }
}
