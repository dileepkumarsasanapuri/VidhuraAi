package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    private User getActiveUser(User user) {
        return (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Policy>>> getAllPolicies(@AuthenticationPrincipal User user) {
        List<Policy> policies = policyService.getUserPolicies(getActiveUser(user).getId());
        return ResponseEntity.ok(ApiResponse.ok(policies, policies.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Policy>> getPolicyById(@PathVariable String id, @AuthenticationPrincipal User user) {
        Optional<Policy> policy = policyService.getPolicyById(id, getActiveUser(user).getId());
        return policy.map(p -> ResponseEntity.ok(ApiResponse.ok(p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.err("Policy not found")));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Policy>> createPolicy(@RequestBody Policy policyData, @AuthenticationPrincipal User user) {
        Policy created = policyService.createPolicy(getActiveUser(user).getId(), policyData);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable String id, @AuthenticationPrincipal User user) {
        boolean deleted = policyService.deletePolicy(id, getActiveUser(user).getId());
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.okMessage("Policy deleted"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.err("Policy not found"));
        }
    }
}
