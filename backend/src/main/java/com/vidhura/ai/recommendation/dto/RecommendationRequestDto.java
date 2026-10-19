package com.vidhura.ai.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Validated DTO representing user questionnaire inputs.
 * Implements progressive questionnaire specification:
 * Step A (Mandatory essentials) + Step B (Accuracy enhancers).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDto {

    // =========================================================================
    // STEP A: Required Minimum Inputs for First Recommendation
    // =========================================================================

    @NotNull(message = "Age of the primary insured is required")
    @Min(value = 18, message = "Primary insured must be at least 18 years old")
    @Max(value = 100, message = "Primary insured age must be 100 or below")
    private Integer primaryAge;

    @NotBlank(message = "Insurance type is required (e.g. INDIVIDUAL, SELF_AND_SPOUSE, FAMILY_WITH_CHILDREN, PARENTS)")
    private String insuranceType;

    /**
     * For family policies, collects the age of every proposed member.
     */
    @Valid
    @Builder.Default
    private List<MemberInputDto> members = new ArrayList<>();

    @NotBlank(message = "City or PIN code is required for localized zone pricing and hospital lookup")
    private String cityOrPincode;

    @NotNull(message = "Annual premium budget is required")
    @Positive(message = "Annual premium budget must be greater than zero")
    private Double annualBudget;

    @NotBlank(message = "Budget type is required (STRICT or PREFERRED)")
    @Pattern(regexp = "^(STRICT|PREFERRED)$", message = "Budget type must be STRICT or PREFERRED")
    private String budgetType;

    /**
     * 500000, 1000000, 2500000, 5000000, or null/0 if helpMeDecideSumInsured is true.
     */
    private Long desiredSumInsured;

    @Builder.Default
    private Boolean helpMeDecideSumInsured = false;

    @NotBlank(message = "Primary priority is required")
    @Pattern(
        regexp = "^(AFFORDABLE_PREMIUMS|BROADER_COVERAGE|LOWER_OUT_OF_POCKET|BALANCED)$",
        message = "Primary priority must be AFFORDABLE_PREMIUMS, BROADER_COVERAGE, LOWER_OUT_OF_POCKET, or BALANCED"
    )
    private String primaryPriority;

    // =========================================================================
    // STEP B: Additional Inputs That Improve Accuracy
    // =========================================================================

    @Builder.Default
    private List<String> preferredHospitals = new ArrayList<>();

    @Builder.Default
    private List<String> preExistingConditions = new ArrayList<>();

    @Builder.Default
    private Boolean hasExistingInsurance = false;

    private Long existingSumInsured;

    @Builder.Default
    private Boolean portingPolicy = false;

    private String policyRenewalDate;

    @Builder.Default
    private Boolean maternityBenefitNeeded = false;

    @Builder.Default
    private Boolean opdBenefitNeeded = false;

    @Builder.Default
    private String copayTolerance = "FLEXIBLE"; // NONE, LOW, FLEXIBLE

    // Phase 3 Intelligence Inputs
    @Builder.Default
    private Integer policyContinuityMonths = 0; // Months of continuous prior coverage for waiting period credit

    @Builder.Default
    private String likelyTreatmentNeeds = "COMPREHENSIVE"; // CARDIAC, ORTHOPEDIC, CATARACT, GENERAL_SURGERY, MATERNITY, COMPREHENSIVE

    @Builder.Default
    private String strictRoomPreference = "SINGLE_PRIVATE_AC"; // SINGLE_PRIVATE_AC, DELUXE, SHARED, NO_PREFERENCE

    @Builder.Default
    private Boolean includeDemoFixtures = false; // Production winner rankings exclude synthetic fixtures by default

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInputDto {
        @NotBlank(message = "Relation is required (e.g. SPOUSE, CHILD, PARENT)")
        private String relation;

        private String displayName;

        @NotNull(message = "Member age is required")
        @Min(value = 0, message = "Age cannot be negative")
        @Max(value = 100, message = "Member age must be 100 or below")
        private Integer age;

        @Builder.Default
        private List<String> preExistingConditions = new ArrayList<>();
    }
}
