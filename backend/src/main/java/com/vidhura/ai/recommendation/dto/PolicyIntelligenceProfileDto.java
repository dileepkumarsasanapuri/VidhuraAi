package com.vidhura.ai.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unified Policy Intelligence Profile Aggregator DTO.
 * Combines policy contractual ground truth, insurer statutory performance,
 * hospital network availability, authentic/modelled quotes, benchmark claim simulation,
 * and evidence quality audit scorecard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyIntelligenceProfileDto {

    private PolicySuitabilityDto policySuitability;
    private EvidenceCompletenessDto evidenceCompleteness;
    private PolicyContractOverviewDto contractOverview;
    private ExternalEvidenceDto.InsurerPerformanceSummaryDto insurerPerformance;
    private ExternalEvidenceDto.HospitalNetworkSummaryDto hospitalAccessibility;
    private ExternalEvidenceDto.QuoteDetailDto premiumQuotation;
    private BenchmarkClaimSimulationSummaryDto benchmarkClaimSimulation;
    private EvidenceQualityScorecardDto evidenceQualityScorecard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicySuitabilityDto {
        private double suitabilityScore; // 0 to 100
        private String suitabilityRating; // EXCELLENT_FIT, STRONG_FIT, MODERATE_FIT, CONDITIONAL_FIT
        private String suitabilitySummary;
        private List<String> keyFitFactors;
        private List<String> potentialGaps;
        private boolean independentOfExternalEvidence; // strictly true: missing external evidence never degrades contract fit
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceCompletenessDto {
        private double completenessScore; // 0 to 100
        private String completenessStatus; // COMPLETE, HIGH, PARTIAL, LIMITED
        private boolean contractualTermsVerified;
        private boolean statutoryPerformanceVerified;
        private boolean authenticQuoteVerified;
        private boolean hospitalNetworkVerified;
        private int verifiedCount;
        private int totalRequiredCount;
        private String auditSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyContractOverviewDto {
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String planName;
        private String category;
        private String description;
        private boolean roomRentCapped;
        private BigDecimal roomRentCapPerDay;
        private boolean proportionateDeductionApplies;
        private String roomRentLimitDescription;
        private BigDecimal copayPercentage;
        private String copayDescription;
        private BigDecimal deductibleAmount;
        private int waitingPeriodInitialDays;
        private int waitingPeriodPedMonths;
        private int waitingPeriodSpecificIllnessMonths;
        private boolean dayCareTreatmentsCovered;
        private boolean restorationBenefit;
        private String cumulativeBonusDescription;
        private String officialDocumentUrl;
        private String policyWordingDocument;
        private String regulatoryFilingSource;
        private boolean syntheticFixture;
        private String verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkClaimSimulationSummaryDto {
        private String diagnosis;
        private BigDecimal totalSubmittedBill;
        private BigDecimal estimatedInsurerPayment;
        private BigDecimal estimatedPatientLiability;
        private boolean isReconciled;
        private BigDecimal roomRentExcessDeduction;
        private BigDecimal proportionateDeduction;
        private BigDecimal nonMedicalConsumablesDeduction;
        private BigDecimal copayDeduction;
        private String contractualExplanation;
        private List<String> clauseCitations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceQualityScorecardDto {
        private String contractualGroundTruthStatus;
        private String externalStatutoryEvidenceStatus;
        private String quoteVerificationStatus;
        private String hospitalRegistryStatus;
        private double overallQualityScore;
        private List<String> verifiedDisclosures;
        private List<String> pendingOrMissingDisclosures;
        private String statutoryDocumentHash;
        private String lastAuditedDate;
        private String disclaimer;
    }
}
