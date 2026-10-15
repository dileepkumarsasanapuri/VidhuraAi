package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.dto.CompareDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.CompareService;
import com.vidhura.ai.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareService compareService;
    private final PolicyService policyService;

    private User getActiveUser(User user) {
        return (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompareDto.CompareResponse>> comparePolicies(
            @Valid @RequestBody(required = false) CompareDto.CompareRequest request,
            @AuthenticationPrincipal User user) {
        try {
            List<Policy> userPolicies = policyService.getUserPolicies(getActiveUser(user).getId());
            List<String> selectedIds = (request != null) ? request.getPolicyIds() : null;

            CompareDto.CompareResponse response = compareService.comparePolicies(userPolicies, selectedIds);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("Comparison failed", e.getMessage()));
        }
    }
}
