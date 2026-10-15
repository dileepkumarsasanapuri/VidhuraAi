package com.vidhura.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ClaimDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimRequest {
        @NotBlank(message = "Claim type is required (e.g., hospitalization, surgery, accident)")
        private String type;

        @NotBlank(message = "Disease or medical condition name is required")
        private String disease;

        @NotNull(message = "Hospital bill is required")
        private Object hospitalBill; // Double, String, or Integer

        @Min(value = 0, message = "Member age must be 0 or greater")
        @Max(value = 130, message = "Member age must be realistic")
        private Integer memberAge;

        private Boolean isPreExisting;
        private String hospitalType; // 'network' or 'non-network'
        private String roomType;     // 'single', 'suite', 'deluxe'
        private String policyId;

        @JsonIgnore
        public Double getParsedHospitalBill() {
            if (hospitalBill == null) return null;
            if (hospitalBill instanceof Number) {
                return ((Number) hospitalBill).doubleValue();
            }
            try {
                return Double.parseDouble(hospitalBill.toString());
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimOutcomeResponse {
        private Integer approvalProbability;
        private Double hospitalBill;
        private Double coveredAmount;
        private Double outOfPocket;
        private Breakdown breakdown;
        private List<String> rejectionReasons;
        private List<RequiredDocument> requiredDocuments;
        private List<String> tips;
        private String timeline;
        private SimilarClaims similarClaims;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private Double deductible;
        private Double coPay;
        private Double roomRentExcess;
        private Double subLimitDeduction;
        private Double networkDiscount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequiredDocument {
        private String name;
        private Boolean critical;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarClaims {
        private Integer approved;
        private Integer rejected;
        private Long avgPayout;
    }
}
