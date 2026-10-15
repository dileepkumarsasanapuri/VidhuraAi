package com.vidhura.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policies")
@CompoundIndexes({
    @CompoundIndex(name = "user_status_idx", def = "{'userId': 1, 'status': 1}"),
    @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
})
public class Policy {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;

    private String provider;

    @Builder.Default
    private String type = "Health";

    private String premium;

    private String coverage;

    private String expiryDate;

    @Builder.Default
    private Integer healthScore = 0;

    @Builder.Default
    private String riskLevel = "medium"; // high, medium, low

    @Builder.Default
    private String status = "active"; // active, expiring, expired

    private PolicyAnalysis analysis;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyAnalysis {
        private String policyName;
        private String provider;
        private String type;
        private String premium;
        private String coverage;
        private String expiryDate;
        
        @Builder.Default
        private List<String> clauses = new ArrayList<>();
        
        @Builder.Default
        private List<String> exclusions = new ArrayList<>();
        
        @Builder.Default
        private List<WaitingPeriod> waitingPeriods = new ArrayList<>();
        
        @Builder.Default
        private List<Rider> riders = new ArrayList<>();
        
        @Builder.Default
        private List<String> hiddenConditions = new ArrayList<>();
        
        // Exact extracted policy contract parameters
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

        private PolicyScores scores;
        private String simplifiedSummary;
        private String analyzedAt;
        private String sourceFile;
        private Double confidence;
        @JsonProperty("isAiGenerated")
        private Boolean isAiGenerated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitingPeriod {
        private String condition;
        private String duration;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rider {
        private String name;
        private String details;
        private String additionalPremium;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyScores {
        private Integer transparency;
        private Integer risk;
        private Integer fairness;
        private Integer claimReliability;
        private Integer overall;
    }
}
