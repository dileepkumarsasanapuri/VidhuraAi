package com.vidhura.ai.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Objects for the Deterministic Claim Simulator.
 * Provides structured, decimal-safe inputs and explainable, clause-grounded outputs.
 */
public class ClaimSimulationDto {

    public enum BillItemCategory {
        ROOM_RENT,
        ICU_CHARGES,
        NURSING_CHARGES,
        SURGEON_FEES,
        OT_CHARGES,
        MEDICINES,
        DIAGNOSTICS,
        CONSUMABLES,
        IMPLANTS,
        AMBULANCE,
        OTHER
    }

    public enum CalculationStatus {
        FULL_DETERMINISTIC_VERIFIED,
        PARTIAL_ESTIMATE,
        MANUAL_REVIEW_REQUIRED,
        POLICY_UNAPPROVED_REJECTED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillLineItemDto {
        @NotBlank(message = "Line item ID is required")
        private String lineId;

        @NotNull(message = "Category is required")
        private BillItemCategory category;

        @NotBlank(message = "Description is required")
        private String description;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price must be non-negative")
        private BigDecimal unitPrice;

        @Builder.Default
        private int quantity = 1;

        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Total amount must be non-negative")
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimSimulationRequest {
        @NotBlank(message = "Policy ID or Code is required")
        private String policyId;

        private String uinRevision; // Optional: specify exact revision e.g. "V042122"

        private int patientAge;

        @NotBlank(message = "Diagnosis/treatment name is required")
        private String diagnosis;

        @Builder.Default
        private boolean accidentalInjury = false;

        @Builder.Default
        private boolean preExistingCondition = false;

        @Builder.Default
        private int policyContinuityMonths = 12; // Months of active continuous cover

        private LocalDate admissionDate;
        private LocalDate dischargeDate;

        @Builder.Default
        private String hospitalType = "NETWORK_CASHLESS"; // NETWORK_CASHLESS, NON_NETWORK_REIMBURSEMENT

        @Builder.Default
        private String hospitalRoomCategoryOpted = "SINGLE_PRIVATE_AC"; // GENERAL_WARD, TWIN_SHARING, SINGLE_PRIVATE_AC, DELUXE_SUITE

        @NotNull(message = "Selected Sum Insured is required")
        @DecimalMin(value = "100000.0", message = "Sum insured must be at least ₹1,00,000")
        private BigDecimal selectedSumInsured;

        @Builder.Default
        private BigDecimal priorClaimsInCurrentYear = BigDecimal.ZERO;

        @Builder.Default
        private boolean subsequentClaimForSameIllness = false;

        @NotEmpty(message = "At least one bill line item is required")
        @Builder.Default
        private List<BillLineItemDto> billLines = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeductionItemDto {
        private String deductionType; // NON_MEDICAL_CONSUMABLES, WAITING_PERIOD_INELIGIBLE, ROOM_RENT_EXCESS, PROPORTIONATE_DEDUCTION, DISEASE_SUBLIMIT_EXCESS, SUM_INSURED_EXHAUSTION, DEDUCTIBLE, COPAYMENT
        private String expenseCategory;
        private BigDecimal amountDeducted;
        private String explanation;
        private String calculationFormula;
        private String sectionReference;
        private String verbatimClauseText;
        private String clauseConfidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimSimulationResponse {
        private String simulationId;
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String sourceDocumentHash;
        private String ruleSetVersion;
        private String calculationEngineVersion;

        private CalculationStatus calculationStatus;
        private String statusReason;

        // Decimal-safe reconciliation totals
        private BigDecimal totalSubmittedBill;
        private BigDecimal totalEligibleExpenses;
        private BigDecimal totalNonCoveredExpenses;
        private BigDecimal totalDeductions;
        private BigDecimal estimatedInsurerPayment;
        private BigDecimal estimatedPatientLiability;

        // Mathematical identity guarantee string
        private String reconciliationIdentity; // "Patient Liability (₹X) = Submitted Bill (₹Y) - Insurer Payment (₹Z)"
        private boolean reconciled; // true if patientLiability.add(insurerPayment) == submittedBill

        // Restoration details
        private boolean restorationTriggered;
        private BigDecimal restorationAmountGranted;
        private String restorationExplanation;

        @Builder.Default
        private List<DeductionItemDto> deductionBreakdown = new ArrayList<>();

        @Builder.Default
        private List<String> unresolvedRules = new ArrayList<>();

        @Builder.Default
        private List<String> appliedClauses = new ArrayList<>();

        private String legalDisclaimer;
        private Instant evaluatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedScenarioDetailDto {
        private String scenarioId;
        private String userId;
        private String policyId;
        private String policyCode;
        private String irdaUin;
        private String uinRevision;
        private int patientAge;
        private String diagnosis;
        private boolean accidentalInjury;
        private boolean preExistingCondition;
        private int policyContinuityMonths;
        private LocalDate admissionDate;
        private LocalDate dischargeDate;
        private String hospitalType;
        private String hospitalRoomCategoryOpted;
        private BigDecimal selectedSumInsured;
        private BigDecimal totalSubmittedBill;
        private boolean syntheticExample;
        private Instant createdAt;
        @Builder.Default
        private List<BillLineItemDto> billLines = new ArrayList<>();
        private ClaimSimulationResponse result;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleScenarioDto {
        private String sampleId;
        private String title;
        private String scenarioType;
        private String description;
        private String clinicalNarrative;
        private String targetPolicyCode;
        private String targetPolicyName;
        private ClaimSimulationRequest request;
        @Builder.Default
        private boolean syntheticExample = true;
        @Builder.Default
        private String disclaimer = "SYNTHETIC DEMO SCENARIO: For educational and illustrative purposes only. Not an actual claim, insurer settlement, or official guarantee.";
        private String educationalTakeaway;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimComparisonRequest {
        @NotEmpty(message = "At least two policies must be selected for comparison")
        private List<String> policyIds;

        @NotNull(message = "Hospitalization scenario parameters are required")
        @Valid
        private ClaimSimulationRequest scenario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyClaimComparisonItemDto {
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String sourceDocumentHash;
        private boolean syntheticExample;
        private CalculationStatus calculationStatus;
        private boolean reconciled;

        private BigDecimal totalSubmittedBill;
        private BigDecimal totalEligibleExpenses;
        private BigDecimal totalNonCoveredExpenses;
        private BigDecimal totalDeductions;
        private BigDecimal estimatedInsurerPayment;
        private BigDecimal estimatedPatientLiability;

        // Breakdown aggregates for fast side-by-side comparison
        private BigDecimal roomRentDeductions;
        private BigDecimal sublimitDeductions;
        private BigDecimal costSharingDeductions; // copay + deductible
        private BigDecimal waitingPeriodDeductions;

        private boolean restorationTriggered;
        private BigDecimal restorationAmountGranted;
        private String restorationExplanation;

        @Builder.Default
        private List<String> unresolvedRules = new ArrayList<>();
        private int appliedClauseCount;
        private ClaimSimulationResponse fullSimulation;

        // Evidence Layer & Provenance Fields
        private boolean authenticAcquiredDocument;
        private String verificationStatus;
        private String reviewerApprovalStatus;
        private String verifiedBy;
        private String roomRentLimit;
        private boolean roomRentCapped;
        private boolean proportionateDeductionApplies;
        private Double copayPercentage;
        private Double deductibleAmount;
        private Integer waitingPeriodInitialDays;
        private Integer waitingPeriodPedMonths;
        private Integer waitingPeriodSpecificMonths;
        @Builder.Default
        private List<String> keyExclusions = new ArrayList<>();
        private String uncertaintyNotice;
        @Builder.Default
        private List<com.vidhura.ai.recommendation.model.StructuredPolicy.PolicyEvidenceItem> documentEvidence = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClauseDeltaDto {
        private String feature;
        private String policyAValue;
        private String policyBValue;
        private String financialImpact;
        private String clauseCitationA;
        private String clauseCitationB;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimComparisonResponseDto {
        private String scenarioDiagnosis;
        private BigDecimal totalSubmittedBill;
        @Builder.Default
        private List<PolicyClaimComparisonItemDto> comparisons = new ArrayList<>();
        private String highestPayoutPolicyName;
        private String lowestOutOfPocketPolicyName;
        private BigDecimal payoutVariance;
        private BigDecimal patientLiabilityVariance;
        private String varianceExplanation;
        @Builder.Default
        private List<ClauseDeltaDto> clauseDeltas = new ArrayList<>();
        @Builder.Default
        private String disclaimer = "SCENARIO-BASED COMPARATIVE ESTIMATE: These figures are comparative estimates grounded in versioned contractual policy clauses for educational purposes, not guarantees of claim settlement.";
        private Instant evaluatedAt;
    }
}
