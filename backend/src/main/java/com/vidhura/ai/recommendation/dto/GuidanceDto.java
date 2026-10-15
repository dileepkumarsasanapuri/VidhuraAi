package com.vidhura.ai.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class GuidanceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuidanceRequest {
        @NotNull(message = "Primary age is required")
        @Min(18)
        @Max(100)
        private Integer primaryAge;

        @NotBlank(message = "Insurance type is required")
        private String insuranceType;

        private Integer totalMembers;
        private String cityOrPincode;
        private Double currentBudget;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuidanceResponse {
        private long recommendedSumInsured;
        private String formattedRecommendedSumInsured;
        private String rationale;
        private String pricingZone;
        private double estimatedAnnualPremiumMin;
        private double estimatedAnnualPremiumMax;
        private String formattedEstimatedPremiumRange;
        private List<String> cityHealthcareInsights;
        private List<String> recommendationsByLifeStage;
    }
}
