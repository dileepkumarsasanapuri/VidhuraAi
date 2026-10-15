package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured master health insurance policy catalog record.
 * Holds verified product facts, IRDAI registration UIN, eligibility bounds,
 * pricing tables, contract limits, exclusions, and official evidence citations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "structured_policies")
public class StructuredPolicy {

    @Id
    private String id;

    @Indexed(unique = true)
    private String policyCode; // e.g. "HDFC_OPTIMA_SECURE"

    private String policyName; // "HDFC ERGO Optima Secure"
    private String provider; // "HDFC ERGO General Insurance Co. Ltd."
    private String irdaUin; // "HDFHLIP21147V012021"
    private String planName; // "Optima Secure Individual & Family Floater"
    private String category; // "Comprehensive Health Insurance"
    private String description;
    private String policyWordingDocument; // "HDFC_Optima_Secure_Wordings_v2024.pdf"
    private String versionDate; // "2024-Q1"

    @Builder.Default
    private boolean active = true;

    // Eligibility Rules
    private EligibilityRules eligibility;

    // Pricing Model by Age Band & Sum Insured
    @Builder.Default
    private List<SumInsuredTier> sumInsuredTiers = new ArrayList<>();

    // Contract Terms, Limits & Out-of-Pocket Conditions
    private ContractTerms contractTerms;

    // Standard Non-Covered Exclusions
    @Builder.Default
    private List<String> exclusions = new ArrayList<>();

    // Insurer Service, Solvency & Cashless Metrics
    private InsurerMetrics insurerMetrics;

    // Verifiable Document Evidence Citations from Official Policy Wordings
    @Builder.Default
    private List<PolicyEvidenceItem> documentEvidence = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityRules {
        private int minAdultAge; // e.g. 18
        private int maxAdultAge; // e.g. 65 (or 99 for no max limit)
        private int minChildAgeDays; // e.g. 91
        private int maxChildAgeYears; // e.g. 25
        @Builder.Default
        private List<String> allowedFamilyTypes = new ArrayList<>(); // INDIVIDUAL, SELF_AND_SPOUSE, FAMILY_WITH_CHILDREN, PARENTS
        private long minSumInsured;
        private long maxSumInsured;
        @Builder.Default
        private List<String> allowedZones = new ArrayList<>(); // Zone 1, Zone 2, Zone 3
        private int maxAdults;
        private int maxChildren;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SumInsuredTier {
        private long sumInsured; // 500000, 1000000, 2500000, 5000000
        private String formattedSumInsured; // "₹10 Lakh"
        // Base annual premiums for adult age brackets (Zone 1):
        private double premiumAge18To35;
        private double premiumAge36To45;
        private double premiumAge46To55;
        private double premiumAge56To65;
        private double premiumAge66Plus;
        // Floater multiplier factors
        private double spouseLoadingFactor; // e.g. 0.70 (70% extra for spouse)
        private double childLoadingFactor;  // e.g. 0.30 (30% extra per child)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractTerms {
        private String roomRentLimit; // "No Limit (Single Private AC Room or any higher room)"
        private boolean roomRentCapped; // false
        private boolean proportionateDeductionApplies; // false
        private String roomRentRiskExplanation;

        private double copayPercentage; // 0.0 (0%)
        private String copayNote; // "No mandatory co-payment across any age or network hospital"
        private boolean seniorCitizenCopayApplies;

        private double deductibleAmount; // 0.0

        @Builder.Default
        private List<DiseaseSublimit> diseaseSublimits = new ArrayList<>();

        private int waitingPeriodInitialDays; // 30 days
        private int waitingPeriodSpecificMonths; // 24 months
        private int waitingPeriodPedMonths; // 36 months (or 24 months)
        private boolean pedReductionAvailable;

        private int preHospitalizationDays; // 60 days
        private int postHospitalizationDays; // 180 days

        private String restorationBenefit; // "100% Instant recharge for same or different illness, unlimited times per year"
        private String noClaimBonus; // "50% increase in sum insured for each claim-free year up to 100%"

        private boolean maternityCovered;
        private int maternityWaitingMonths;
        private boolean opdCovered;
        private int dayCareProceduresCount; // e.g. 540
        private boolean cashlessEverywhere; // true
        private boolean ambulanceCovered;
        private boolean organDonorCovered;
        private boolean modernTreatmentsCovered; // robotic, stem cell, etc.
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiseaseSublimit {
        private String diseaseName; // "Cataract"
        private double sublimitAmount; // 40000.0 (or -1 for unlimited)
        private String formattedLimit; // "₹40,000 per eye"
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsurerMetrics {
        private double claimSettlementRatio; // 98.4%
        private double incurredClaimsRatio; // 82.1%
        private int cashlessHospitalsCount; // 14500
        @Builder.Default
        private List<String> topHospitalNetworks = new ArrayList<>(); // Apollo, Fortis, Manipal, Max, Narayana
        private double avgGrievanceResolutionDays; // 1.8 days
        private String claimPaymentSpeed; // "Average cashless authorization within 45 minutes"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyEvidenceItem {
        private String clauseName; // "In-patient Hospitalization & Room Rent"
        private String sectionReference; // "Section 4.1 (a)"
        private String documentName; // "HDFC_Optima_Secure_Wordings_v2024.pdf"
        private String verbatimText; // Exact quote from document
        private String verifiedFact; // Human plain language fact
        private String tag; // ROOM_RENT, PED, RESTORATION, COPAY, SUBLIMIT, CLAIM
    }
}
