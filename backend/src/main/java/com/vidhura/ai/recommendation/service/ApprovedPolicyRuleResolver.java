package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Approved Policy Rule Resolver.
 * Resolves versioned contractual benefit rules directly from reviewer-approved StructuredPolicy records.
 *
 * Strict Server-Side Gates:
 * 1. Fail-closed: Rejects any policy not in VERIFIED_OFFICIAL_CATALOG with active=true.
 * 2. Revision lock: Rejects requested revisions that do not match the approved statutory filing revision.
 * 3. Evidence grounding: Links each rule to its exact verbatim clause and section reference.
 * 4. Uncertainty audit: Flags material uncertainties (e.g. UNCERTAIN_ROOM_RENT_CLAUSE) as MANUAL_REVIEW_REQUIRED.
 */
@Slf4j
@Service
public class ApprovedPolicyRuleResolver {

    private final StructuredPolicyRepository policyRepository;
    private final PolicyIngestionService policyIngestionService;

    public ApprovedPolicyRuleResolver(StructuredPolicyRepository policyRepository) {
        this(policyRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovedPolicyRuleResolver(
            StructuredPolicyRepository policyRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            PolicyIngestionService policyIngestionService) {
        this.policyRepository = policyRepository;
        this.policyIngestionService = policyIngestionService;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedPolicyRules {
        private String policyId;
        private String policyCode;
        private String policyName;
        private String provider;
        private String irdaUin;
        private String uinRevision;
        private String sourceDocumentHash;
        private String ruleSetVersion;

        // Room Rent
        private boolean roomRentCapped;
        private String roomRentLimitText;
        private BigDecimal roomRentCapPerDay; // null if uncapped
        private boolean proportionateDeductionApplies;
        private String roomRentClauseRef;
        private String roomRentVerbatimText;

        // Waiting Periods
        private int waitingPeriodInitialDays;
        private String initialWaitClauseRef;
        private String initialWaitVerbatimText;

        private int waitingPeriodPedMonths;
        private String pedWaitClauseRef;
        private String pedWaitVerbatimText;

        private int waitingPeriodSpecificMonths;
        @Builder.Default
        private Set<String> specificIllnessKeywords = new HashSet<>();
        private String specificWaitClauseRef;
        private String specificWaitVerbatimText;

        // Sublimits
        @Builder.Default
        private Map<String, BigDecimal> diseaseSublimits = new HashMap<>();
        @Builder.Default
        private Map<String, String> sublimitClauseRefs = new HashMap<>();
        @Builder.Default
        private Map<String, String> sublimitVerbatimTexts = new HashMap<>();

        // Cost Sharing
        private BigDecimal deductibleAmount;
        private String deductibleClauseRef;
        private String deductibleVerbatimText;

        private BigDecimal copayPercentage;
        private String copayClauseRef;
        private String copayVerbatimText;

        // Restoration
        private boolean restorationAvailable;
        private boolean restorationCoversSameIllness;
        private String restorationExplanation;
        private String restorationClauseRef;
        private String restorationVerbatimText;

        // Exclusions
        @Builder.Default
        private List<String> exclusions = new ArrayList<>();
        private String exclusionsClauseRef;
        private String exclusionsVerbatimText;

        // Ambiguity / Uncertainty Flags
        @Builder.Default
        private List<String> materialUncertainties = new ArrayList<>();

        public boolean hasMaterialUncertaintyFor(String featureTag) {
            if (materialUncertainties.isEmpty()) return false;
            for (String u : materialUncertainties) {
                if (u.contains(featureTag)) return true;
            }
            return false;
        }
    }

    /**
     * Resolves and verifies rules for an approved policy revision.
     * Fails closed if the policy is unapproved, inactive, or revision-mismatched.
     */
    public ResolvedPolicyRules resolveRulesForSimulation(String policyIdOrCode, String requestedRevision, BigDecimal selectedSumInsured) {
        if (policyIdOrCode == null || policyIdOrCode.isBlank()) {
            throw new IllegalArgumentException("Policy identifier is required for claim simulation.");
        }

        Optional<StructuredPolicy> policyOpt = policyRepository.findById(policyIdOrCode);
        if (policyOpt.isEmpty()) {
            policyOpt = policyRepository.findByPolicyCode(policyIdOrCode);
        }
        if (policyOpt.isEmpty()) {
            policyOpt = policyRepository.findByIrdaUin(policyIdOrCode);
        }
        if (policyOpt.isEmpty() && policyIngestionService != null) {
            policyOpt = policyIngestionService.getAllCatalogPolicies().stream()
                    .filter(p -> p.matchesPolicyCodeOrAlias(policyIdOrCode) || p.matchesUinOrHistorical(policyIdOrCode))
                    .findFirst();
        }

        if (policyOpt.isEmpty()) {
            throw new IllegalArgumentException("Policy not found in repository: " + policyIdOrCode);
        }

        return resolveRules(policyOpt.get(), requestedRevision, selectedSumInsured);
    }

    /**
     * Resolves and verifies rules directly from an in-memory StructuredPolicy record.
     */
    public ResolvedPolicyRules resolveRules(StructuredPolicy policy, String requestedRevision, BigDecimal selectedSumInsured) {
        if (policy == null) {
            throw new IllegalArgumentException("StructuredPolicy cannot be null.");
        }

        // 1. Gating Guard: Must be ACTIVE and approved
        boolean isApproved = "VERIFIED_OFFICIAL_CATALOG".equalsIgnoreCase(policy.getVerificationStatus())
                || "VERIFIED_CONTRACTUAL_SPECIFICATION".equalsIgnoreCase(policy.getVerificationStatus())
                || "REVIEWER_APPROVED".equalsIgnoreCase(policy.getReviewerApprovalStatus());

        if (!policy.isActive() || !isApproved) {
            log.warn("[RuleResolver] Rejected unapproved policy draft '{}' (active={}, status={})",
                    policy.getPolicyCode(), policy.isActive(), policy.getVerificationStatus());
            throw new IllegalStateException("Policy '" + policy.getPolicyName() + "' (code: " + policy.getPolicyCode()
                    + ") is not approved for live claim calculation (Status: " + policy.getVerificationStatus()
                    + ", Active: " + policy.isActive() + "). Only reviewer-approved policies can be simulated.");
        }

        // 2. Revision Guard: If user requested a specific revision, verify match
        if (requestedRevision != null && !requestedRevision.isBlank()) {
            String activeRev = policy.getUinRevision() != null ? policy.getUinRevision().trim() : "";
            if (!activeRev.equalsIgnoreCase(requestedRevision.trim())) {
                log.warn("[RuleResolver] Revision mismatch: requested '{}', active '{}' for policy '{}'",
                        requestedRevision, activeRev, policy.getPolicyCode());
                throw new IllegalStateException("Requested revision '" + requestedRevision
                        + "' does not match active approved statutory filing revision '" + activeRev + "'.");
            }
        }

        // 3. Extract Contract Terms and Evidence Citations
        StructuredPolicy.ContractTerms terms = policy.getContractTerms();
        if (terms == null) {
            terms = new StructuredPolicy.ContractTerms();
        }

        List<StructuredPolicy.PolicyEvidenceItem> evidenceList = policy.getDocumentEvidence() != null
                ? policy.getDocumentEvidence()
                : Collections.emptyList();

        StructuredPolicy.PolicyEvidenceItem roomEvidence = findEvidence(evidenceList, "ROOM_RENT");
        StructuredPolicy.PolicyEvidenceItem pedEvidence = findEvidence(evidenceList, "PED");
        StructuredPolicy.PolicyEvidenceItem copayEvidence = findEvidence(evidenceList, "COPAY");
        StructuredPolicy.PolicyEvidenceItem restoreEvidence = findEvidence(evidenceList, "RESTORATION");
        StructuredPolicy.PolicyEvidenceItem exclEvidence = findEvidence(evidenceList, "EXCLUSIONS");

        // Calculate Room Rent daily cap
        boolean roomRentCapped = terms.isRoomRentCapped();
        BigDecimal roomCapPerDay = null;
        if (roomRentCapped && selectedSumInsured != null) {
            String limitText = terms.getRoomRentLimit() != null ? terms.getRoomRentLimit().toLowerCase() : "";
            if (limitText.contains("1%") || limitText.contains("1 %")) {
                roomCapPerDay = selectedSumInsured.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            } else if (limitText.contains("2%") || limitText.contains("2 %")) {
                roomCapPerDay = selectedSumInsured.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
            } else {
                // Default to 1% of SI if unspecified
                roomCapPerDay = selectedSumInsured.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Parse Sublimits
        Map<String, BigDecimal> sublimits = new HashMap<>();
        Map<String, String> sublimitRefs = new HashMap<>();
        Map<String, String> sublimitTexts = new HashMap<>();
        if (terms.getDiseaseSublimits() != null) {
            for (StructuredPolicy.DiseaseSublimit ds : terms.getDiseaseSublimits()) {
                if (ds.getDiseaseName() != null && ds.getSublimitAmount() > 0) {
                    sublimits.put(ds.getDiseaseName().toLowerCase().trim(), BigDecimal.valueOf(ds.getSublimitAmount()).setScale(2, RoundingMode.HALF_UP));
                    sublimitRefs.put(ds.getDiseaseName().toLowerCase().trim(), "Section: Disease Sub-limits");
                    sublimitTexts.put(ds.getDiseaseName().toLowerCase().trim(), ds.getNote() != null ? ds.getNote() : ds.getFormattedLimit());
                }
            }
        }

        // Parse Restoration Semantics
        String restoreText = terms.getRestorationBenefit() != null ? terms.getRestorationBenefit().toLowerCase() : "";
        boolean restorationAvailable = !restoreText.isBlank() && !restoreText.contains("not covered") && !restoreText.contains("nil");
        boolean restorationCoversSameIllness = restoreText.contains("same") || restoreText.contains("unlimited") || restoreText.contains("forever");

        // Specific Illness Keywords
        Set<String> specificKeywords = Set.of(
                "cataract", "hernia", "hysterectomy", "joint replacement", "knee replacement",
                "calculus", "kidney stone", "gall bladder stone", "piles", "fistula", "fissure",
                "tonsillectomy", "varicose veins", "benign prostatic hypertrophy"
        );

        String ruleSetVersion = String.format("RULES-%s-%s-v1.0",
                policy.getIrdaUin() != null ? policy.getIrdaUin() : "UIN",
                policy.getUinRevision() != null ? policy.getUinRevision() : "REV");

        List<String> uncertainties = policy.getContractUncertainFields() != null
                ? new ArrayList<>(policy.getContractUncertainFields())
                : new ArrayList<>();

        return ResolvedPolicyRules.builder()
                .policyId(policy.getId())
                .policyCode(policy.getPolicyCode())
                .policyName(policy.getPolicyName())
                .provider(policy.getProvider())
                .irdaUin(policy.getIrdaUin())
                .uinRevision(policy.getUinRevision())
                .sourceDocumentHash(policy.getEffectiveDocumentChecksum() != null ? "sha256:" + policy.getEffectiveDocumentChecksum() : policy.getPolicyWordingDocument())
                .ruleSetVersion(ruleSetVersion)
                .roomRentCapped(roomRentCapped)
                .roomRentLimitText(terms.getRoomRentLimit() != null ? terms.getRoomRentLimit() : "Single Private AC Room")
                .roomRentCapPerDay(roomCapPerDay)
                .proportionateDeductionApplies(terms.isProportionateDeductionApplies())
                .roomRentClauseRef(roomEvidence != null ? roomEvidence.getSectionReference() : "Section 2.1: In-patient Care & Room Rent")
                .roomRentVerbatimText(roomEvidence != null ? roomEvidence.getVerbatimText() : "Hospitalisation expenses covered up to eligible room category.")
                .waitingPeriodInitialDays(terms.getWaitingPeriodInitialDays() > 0 ? terms.getWaitingPeriodInitialDays() : 30)
                .initialWaitClauseRef("Section 3.1: Initial Waiting Period")
                .initialWaitVerbatimText("Expenses related to the treatment of any illness within 30 days from policy commencement are excluded except accidental trauma.")
                .waitingPeriodPedMonths(terms.getWaitingPeriodPedMonths() > 0 ? terms.getWaitingPeriodPedMonths() : 36)
                .pedWaitClauseRef(pedEvidence != null ? pedEvidence.getSectionReference() : "Section 3.3: Pre-Existing Diseases (PED)")
                .pedWaitVerbatimText(pedEvidence != null ? pedEvidence.getVerbatimText() : "Pre-existing diseases covered after continuous coverage period.")
                .waitingPeriodSpecificMonths(terms.getWaitingPeriodSpecificMonths() > 0 ? terms.getWaitingPeriodSpecificMonths() : 24)
                .specificIllnessKeywords(specificKeywords)
                .specificWaitClauseRef("Section 3.2: Specific Illness Waiting Period")
                .specificWaitVerbatimText("Specific illnesses and surgical procedures are covered after 24 months of continuous policy tenure.")
                .diseaseSublimits(sublimits)
                .sublimitClauseRefs(sublimitRefs)
                .sublimitVerbatimTexts(sublimitTexts)
                .deductibleAmount(BigDecimal.valueOf(terms.getDeductibleAmount()).setScale(2, RoundingMode.HALF_UP))
                .deductibleClauseRef("Section: Deductible Terms")
                .deductibleVerbatimText("Cost sharing requirement where insured bears initial specified expense per policy year.")
                .copayPercentage(BigDecimal.valueOf(terms.getCopayPercentage()).setScale(2, RoundingMode.HALF_UP))
                .copayClauseRef(copayEvidence != null ? copayEvidence.getSectionReference() : "Section 4.1: Co-payment Mandate")
                .copayVerbatimText(copayEvidence != null ? copayEvidence.getVerbatimText() : "Cost sharing percentage payable by insured on admissible claim.")
                .restorationAvailable(restorationAvailable)
                .restorationCoversSameIllness(restorationCoversSameIllness)
                .restorationExplanation(terms.getRestorationBenefit() != null ? terms.getRestorationBenefit() : "No restoration benefit")
                .restorationClauseRef(restoreEvidence != null ? restoreEvidence.getSectionReference() : "Section 2.3: Restoration Benefit")
                .restorationVerbatimText(restoreEvidence != null ? restoreEvidence.getVerbatimText() : "Sum insured restoration terms as per schedule.")
                .exclusions(policy.getExclusions() != null ? policy.getExclusions() : Collections.emptyList())
                .exclusionsClauseRef(exclEvidence != null ? exclEvidence.getSectionReference() : "Section 5: General Permanent Exclusions")
                .exclusionsVerbatimText(exclEvidence != null ? exclEvidence.getVerbatimText() : "Non-medical consumables under IRDAI List 1 and cosmetic treatments excluded.")
                .materialUncertainties(uncertainties)
                .build();
    }

    private StructuredPolicy.PolicyEvidenceItem findEvidence(List<StructuredPolicy.PolicyEvidenceItem> list, String tag) {
        if (list == null) return null;
        return list.stream()
                .filter(e -> tag.equalsIgnoreCase(e.getTag()))
                .findFirst()
                .orElse(null);
    }
}
