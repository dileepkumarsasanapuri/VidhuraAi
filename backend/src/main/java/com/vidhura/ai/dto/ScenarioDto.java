package com.vidhura.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ScenarioDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioRequest {
        @NotBlank(message = "Question or scenario description is required")
        @Size(min = 3, max = 2000, message = "Question must be between 3 and 2000 characters")
        private String question;

        private String policyId;
        private String language;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioResponse {
        private Object covered; // true, false, or "unknown"
        private String coverageStatus;
        private String summary;
        private CostEstimate estimatedCost;
        private List<String> conditions;
        private String recommendation;
        private List<String> relevantClauses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostEstimate {
        private Double total;
        private Double covered;
        private Double outOfPocket;
    }
}
