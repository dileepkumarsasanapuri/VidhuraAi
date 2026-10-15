package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics(@AuthenticationPrincipal User user) {
        User activeUser = (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAnalyticsData(activeUser.getId())));
    }
}
