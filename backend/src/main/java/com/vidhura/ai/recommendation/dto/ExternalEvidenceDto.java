package com.vidhura.ai.recommendation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.vidhura.ai.recommendation.model.HospitalNetworkNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExternalEvidenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestEvidenceRequest {
        @NotBlank(message = "Evidence type is required (e.g. IRDAI_ANNUAL_REPORT, NL_DISCLOSURE)")
        private String evidenceType;

        @NotBlank(message = "Insurer code is required (e.g. HDFC_ERGO, STAR_HEALTH)")
        private String insurerCode;

        private String insurerName;

        @NotBlank(message = "Metric category is required (e.g. SETTLEMENT_RATIO_LESS_3_MONTHS, INCURRED_CLAIM_RATIO)")
        private String metricCategory;

        @NotBlank(message = "Metric key is required")
        private String metricKey;

        @NotBlank(message = "Formatted metric value is required (e.g. '98.40%')")
        private String metricValue;

        @NotNull(message = "Numeric value is required for quantitative ranking")
        private BigDecimal numericValue;

        private String metricUnit;

        @NotBlank(message = "Reporting period is required (e.g. FY2023-24)")
        private String reportingPeriod;

        private LocalDate publicationDate;

        @NotBlank(message = "Source document title is required")
        private String sourceDocumentTitle;

        @NotBlank(message = "Canonical source URL is required")
        private String sourceUrl;

        @NotBlank(message = "Valid 64-character SHA-256 cryptographic hash of source filing is required")
        private String sourceDocumentHash;

        private Integer citationPageNumber;
        private String citationTableReference;
        private String verificationNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyEvidenceRequest {
        @NotBlank(message = "Verification status is required: VERIFIED_STATUTORY or REJECTED")
        private String verificationStatus;

        private String verificationNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRecordDto {
        private String id;
        private String evidenceType;
        private String insurerCode;
        private String insurerName;
        private String metricCategory;
        private String metricKey;
        private String metricValue;
        private BigDecimal numericValue;
        private String metricUnit;
        private String reportingPeriod;
        private LocalDate publicationDate;
        private String sourceDocumentTitle;
        private String sourceUrl;
        private String sourceDocumentHash;
        private Integer citationPageNumber;
        private String citationTableReference;
        private String verificationStatus;
        private String verifiedBy;
        private Instant verifiedAt;
        private String verificationNotes;
        private Instant ingestionTimestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsurerPerformanceSummaryDto {
        private String insurerCode;
        private String insurerName;
        private String reportingPeriod;

        // Metric fields extracted from verified statutory filings
        private String incurredClaimRatio;
        private BigDecimal incurredClaimRatioValue;

        private String settlementRatioLessThan3Months;
        private BigDecimal settlementRatioLessThan3MonthsValue;

        private String annualSettlementRatio;
        private BigDecimal annualSettlementRatioValue;

        private String repudiationRatio;
        private BigDecimal repudiationRatioValue;

        private String complaintsPer10kClaims;
        private BigDecimal complaintsPer10kClaimsValue;

        private String solvencyRatio;
        private BigDecimal solvencyRatioValue;

        private String claimTurnaroundDays;
        private BigDecimal claimTurnaroundDaysValue;

        private String overallPerformanceRating; // EXCELLENT, VERY_GOOD, STANDARD, REQUIRES_ATTENTION
        private String statutorySourceTitle;
        private String statutoryDocumentHash;
        private String sourceUrl;
        private String verificationStatus;
        private boolean verifiedStatutory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalNodeDto {
        private String hospitalId;
        private String hospitalName;
        private String registryRohiniCode;
        private String address;
        private String city;
        private String pinCode;
        private Double latitude;
        private Double longitude;
        private String category;
        private boolean cashlessActive;
        private String tier;
        private LocalDate agreementValidUntil;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalNetworkSummaryDto {
        private String insurerCode;
        private String city;
        private String pinCode;
        private int totalNetworkHospitalsInCity;
        private int preferredTier1Count;
        private int secondaryCount;
        private double cashlessAccessibilityScore; // 0 to 100
        private String verificationBadge; // "VERIFIED_HOSPITAL_REGISTRY" or "UNVERIFIED"
        private String networkDisclaimer;

        @Builder.Default
        private List<HospitalNodeDto> hospitals = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestHospitalRequest {
        @NotBlank(message = "Hospital ID is required")
        private String hospitalId;

        @NotBlank(message = "Hospital name is required")
        private String hospitalName;

        @NotBlank(message = "ROHINI code is required")
        private String registryRohiniCode;

        private String address;

        @NotBlank(message = "City is required")
        private String city;

        private String state;

        @NotBlank(message = "Pincode is required")
        private String pinCode;

        private Double latitude;
        private Double longitude;
        private String category;

        @NotNull(message = "Insurer agreement list is required")
        @Builder.Default
        private List<HospitalNetworkNode.CashlessAgreement> cashlessAgreements = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteDetailDto {
        private String quoteId;
        private String policyCode;
        private String uinRevision;
        private String insurerCode;
        private BigDecimal sumInsured;
        private int age;
        private String pinCode;
        private String zone;
        private String familyConstruct;
        private BigDecimal quotedAnnualPremium;
        private BigDecimal quotedBasePremium;
        private BigDecimal quotedGst;
        private String quotingChannel;
        private Instant quoteTimestamp;
        private String quoteReferenceNumber;
        private String sourceDocumentHash;
        private boolean isAuthentic;
        private String verificationStatus;
        private String formattedAnnualPremium;
        private boolean isStale;
    }
}
