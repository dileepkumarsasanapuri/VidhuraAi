package com.vidhura.ai.controller;

import com.vidhura.ai.dto.AnalysisTaskDto;
import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.service.AnalysisCacheService;
import com.vidhura.ai.service.AnalysisTaskManager;
import com.vidhura.ai.service.AsyncPolicyAnalysisService;
import com.vidhura.ai.service.PolicyAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
public class AnalyzeController {

    private final PolicyAnalyzerService policyAnalyzerService;
    private final AsyncPolicyAnalysisService asyncPolicyAnalysisService;
    private final AnalysisTaskManager analysisTaskManager;
    private final AnalysisCacheService analysisCacheService;

    @PostMapping
    public ResponseEntity<ApiResponse<Policy.PolicyAnalysis>> analyzePolicy(
            @RequestParam("policyFile") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.err("File is required"));
        }

        try {
            byte[] bytes = file.getBytes();
            String sha256 = analysisCacheService.computeSha256(bytes);

            // Phase 3.2: Check SHA-256 cache
            Optional<Policy.PolicyAnalysis> cached = analysisCacheService.getCached(sha256);
            if (cached.isPresent()) {
                log.info("[Analyze API] Instant return from cache for file: {} (hash: {})", 
                        file.getOriginalFilename(), sha256);
                return ResponseEntity.ok(ApiResponse.ok(cached.get()));
            }

            Policy.PolicyAnalysis analysis = policyAnalyzerService.analyzePolicy(
                    file.getOriginalFilename(), bytes);
            analysisCacheService.put(sha256, analysis);
            return ResponseEntity.ok(ApiResponse.ok(analysis));
        } catch (Exception e) {
            log.error("Analysis route error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("Analysis failed", e.getMessage()));
        }
    }

    @PostMapping("/async")
    public ResponseEntity<ApiResponse<AnalysisTaskDto>> analyzePolicyAsync(
            @RequestParam("policyFile") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.err("File is required"));
        }

        try {
            byte[] bytes = file.getBytes();
            String fileName = file.getOriginalFilename();
            String sha256 = analysisCacheService.computeSha256(bytes);
            String taskId = UUID.randomUUID().toString();

            // Phase 3.2: Check SHA-256 cache first
            Optional<Policy.PolicyAnalysis> cached = analysisCacheService.getCached(sha256);
            if (cached.isPresent()) {
                log.info("[Async Analyze API] Instant return from cache for file: {} (hash: {})", fileName, sha256);
                AnalysisTaskDto completedTask = analysisTaskManager.createTask(taskId, fileName, true, cached.get());
                return ResponseEntity.ok(ApiResponse.ok(completedTask));
            }

            // Phase 3.1: Start async processing
            AnalysisTaskDto task = analysisTaskManager.createTask(taskId, fileName, false, null);
            asyncPolicyAnalysisService.processAnalysisAsync(taskId, fileName, bytes, sha256);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(task));
        } catch (Exception e) {
            log.error("Async analysis initiation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("Async analysis failed to start", e.getMessage()));
        }
    }

    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysisProgress(@PathVariable String taskId) {
        log.info("[SSE Stream] Client subscribed to progress stream for taskId: {}", taskId);
        return analysisTaskManager.subscribe(taskId);
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<ApiResponse<AnalysisTaskDto>> getAnalysisStatus(@PathVariable String taskId) {
        Optional<AnalysisTaskDto> taskOpt = analysisTaskManager.getTask(taskId);
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(taskOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.err("Analysis task not found for ID: " + taskId));
    }
}
