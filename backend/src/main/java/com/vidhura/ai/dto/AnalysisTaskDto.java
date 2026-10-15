package com.vidhura.ai.dto;

import com.vidhura.ai.model.Policy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskDto {

    private String taskId;
    private String fileName;
    private String status; // "PROCESSING", "COMPLETED", "FAILED"
    private int progress;  // 0 - 100
    private String step;   // "UPLOADED", "PARSING_PDF", "AI_ANALYSIS", "NORMALIZING", "COMPLETE", "FAILED"
    private String message;
    private boolean cached;
    private Policy.PolicyAnalysis result;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;
}
