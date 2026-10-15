package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.AuditLog;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(@AuthenticationPrincipal User user) {
        String userId = (user != null && !JwtAuthenticationFilter.GUEST_USER.getId().equals(user.getId())) 
                ? user.getId() 
                : null;

        List<AuditLog> logs = (userId != null) 
                ? auditService.getLogsForUser(userId) 
                : auditService.getRecentLogs();

        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
