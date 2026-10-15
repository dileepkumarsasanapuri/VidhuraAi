package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Void>> checkHealth() {
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("healthy")
                .service("Vidhura AI Spring Boot Backend")
                .version("1.0.0")
                .timestamp(Instant.now().toString())
                .build());
    }
}
