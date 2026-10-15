package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing the user's health insurance requirements profile
 * collected through the progressive questionnaire (Step A and Step B).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recommendation_profiles")
public class RecommendationProfile {

    @Id
    private String id;

    @Indexed
    private String userId;

    // Step A: Required Inputs
    private int primaryAge;
    private String insuranceType; // INDIVIDUAL, SELF_AND_SPOUSE, FAMILY_WITH_CHILDREN, PARENTS
    
    @Builder.Default
    private List<InsuredMember> members = new ArrayList<>();

    private String cityOrPincode;
    private String detectedZone; // Zone 1, Zone 2, Zone 3
    private double annualBudget;
    private String budgetType; // STRICT, PREFERRED
    private long desiredSumInsured; // 500000, 1000000, 2500000, 5000000, etc.
    private boolean helpMeDecideSumInsured;
    private String primaryPriority; // AFFORDABLE_PREMIUMS, BROADER_COVERAGE, LOWER_OUT_OF_POCKET, BALANCED

    // Step B: Additional Inputs for Accuracy
    @Builder.Default
    private List<String> preferredHospitals = new ArrayList<>();

    @Builder.Default
    private List<String> preExistingConditions = new ArrayList<>();

    private boolean hasExistingInsurance;
    private Long existingSumInsured;
    private boolean portingPolicy;
    private String policyRenewalDate;

    private boolean maternityBenefitNeeded;
    private boolean opdBenefitNeeded;
    private String copayTolerance; // NONE, LOW, FLEXIBLE

    @CreatedDate
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsuredMember {
        private String relation; // SELF, SPOUSE, CHILD_1, CHILD_2, CHILD_3, FATHER, MOTHER
        private String displayName;
        private int age;
        @Builder.Default
        private List<String> preExistingConditions = new ArrayList<>();
    }
}
