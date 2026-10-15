package com.vidhura.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CompareDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareRequest {
        @NotEmpty(message = "Policy IDs list cannot be empty")
        @Size(min = 1, max = 10, message = "You can compare between 1 and 10 policies")
        private List<String> policyIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareResponse {
        private List<PolicyComparisonItem> policies;
        private Recommendation recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyComparisonItem {
        private String id;
        private String name;
        private String provider;
        private String premium;
        private String coverage;
        private Scores scores;
        private List<String> pros;
        private List<String> cons;
        private Integer exclusions;
        private Integer hiddenTraps;
        private String roomRentLimit;
        private Boolean roomRentCapped;
        private String copay;
        private Double copayPercentage;
        private String deductible;
        private Double deductibleAmount;
        private String diseaseSubLimits;
        private Integer preHospitalizationDays;
        private Integer postHospitalizationDays;
        private String restorationBenefit;
        private String noClaimBonus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scores {
        private Integer transparency;
        private Integer risk;
        private Integer fairness;
        private Integer claimReliability;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String bestPolicy;
        private String reason;
        private String savings;
    }
}
