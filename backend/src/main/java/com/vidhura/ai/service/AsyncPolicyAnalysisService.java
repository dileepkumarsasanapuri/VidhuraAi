package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AsyncPolicyAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AsyncPolicyAnalysisService.class);

    private final PolicyAnalyzerService policyAnalyzerService;
    private final AnalysisTaskManager analysisTaskManager;
    private final AnalysisCacheService analysisCacheService;

    @Async("analysisTaskExecutor")
    public CompletableFuture<Policy.PolicyAnalysis> processAnalysisAsync(
            String taskId, String fileName, byte[] fileBytes, String sha256) {
        log.info("[Async Analysis] Starting asynchronous policy analysis for taskId: {}, file: {}", taskId, fileName);

        try {
            // Milestone 1: Document text extraction
            analysisTaskManager.updateProgress(taskId, 20, "PARSING_PDF", 
                    "Extracting and cleaning text from '" + fileName + "'...");
            Thread.sleep(300); // brief smoothing for UI animation

            // Milestone 2: AI Reasoning
            analysisTaskManager.updateProgress(taskId, 50, "AI_ANALYSIS", 
                    "Evaluating clauses, waiting periods, and room rent with AI model...");

            // Execute full pipeline
            Policy.PolicyAnalysis analysis = policyAnalyzerService.analyzePolicy(fileName, fileBytes);

            // Milestone 3: Normalizing & scoring
            analysisTaskManager.updateProgress(taskId, 85, "NORMALIZING", 
                    "Calculating transparency, risk index, and composite policy health score...");
            Thread.sleep(300);

            // Cache result by SHA-256 for instant retrieval in future uploads
            if (sha256 != null && !sha256.isEmpty()) {
                analysisCacheService.put(sha256, analysis);
            }

            // Milestone 4: Complete
            analysisTaskManager.completeTask(taskId, analysis);
            log.info("[Async Analysis] Successfully completed taskId: {}", taskId);
            return CompletableFuture.completedFuture(analysis);
        } catch (Exception e) {
            log.error("[Async Analysis] Failed processing taskId {}: {}", taskId, e.getMessage(), e);
            analysisTaskManager.failTask(taskId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}
