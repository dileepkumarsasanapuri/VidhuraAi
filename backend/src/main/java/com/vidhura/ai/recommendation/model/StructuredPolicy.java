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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String documentClassification = "POLICY_WORDING"; // POLICY_WORDING, PRODUCT_CATALOGUE, CUSTOMER_INFORMATION_SHEET, REGULATORY_FILING
    @Builder.Default
    private String verificationStatus = "VERIFIED_CONTRACTUAL_SPECIFICATION";
    private String uinRevision; // e.g. "V012021"
    @Builder.Default
    private String regulatoryFilingSource = "IRDAI Health Product Registry & Insurer Public Disclosure";
    @Builder.Default
    private String officialDocumentUrl = "https://irdai.gov.in";
    private String lastAuditedDate; // e.g. "2024-Q1"

    // Cryptographic & Reviewer Provenance Identification
    @Builder.Default
    private boolean syntheticFixture = false; // true if this is a synthetic demonstration fixture
    private String documentChecksum; // 64-character SHA-256 integrity checksum
    private String sourceRecordId; // Reference to acquired source document / filing
    @Builder.Default
    private String reviewerApprovalStatus = "PENDING_REVIEW"; // REVIEWER_APPROVED, PENDING_REVIEW, SYNTHETIC_DEMO

    // Discovery & Ingestion Audit Trail - Decoupled Contract vs Pricing Confidence
    @Builder.Default
    private double contractConfidenceScore = 100.0; // 0-100 score based purely on verified contractual clauses
    @Builder.Default
    private double pricingConfidenceScore = 100.0; // 0-100 score based on actuarial rate table calibration
    @Builder.Default
    private String pricingStatus = "CALIBRATED_ACTUARIAL_TABLE"; // CALIBRATED_ACTUARIAL_TABLE, MODELED_ESTIMATE_ONLY, UNVERIFIED_PRICING_TABLE
    @Builder.Default
    private List<String> contractUncertainFields = new ArrayList<>(); // contractual ambiguities (e.g. UNCERTAIN_ROOM_RENT_CLAUSE)
    @Builder.Default
    private List<String> pricingUncertainFields = new ArrayList<>(); // pricing ambiguities (e.g. UNVERIFIED_PROSPECTUS_RATES)

    // Backward-compatible aliases
    @Builder.Default
    private double confidenceScore = 100.0; // 0-100 quality confidence score
    @Builder.Default
    private List<String> uncertainFields = new ArrayList<>(); // fields flagged for human review before publication
    private String auditNotes;
    private String verifiedBy;
    private Instant publicationDate;
    @Builder.Default
    private String discoverySource = "OFFICIAL_FILING"; // IRDAI_REGISTRY, INSURER_PORTAL, POLICY_WORDING_PDF, MANUAL_INGESTION
    @Builder.Default
    private List<AuditLogEntry> auditHistory = new ArrayList<>();

    private PolicyChangeImpactReport changeImpactReport;

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

        // Dated Regulatory Metrics Provenance
        @Builder.Default
        private String csrMetricName = "Claim Settlement Ratio (Settled vs Reported)";
        @Builder.Default
        private String csrReportingPeriod = "IRDAI Annual Report FY 2022-23";
        @Builder.Default
        private String csrSource = "IRDAI Annual Report & Form NL-25 Public Disclosure";
        @Builder.Default
        private String csrCalculationMethod = "Claims Settled / Total Claims Reported during the Financial Year";
        @Builder.Default
        private String networkAsOfDate = "March 2024";
        @Builder.Default
        private String networkSource = "Insurer Cashless Everywhere Network Directory";
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
        @Builder.Default
        private String evidenceConfidence = "VERIFIED_CONTRACT_CLAUSE";
        @Builder.Default
        private String sourceDocumentVersion = "v2024.1";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogEntry {
        private Instant timestamp;
        private String action; // DISCOVERED, VERIFIED_PUBLISHED, FLAGGED_OUTDATED, REJECTED_FOR_REVISION
        private String performedBy;
        private String previousStatus;
        private String newStatus;
        private String notes;
        @Builder.Default
        private Map<String, Object> changes = new HashMap<>();
    }

    public boolean isAuthenticAcquiredDocument() {
        if (syntheticFixture) return false;
        boolean isApproved = "VERIFIED_OFFICIAL_CATALOG".equalsIgnoreCase(verificationStatus)
                || "REVIEWER_APPROVED".equalsIgnoreCase(reviewerApprovalStatus);
        boolean hasReviewer = verifiedBy != null && !verifiedBy.trim().isBlank();
        boolean hasSourceUrl = officialDocumentUrl != null && !officialDocumentUrl.trim().isBlank();
        boolean hasSourceRecord = sourceRecordId != null && !sourceRecordId.trim().isBlank();
        String checksum = getEffectiveDocumentChecksum();
        boolean hasValidChecksum = checksum != null && checksum.matches("^[a-fA-F0-9]{64}$")
                && !isPlaceholderChecksum(checksum);
        return isApproved && hasReviewer && hasSourceUrl && hasSourceRecord && hasValidChecksum;
    }

    public static boolean isPlaceholderChecksum(String checksum) {
        if (checksum == null) return true;
        String clean = checksum.toLowerCase().trim();
        if (clean.length() != 64) return true;

        // Check for single repeated character: e.g. "00000...", "aaaaa..."
        char first = clean.charAt(0);
        boolean allSame = true;
        for (int i = 1; i < 64; i++) {
            if (clean.charAt(i) != first) {
                allSame = false;
                break;
            }
        }
        if (allSame) return true;

        // Check for standard placeholder prefix patterns
        if (clean.startsWith("a1b2c3d4") || clean.startsWith("b2c3d4e5") || clean.startsWith("c3d4e5f6")
                || clean.startsWith("12345678") || clean.startsWith("abcdef12") || clean.startsWith("01234567")) {
            return true;
        }

        // Check for periodic 32-char repeat patterns (such as duplicate halves)
        if (clean.substring(0, 32).equals(clean.substring(32, 64))) {
            return true;
        }

        return false;
    }

    public String getEffectiveDocumentChecksum() {
        if (documentChecksum != null && !documentChecksum.isBlank()) {
            return documentChecksum.replaceFirst("^(?i)sha256:", "").trim();
        }
        if (policyWordingDocument != null && policyWordingDocument.toLowerCase().startsWith("sha256:")) {
            return policyWordingDocument.substring(7).trim();
        }
        return null;
    }
}
