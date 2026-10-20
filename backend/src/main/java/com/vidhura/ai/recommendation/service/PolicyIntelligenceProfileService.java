package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.dto.PolicyIntelligenceProfileDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Aggregator service that produces the unified Policy Intelligence Profile.
 *
 * Reuses existing policy catalog, rule resolution, deterministic claim simulation,
 * and Phase 2 external statutory evidence services to synthesize a complete 360-degree
 * profile for any policy in one single read call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyIntelligenceProfileService {

    private final StructuredPolicyRepository policyRepository;
    private final ApprovedPolicyRuleResolver ruleResolver;
    private final DeterministicClaimEngine claimEngine;
    private final ExternalEvidenceService externalEvidenceService;

    /**
     * Aggregates contractual ground truth, insurer statutory performance,
     * hospital network availability, authentic/modelled quotes, and benchmark claim simulation.
     */
    public PolicyIntelligenceProfileDto getPolicyIntelligenceProfile(
            String policyCodeOrId,
            int age,
            BigDecimal sumInsured,
            String city,
            String pinCode,
            String familyConstruct) {
        return getPolicyIntelligenceProfile(policyCodeOrId, age, sumInsured, city, pinCode, familyConstruct, null);
    }

    /**
     * Overloaded aggregator accepting custom suitability score from recommendation engine
     * while guaranteeing decoupling between Suitability, Evidence Completeness, and Insurer Performance.
     */
    public PolicyIntelligenceProfileDto getPolicyIntelligenceProfile(
            String policyCodeOrId,
            int age,
            BigDecimal sumInsured,
            String city,
            String pinCode,
            String familyConstruct,
            Double customSuitabilityScore) {

        log.info("[PolicyIntelligenceProfile] Aggregating profile for policy='{}', age={}, SI={}, city='{}', customSuitability={}",
                policyCodeOrId, age, sumInsured, city, customSuitabilityScore);

        // 1. Resolve structured policy from repository
        StructuredPolicy policy = policyRepository.findByPolicyCode(policyCodeOrId)
                .or(() -> policyRepository.findById(policyCodeOrId))
                .orElseThrow(() -> new NoSuchElementException("Policy not found with code or ID: " + policyCodeOrId));

        BigDecimal effectiveSI = (sumInsured != null && sumInsured.compareTo(BigDecimal.ZERO) > 0)
                ? sumInsured
                : new BigDecimal("1000000.00");
        int effectiveAge = age > 0 ? age : 35;

        // 2. Resolve contractual rules
        ApprovedPolicyRuleResolver.ResolvedPolicyRules rules =
                ruleResolver.resolveRules(policy, policy.getUinRevision(), effectiveSI);

        // 3. Build Contract Overview
        PolicyIntelligenceProfileDto.PolicyContractOverviewDto contractOverview = buildContractOverview(policy, rules);

        // 4. Retrieve Insurer Statutory Performance from External Evidence Service
        String insurerCode = extractInsurerCode(policy);
        ExternalEvidenceDto.InsurerPerformanceSummaryDto insurerPerformance =
                externalEvidenceService.getInsurerPerformanceMetrics(insurerCode, null);

        // 5. Retrieve Hospital Network Accessibility from External Evidence Service
        ExternalEvidenceDto.HospitalNetworkSummaryDto hospitalAccessibility =
                externalEvidenceService.getHospitalNetworkSummary(insurerCode, city, pinCode);

        // 6. Retrieve Authentic or Modelled Quote
        ExternalEvidenceDto.QuoteDetailDto quotation = externalEvidenceService.getAuthenticOrModelledQuote(
                policy.getPolicyCode(), effectiveAge, effectiveSI, pinCode, familyConstruct);

        // 7. Calculate Benchmark Claim Simulation (Standard 2-Day Laparoscopic Appendectomy Benchmark)
        PolicyIntelligenceProfileDto.BenchmarkClaimSimulationSummaryDto benchmarkClaim =
                simulateStandardBenchmarkClaim(policy, rules, effectiveSI);

        // 8. Decoupled Triad: Build Policy Suitability (Contract fit, independent of external data)
        PolicyIntelligenceProfileDto.PolicySuitabilityDto policySuitability =
                buildPolicySuitability(policy, rules, customSuitabilityScore);

        // 9. Decoupled Triad: Build Evidence Completeness (Depth of verified proof)
        PolicyIntelligenceProfileDto.EvidenceCompletenessDto evidenceCompleteness =
                buildEvidenceCompleteness(policy, insurerPerformance, quotation, hospitalAccessibility);

        // 10. Build Evidence Quality Scorecard
        PolicyIntelligenceProfileDto.EvidenceQualityScorecardDto scorecard =
                buildEvidenceQualityScorecard(policy, insurerPerformance, quotation, hospitalAccessibility);

        return PolicyIntelligenceProfileDto.builder()
                .policySuitability(policySuitability)
                .evidenceCompleteness(evidenceCompleteness)
                .contractOverview(contractOverview)
                .insurerPerformance(insurerPerformance)
                .hospitalAccessibility(hospitalAccessibility)
                .premiumQuotation(quotation)
                .benchmarkClaimSimulation(benchmarkClaim)
                .evidenceQualityScorecard(scorecard)
                .build();
    }

    private PolicyIntelligenceProfileDto.PolicyContractOverviewDto buildContractOverview(
            StructuredPolicy policy, ApprovedPolicyRuleResolver.ResolvedPolicyRules rules) {

        StructuredPolicy.ContractTerms terms = policy.getContractTerms();
        boolean dayCareCovered = terms != null && terms.getDayCareProceduresCount() > 0;
        boolean restoration = terms != null && terms.getRestorationBenefit() != null && !terms.getRestorationBenefit().isBlank();
        String bonus = terms != null && terms.getNoClaimBonus() != null ? terms.getNoClaimBonus() : "No cumulative bonus specified.";

        return PolicyIntelligenceProfileDto.PolicyContractOverviewDto.builder()
                .policyId(policy.getId())
                .policyCode(policy.getPolicyCode())
                .policyName(policy.getPolicyName())
                .provider(policy.getProvider())
                .irdaUin(policy.getIrdaUin())
                .uinRevision(policy.getUinRevision())
                .planName(policy.getPlanName())
                .category(policy.getCategory())
                .description(policy.getDescription())
                .roomRentCapped(rules.isRoomRentCapped())
                .roomRentCapPerDay(rules.getRoomRentCapPerDay())
                .proportionateDeductionApplies(rules.isProportionateDeductionApplies())
                .roomRentLimitDescription(rules.isRoomRentCapped()
                        ? "Capped at ₹" + rules.getRoomRentCapPerDay() + "/day with proportionate deduction on associated hospital charges."
                        : "No room rent capping. Up to single private AC room or sum insured without proportionate deduction penalty.")
                .copayPercentage(rules.getCopayPercentage())
                .copayDescription(rules.getCopayPercentage().compareTo(BigDecimal.ZERO) > 0
                        ? rules.getCopayPercentage() + "% mandatory copay applies on all admissible claims."
                        : "Zero mandatory copay.")
                .deductibleAmount(rules.getDeductibleAmount())
                .waitingPeriodInitialDays(rules.getWaitingPeriodInitialDays())
                .waitingPeriodPedMonths(rules.getWaitingPeriodPedMonths())
                .waitingPeriodSpecificIllnessMonths(rules.getWaitingPeriodSpecificMonths())
                .dayCareTreatmentsCovered(dayCareCovered)
                .restorationBenefit(restoration)
                .cumulativeBonusDescription(bonus)
                .officialDocumentUrl(policy.getOfficialDocumentUrl())
                .policyWordingDocument(policy.getPolicyWordingDocument())
                .regulatoryFilingSource(policy.getRegulatoryFilingSource())
                .syntheticFixture(policy.isSyntheticFixture())
                .verificationStatus(policy.getVerificationStatus())
                .build();
    }

    private PolicyIntelligenceProfileDto.BenchmarkClaimSimulationSummaryDto simulateStandardBenchmarkClaim(
            StructuredPolicy policy, ApprovedPolicyRuleResolver.ResolvedPolicyRules rules, BigDecimal sumInsured) {

        // Construct standard statutory benchmark hospitalization bill (₹1,23,000 total):
        // 2 days room rent @ ₹6,500 = ₹13,000
        // Associated surgeon & OT = ₹78,000
        // Exempt medicines = ₹20,000
        // Consumables = ₹12,000
        List<ClaimSimulationDto.BillLineItemDto> billLines = List.of(
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT).description("Single Room Rent (2 days @ ₹6,500/day)")
                        .unitPrice(new BigDecimal("6500.00")).quantity(2).totalAmount(new BigDecimal("13000.00")).build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("L2").category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES).description("Surgeon & Specialist Professional Fees")
                        .unitPrice(new BigDecimal("50000.00")).quantity(1).totalAmount(new BigDecimal("50000.00")).build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("L3").category(ClaimSimulationDto.BillItemCategory.OT_CHARGES).description("Operation Theatre & Anaesthetist Charges")
                        .unitPrice(new BigDecimal("28000.00")).quantity(1).totalAmount(new BigDecimal("28000.00")).build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("L4").category(ClaimSimulationDto.BillItemCategory.MEDICINES).description("Prescribed Inpatient Medicines (Exempt)")
                        .unitPrice(new BigDecimal("20000.00")).quantity(1).totalAmount(new BigDecimal("20000.00")).build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("L5").category(ClaimSimulationDto.BillItemCategory.CONSUMABLES).description("Consumables (IRDAI List 1 Non-Payable Items)")
                        .unitPrice(new BigDecimal("12000.00")).quantity(1).totalAmount(new BigDecimal("12000.00")).build()
        );

        ClaimSimulationDto.ClaimSimulationRequest simRequest = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId(policy.getId())
                .uinRevision(policy.getUinRevision())
                .patientAge(45)
                .diagnosis("Acute Appendicitis (Laparoscopic Surgery)")
                .hospitalRoomCategoryOpted("SINGLE_PRIVATE_AC")
                .selectedSumInsured(sumInsured)
                .policyContinuityMonths(36)
                .billLines(billLines)
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = claimEngine.calculateClaim(simRequest, rules);

        BigDecimal roomExcess = BigDecimal.ZERO;
        BigDecimal proportionate = BigDecimal.ZERO;
        BigDecimal consumables = BigDecimal.ZERO;
        BigDecimal copay = BigDecimal.ZERO;
        List<String> citations = new ArrayList<>();

        if (response.getDeductionBreakdown() != null) {
            for (ClaimSimulationDto.DeductionItemDto item : response.getDeductionBreakdown()) {
                if (item.getDeductionType() != null) {
                    switch (item.getDeductionType()) {
                        case "ROOM_RENT_EXCESS":
                            roomExcess = roomExcess.add(item.getAmountDeducted());
                            break;
                        case "PROPORTIONATE_DEDUCTION":
                            proportionate = proportionate.add(item.getAmountDeducted());
                            break;
                        case "NON_MEDICAL_CONSUMABLES":
                            consumables = consumables.add(item.getAmountDeducted());
                            break;
                        case "COPAYMENT":
                            copay = copay.add(item.getAmountDeducted());
                            break;
                        default:
                            break;
                    }
                    String citation = (item.getSectionReference() != null ? item.getSectionReference() + ": " : "")
                            + (item.getExplanation() != null ? item.getExplanation() : item.getDeductionType());
                    if (!citations.contains(citation)) {
                        citations.add(citation);
                    }
                }
            }
        }

        String explanation;
        if (rules.isRoomRentCapped()) {
            explanation = String.format("Under contractual room cap of ₹%s/day, room excess is ₹%s and associated charges incurred a proportionate deduction of ₹%s. Consumables (₹%s) are excluded per IRDAI List 1.",
                    rules.getRoomRentCapPerDay(), roomExcess, proportionate, consumables);
        } else {
            explanation = String.format("With zero room rent capping, 100%% of room, surgeon, OT, and medicine charges are approved. Only non-medical consumables (₹%s) are borne by the patient.",
                    consumables);
        }

        return PolicyIntelligenceProfileDto.BenchmarkClaimSimulationSummaryDto.builder()
                .diagnosis("Acute Appendicitis (Laparoscopic Surgery Standard Benchmark)")
                .totalSubmittedBill(new BigDecimal("123000.00"))
                .estimatedInsurerPayment(response.getEstimatedInsurerPayment())
                .estimatedPatientLiability(response.getEstimatedPatientLiability())
                .isReconciled(response.isReconciled())
                .roomRentExcessDeduction(roomExcess)
                .proportionateDeduction(proportionate)
                .nonMedicalConsumablesDeduction(consumables)
                .copayDeduction(copay)
                .contractualExplanation(explanation)
                .clauseCitations(citations)
                .build();
    }

    private PolicyIntelligenceProfileDto.EvidenceQualityScorecardDto buildEvidenceQualityScorecard(
            StructuredPolicy policy,
            ExternalEvidenceDto.InsurerPerformanceSummaryDto insurerPerf,
            ExternalEvidenceDto.QuoteDetailDto quotation,
            ExternalEvidenceDto.HospitalNetworkSummaryDto hospitalNet) {

        List<String> verifiedDisclosures = new ArrayList<>();
        List<String> pendingOrMissing = new ArrayList<>();
        double score = 40.0; // Base score for verified contractual structure

        // 1. Contractual Ground Truth
        if (policy.isSyntheticFixture()) {
            pendingOrMissing.add("Contractual Terms: Synthetic demonstration fixture (re-verification required for live binding contract).");
        } else {
            verifiedDisclosures.add("Contractual Specifications: Extracted verbatim from IRDAI filing " + policy.getIrdaUin());
            score += 20.0;
        }

        // 2. External Statutory Performance Evidence
        String statutoryStatus = "UNVERIFIED";
        String statutoryHash = null;
        if (insurerPerf != null && insurerPerf.isVerifiedStatutory()) {
            statutoryStatus = "VERIFIED_STATUTORY";
            statutoryHash = insurerPerf.getStatutoryDocumentHash();
            verifiedDisclosures.add("Insurer Performance: Verified against " + insurerPerf.getStatutorySourceTitle()
                    + " (" + insurerPerf.getReportingPeriod() + ") [SHA-256: " + statutoryHash + "]");
            score += 20.0;
        } else {
            pendingOrMissing.add("Insurer Performance: External statutory filing extraction pending reviewer verification.");
        }

        // 3. Premium Quote
        String quoteStatus = "ESTIMATED_ACTUARIAL_MODEL";
        if (quotation != null) {
            quoteStatus = quotation.getVerificationStatus();
            if (quotation.isAuthentic() && !quotation.isStale()) {
                verifiedDisclosures.add("Premium Quote: Authentic prospect quote from " + quotation.getQuotingChannel()
                        + " (Ref: " + quotation.getQuoteReferenceNumber() + ")");
                score += 10.0;
            } else if (quotation.isStale()) {
                pendingOrMissing.add("Premium Quote: Previously verified quote has expired. Displaying non-binding actuarial estimate.");
            } else {
                pendingOrMissing.add("Premium Quote: Non-binding actuarial model estimate. Live insurer underwriting API quote pending.");
            }
        }

        // 4. Hospital Registry
        String hospitalStatus = "UNVERIFIED";
        if (hospitalNet != null && hospitalNet.getTotalNetworkHospitalsInCity() > 0) {
            hospitalStatus = hospitalNet.getVerificationBadge();
            if ("VERIFIED_HOSPITAL_REGISTRY".equals(hospitalStatus)) {
                verifiedDisclosures.add("Hospital Registry: " + hospitalNet.getTotalNetworkHospitalsInCity()
                        + " verified network facilities in selected region (" + hospitalNet.getPreferredTier1Count() + " Tier-1 Preferred)");
                score += 10.0;
            } else {
                pendingOrMissing.add("Hospital Registry: Regional hospital listings pending bilateral cashless verification audit.");
            }
        } else {
            pendingOrMissing.add("Hospital Registry: Location-specific cashless hospital records pending official provider disclosure ingestion.");
        }

        return PolicyIntelligenceProfileDto.EvidenceQualityScorecardDto.builder()
                .contractualGroundTruthStatus(policy.getVerificationStatus() != null ? policy.getVerificationStatus() : "VERIFIED_OFFICIAL_CATALOG")
                .externalStatutoryEvidenceStatus(statutoryStatus)
                .quoteVerificationStatus(quoteStatus)
                .hospitalRegistryStatus(hospitalStatus)
                .overallQualityScore(Math.min(100.0, score))
                .verifiedDisclosures(verifiedDisclosures)
                .pendingOrMissingDisclosures(pendingOrMissing)
                .statutoryDocumentHash(statutoryHash)
                .lastAuditedDate(policy.getLastAuditedDate() != null ? policy.getLastAuditedDate() : "2024-Q1")
                .disclaimer("Policy Suitability, Evidence Completeness, and Insurer Performance are independently evaluated. External data absence does not reduce contractual suitability, and high suitability does not conceal missing regulatory evidence.")
                .build();
    }

    private PolicyIntelligenceProfileDto.PolicySuitabilityDto buildPolicySuitability(
            StructuredPolicy policy, ApprovedPolicyRuleResolver.ResolvedPolicyRules rules, Double customSuitabilityScore) {

        double score;
        List<String> keyFitFactors = new ArrayList<>();
        List<String> potentialGaps = new ArrayList<>();

        if (customSuitabilityScore != null && customSuitabilityScore > 0) {
            score = customSuitabilityScore;
            if (!rules.isRoomRentCapped()) {
                keyFitFactors.add("No room rent sublimit: Inpatient room charges covered without proportionate deduction.");
            }
            if (rules.getCopayPercentage().compareTo(BigDecimal.ZERO) == 0) {
                keyFitFactors.add("Zero mandatory co-payment: Insurer covers 100% of approved claim expenses.");
            }
        } else {
            // Compute contract baseline suitability
            double base = 70.0;
            if (!rules.isRoomRentCapped()) {
                base += 15.0;
                keyFitFactors.add("No room rent sublimit: Covers up to single private room or sum insured without proportionate deduction penalty.");
            } else {
                base -= 8.0;
                potentialGaps.add(String.format("Room rent capped at ₹%s/day: Exceeding room ceiling triggers proportionate deductions across associated medical charges.",
                        rules.getRoomRentCapPerDay()));
            }

            if (rules.getCopayPercentage().compareTo(BigDecimal.ZERO) == 0) {
                base += 10.0;
                keyFitFactors.add("Zero mandatory co-payment: No co-pay deduction regardless of age or hospital network status.");
            } else {
                base -= 10.0;
                potentialGaps.add(String.format("Mandatory %s%% co-payment on all hospital claims.", rules.getCopayPercentage()));
            }

            if (rules.getWaitingPeriodPedMonths() <= 24) {
                base += 5.0;
                keyFitFactors.add(String.format("Expedited pre-existing disease waiting period: Only %d months.", rules.getWaitingPeriodPedMonths()));
            } else if (rules.getWaitingPeriodPedMonths() >= 48) {
                potentialGaps.add(String.format("Standard %d-month pre-existing condition waiting period.", rules.getWaitingPeriodPedMonths()));
            }

            score = Math.min(95.0, Math.max(50.0, base));
        }

        String rating;
        if (score >= 88.0) rating = "EXCELLENT_FIT";
        else if (score >= 75.0) rating = "STRONG_FIT";
        else if (score >= 60.0) rating = "MODERATE_FIT";
        else rating = "CONDITIONAL_FIT";

        String summary = String.format("Contract terms provide %s compatibility (%s/100). Suitability is strictly decoupled from external statutory disclosure availability.",
                rating.toLowerCase().replace('_', ' '), Math.round(score));

        return PolicyIntelligenceProfileDto.PolicySuitabilityDto.builder()
                .suitabilityScore(score)
                .suitabilityRating(rating)
                .suitabilitySummary(summary)
                .keyFitFactors(keyFitFactors)
                .potentialGaps(potentialGaps)
                .independentOfExternalEvidence(true)
                .build();
    }

    private PolicyIntelligenceProfileDto.EvidenceCompletenessDto buildEvidenceCompleteness(
            StructuredPolicy policy,
            ExternalEvidenceDto.InsurerPerformanceSummaryDto insurerPerf,
            ExternalEvidenceDto.QuoteDetailDto quotation,
            ExternalEvidenceDto.HospitalNetworkSummaryDto hospitalNet) {

        boolean contractVerified = !policy.isSyntheticFixture() && "VERIFIED_OFFICIAL_CATALOG".equals(policy.getVerificationStatus());
        boolean perfVerified = insurerPerf != null && insurerPerf.isVerifiedStatutory();
        boolean quoteVerified = quotation != null && quotation.isAuthentic() && !quotation.isStale();
        boolean hospVerified = hospitalNet != null && "VERIFIED_HOSPITAL_REGISTRY".equals(hospitalNet.getVerificationBadge());

        int verifiedCount = 0;
        if (contractVerified) verifiedCount++;
        if (perfVerified) verifiedCount++;
        if (quoteVerified) verifiedCount++;
        if (hospVerified) verifiedCount++;

        double completenessScore = (verifiedCount / 4.0) * 100.0;
        String status;
        if (completenessScore >= 99.0) status = "COMPLETE";
        else if (completenessScore >= 74.0) status = "HIGH";
        else if (completenessScore >= 49.0) status = "PARTIAL";
        else status = "LIMITED";

        String audit = String.format("Verified %d of 4 core evidence dimensions (%s). Contract: %s, Statutory: %s, Quote: %s, Hospitals: %s.",
                verifiedCount, status,
                contractVerified ? "Verified Official Catalog" : (policy.isSyntheticFixture() ? "Synthetic Demo Fixture" : "Pending Verification"),
                perfVerified ? "Verified Statutory IRDAI" : "Unverified Pending Filing",
                quoteVerified ? "Verified Authentic Quote" : (quotation != null && quotation.isStale() ? "Expired Stale Quote" : "Actuarial Model Estimate"),
                hospVerified ? ("Verified (" + hospitalNet.getTotalNetworkHospitalsInCity() + " Network Facilities)") : "Pending Regional Records");

        return PolicyIntelligenceProfileDto.EvidenceCompletenessDto.builder()
                .completenessScore(completenessScore)
                .completenessStatus(status)
                .contractualTermsVerified(contractVerified)
                .statutoryPerformanceVerified(perfVerified)
                .authenticQuoteVerified(quoteVerified)
                .hospitalNetworkVerified(hospVerified)
                .verifiedCount(verifiedCount)
                .totalRequiredCount(4)
                .auditSummary(audit)
                .build();
    }

    private String extractInsurerCode(StructuredPolicy policy) {
        if (policy.getPolicyCode() != null) {
            String code = policy.getPolicyCode().toUpperCase();
            if (code.contains("HDFC")) return "HDFC_ERGO";
            if (code.contains("STAR")) return "STAR_HEALTH";
            if (code.contains("UIIC") || code.contains("UNITED")) return "UNITED_INDIA";
            if (code.contains("NEW_INDIA") || code.contains("NIA")) return "NEW_INDIA";
            if (code.contains("CARE")) return "CARE";
            if (code.contains("NIVA")) return "NIVA_BUPA";
            if (code.contains("ICICI")) return "ICICI_LOMBARD";
            if (code.contains("ADITYA")) return "ADITYA_BIRLA";
        }
        if (policy.getProvider() != null) {
            String p = policy.getProvider().toUpperCase();
            if (p.contains("HDFC")) return "HDFC_ERGO";
            if (p.contains("STAR")) return "STAR_HEALTH";
            if (p.contains("UNITED")) return "UNITED_INDIA";
            if (p.contains("NEW INDIA")) return "NEW_INDIA";
            if (p.contains("CARE")) return "CARE";
            if (p.contains("NIVA")) return "NIVA_BUPA";
            if (p.contains("ICICI")) return "ICICI_LOMBARD";
        }
        return "UNKNOWN_INSURER";
    }
}
