package com.vidhura.ai.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for automated policy discovery, ingestion,
 * field uncertainty detection, and publication verification.
 */
public class PolicyDiscoveryDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyDiscoveryRequest {
        private String documentUrl;
        private String documentName;
        @Builder.Default
        private String sourceType = "POLICY_WORDING_PDF"; // IRDAI_REGISTRY, INSURER_PORTAL, POLICY_WORDING_PDF, MANUAL_INGESTION
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String rawPolicyText;
        @Builder.Default
        private boolean autoPublishIfHighConfidence = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyVerificationRequest {
        @Builder.Default
        private String decision = "APPROVE"; // APPROVE, REJECT, FLAG_OUTDATED, FLAG_INCOMPLETE
        private String reviewerName;
        private String reviewerNotes;
        @Builder.Default
        private Map<String, Object> overrideFields = new HashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyIngestionResultDto {
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String verificationStatus;
        private String documentClassification; // POLICY_WORDING, PRODUCT_CATALOGUE, CUSTOMER_INFORMATION_SHEET, REGULATORY_FILING
        // Decoupled confidence metrics
        private double contractConfidenceScore; // 0-100 score on contractual clauses
        private double pricingConfidenceScore;  // 0-100 score on actuarial rate table
        private String pricingStatus;           // CALIBRATED_ACTUARIAL_TABLE, MODELED_ESTIMATE_ONLY, UNVERIFIED_PRICING_TABLE
        private double confidenceScore;         // legacy composite score
        private boolean readyForPublication;    // false on discovery: strict human verification gate enforced
        private boolean requiresHumanVerification; // always true for newly discovered extractions
        @Builder.Default
        private List<String> contractUncertainFields = new ArrayList<>();
        @Builder.Default
        private List<String> pricingUncertainFields = new ArrayList<>();
        @Builder.Default
        private List<String> uncertainFields = new ArrayList<>(); // merged legacy list
        @Builder.Default
        private List<String> criticalAuditFindings = new ArrayList<>();
        @Builder.Default
        private Map<String, Object> extractedSummary = new HashMap<>();
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogStatsDto {
        private long totalPolicies;
        private long verifiedPublishedPolicies;
        private long pendingReviewPolicies;
        private long draftPolicies;
        private long flaggedOrOutdatedPolicies;
    }
}
