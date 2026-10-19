package com.vidhura.ai.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
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

        @JsonAlias({"memberCount", "totalMembers"})
        private Integer totalMembers;

        private String cityOrPincode;

        @JsonAlias({"annualBudget", "currentBudget"})
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
        private String zone; // frontend alias
        private double estimatedAnnualPremiumMin;
        private double estimatedAnnualPremiumMax;
        private double estimatedMinBudget; // frontend alias
        private double estimatedMaxBudget; // frontend alias
        private String formattedEstimatedPremiumRange;
        private String budgetAdequacyNote;
        private String medicalInflationInsight;
        @Builder.Default
        private List<String> cityHealthcareInsights = new ArrayList<>();
        @Builder.Default
        private List<String> recommendationsByLifeStage = new ArrayList<>();
        @Builder.Default
        private List<String> tips = new ArrayList<>(); // frontend alias
    }
}
