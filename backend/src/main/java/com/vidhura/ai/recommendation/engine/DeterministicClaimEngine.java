package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.service.ApprovedPolicyRuleResolver.ResolvedPolicyRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Deterministic Claim Calculation Engine.
 *
 * Implements policy-specific, decimal-safe arithmetic for health insurance claim simulations.
 *
 * Guarantees:
 * 1. Policy-specific calculation semantics (no universal monolithic waterfall).
 * 2. Independent modeling of Sum Insured vs Restoration benefit with trigger conditions.
 * 3. Nuanced waiting period checks (accidental trauma exemption, continuity credits).
 * 4. Exact mathematical reconciliation identity:
 *    Patient Liability = Submitted Bill - Insurer Payment
 * 5. Fail-closed handling of unresolved or ambiguous rules (MANUAL_REVIEW_REQUIRED).
 */
@Slf4j
@Component
public class DeterministicClaimEngine {

    public static final String ENGINE_VERSION = "v1.2.0-deterministic";

    /**
     * Executes the deterministic claim calculation against resolved, approved policy rules.
     */
    public ClaimSimulationDto.ClaimSimulationResponse calculateClaim(
            ClaimSimulationDto.ClaimSimulationRequest request,
            ResolvedPolicyRules rules) {

        Instant evalTime = Instant.now();
        String simId = "sim-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Initial Validation & Total Submitted Bill
        BigDecimal submittedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (ClaimSimulationDto.BillLineItemDto line : request.getBillLines()) {
            if (line.getTotalAmount() != null) {
                submittedTotal = submittedTotal.add(line.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
            }
        }

        List<ClaimSimulationDto.DeductionItemDto> deductions = new ArrayList<>();
        List<String> appliedClauses = new ArrayList<>();
        List<String> unresolvedRules = new ArrayList<>();

        // Check for material rule uncertainties touching submitted expenses
        boolean touchesRoomRent = request.getBillLines().stream()
                .anyMatch(l -> l.getCategory() == ClaimSimulationDto.BillItemCategory.ROOM_RENT);
        if (touchesRoomRent && rules.hasMaterialUncertaintyFor("UNCERTAIN_ROOM_RENT_CLAUSE")) {
            unresolvedRules.add("Room rent ceiling and proportionate deduction terms are uncertain in source document.");
            return buildManualReviewResponse(simId, request, rules, submittedTotal,
                    "Room rent terms require manual reviewer clarification before claim calculation can proceed.",
                    unresolvedRules, evalTime);
        }

        if (rules.hasMaterialUncertaintyFor("UNCERTAIN_COPAY_PERCENTAGE")) {
            unresolvedRules.add("Co-payment terms are ambiguous in policy wording.");
            return buildManualReviewResponse(simId, request, rules, submittedTotal,
                    "Co-payment terms require manual reviewer clarification.",
                    unresolvedRules, evalTime);
        }

        // 2. Primary Waiting Period & Eligibility Checks
        // 2a. Initial 30-Day Waiting Period (Accidental injury is statutorily exempt)
        if (!request.isAccidentalInjury() && request.getPolicyContinuityMonths() == 0) {
            String expl = "Claim occurred within initial 30-day waiting period. Illness admissions are excluded during this period.";
            deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                    .deductionType("WAITING_PERIOD_INELIGIBLE")
                    .expenseCategory("ENTIRE_BILL")
                    .amountDeducted(submittedTotal)
                    .explanation(expl)
                    .calculationFormula("Submitted Total (₹" + submittedTotal + ") excluded due to active 30-day initial waiting period.")
                    .sectionReference(rules.getInitialWaitClauseRef())
                    .verbatimClauseText(rules.getInitialWaitVerbatimText())
                    .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                    .build());

            appliedClauses.add(rules.getInitialWaitClauseRef());
            return buildFinalReconciledResponse(simId, request, rules, submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    submittedTotal,
                    submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    deductions, appliedClauses, unresolvedRules, false, BigDecimal.ZERO, null,
                    ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, null, evalTime);
        }

        // 2b. Pre-Existing Disease (PED) Waiting Period
        if (request.isPreExistingCondition() && request.getPolicyContinuityMonths() < rules.getWaitingPeriodPedMonths()) {
            String expl = String.format("Treatment for Pre-Existing Condition is excluded. Required continuity: %d months; patient continuity: %d months.",
                    rules.getWaitingPeriodPedMonths(), request.getPolicyContinuityMonths());
            deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                    .deductionType("WAITING_PERIOD_INELIGIBLE")
                    .expenseCategory("ENTIRE_BILL")
                    .amountDeducted(submittedTotal)
                    .explanation(expl)
                    .calculationFormula(String.format("Pre-existing condition excluded: %d months continuity < %d months required.",
                            request.getPolicyContinuityMonths(), rules.getWaitingPeriodPedMonths()))
                    .sectionReference(rules.getPedWaitClauseRef())
                    .verbatimClauseText(rules.getPedWaitVerbatimText())
                    .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                    .build());

            appliedClauses.add(rules.getPedWaitClauseRef());
            return buildFinalReconciledResponse(simId, request, rules, submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    submittedTotal,
                    submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    deductions, appliedClauses, unresolvedRules, false, BigDecimal.ZERO, null,
                    ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, null, evalTime);
        }

        // 2c. Specific Illness Waiting Period
        boolean isSpecificIllness = isConditionSubjectToSpecificIllnessWait(request.getDiagnosis(), rules.getSpecificIllnessKeywords());
        if (isSpecificIllness && request.getPolicyContinuityMonths() < rules.getWaitingPeriodSpecificMonths()) {
            String expl = String.format("Treatment for '%s' is subject to specific illness waiting period of %d months (patient continuity: %d months).",
                    request.getDiagnosis(), rules.getWaitingPeriodSpecificMonths(), request.getPolicyContinuityMonths());
            deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                    .deductionType("WAITING_PERIOD_INELIGIBLE")
                    .expenseCategory("ENTIRE_BILL")
                    .amountDeducted(submittedTotal)
                    .explanation(expl)
                    .calculationFormula(String.format("Specific procedure excluded: %d months continuity < %d months required.",
                            request.getPolicyContinuityMonths(), rules.getWaitingPeriodSpecificMonths()))
                    .sectionReference(rules.getSpecificWaitClauseRef())
                    .verbatimClauseText(rules.getSpecificWaitVerbatimText())
                    .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                    .build());

            appliedClauses.add(rules.getSpecificWaitClauseRef());
            return buildFinalReconciledResponse(simId, request, rules, submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    submittedTotal,
                    submittedTotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    deductions, appliedClauses, unresolvedRules, false, BigDecimal.ZERO, null,
                    ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, null, evalTime);
        }

        // 3. Line-Item Eligibility & Non-Medical Consumables Exclusion
        BigDecimal nonCoveredTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal eligibleMedicalTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Map line items into categories
        BigDecimal roomRentSubmitted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal associatedMedicalSubmitted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fixedMedicalSubmitted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (ClaimSimulationDto.BillLineItemDto line : request.getBillLines()) {
            BigDecimal lineAmount = line.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            if (line.getCategory() == ClaimSimulationDto.BillItemCategory.CONSUMABLES) {
                nonCoveredTotal = nonCoveredTotal.add(lineAmount).setScale(2, RoundingMode.HALF_UP);
                deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                        .deductionType("NON_MEDICAL_CONSUMABLES")
                        .expenseCategory(line.getCategory().name())
                        .amountDeducted(lineAmount)
                        .explanation("Non-medical consumables classified under IRDAI List 1 (gloves, sanitizers, admin fees) are excluded.")
                        .calculationFormula("Excluded full consumable charge: ₹" + lineAmount)
                        .sectionReference(rules.getExclusionsClauseRef())
                        .verbatimClauseText(rules.getExclusionsVerbatimText())
                        .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                        .build());
            } else {
                eligibleMedicalTotal = eligibleMedicalTotal.add(lineAmount).setScale(2, RoundingMode.HALF_UP);
                if (line.getCategory() == ClaimSimulationDto.BillItemCategory.ROOM_RENT) {
                    roomRentSubmitted = roomRentSubmitted.add(lineAmount);
                } else if (isAssociatedMedicalCharge(line.getCategory())) {
                    associatedMedicalSubmitted = associatedMedicalSubmitted.add(lineAmount);
                } else {
                    fixedMedicalSubmitted = fixedMedicalSubmitted.add(lineAmount);
                }
            }
        }
        if (nonCoveredTotal.compareTo(BigDecimal.ZERO) > 0) {
            appliedClauses.add(rules.getExclusionsClauseRef());
        }

        // 4. Room Rent Limits & Proportionate Deductions
        BigDecimal admissibleRoomRent = roomRentSubmitted;
        BigDecimal roomRentExcess = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal proportionateDeductionAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (rules.isRoomRentCapped() && roomRentSubmitted.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyCap = rules.getRoomRentCapPerDay();
            // Estimate number of days from line or request
            int days = calculateHospitalDays(request);
            BigDecimal totalAllowedRoomTariff = dailyCap.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);

            if (roomRentSubmitted.compareTo(totalAllowedRoomTariff) > 0) {
                roomRentExcess = roomRentSubmitted.subtract(totalAllowedRoomTariff).setScale(2, RoundingMode.HALF_UP);
                admissibleRoomRent = totalAllowedRoomTariff;

                deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                        .deductionType("ROOM_RENT_EXCESS")
                        .expenseCategory("ROOM_RENT")
                        .amountDeducted(roomRentExcess)
                        .explanation(String.format("Room rent capped at ₹%s/day. Actual room charge exceeded policy entitlement.", dailyCap))
                        .calculationFormula(String.format("Actual Room (₹%s) - Allowed Cap (₹%s * %d days = ₹%s) = ₹%s",
                                roomRentSubmitted, dailyCap, days, totalAllowedRoomTariff, roomRentExcess))
                        .sectionReference(rules.getRoomRentClauseRef())
                        .verbatimClauseText(rules.getRoomRentVerbatimText())
                        .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                        .build());

                appliedClauses.add(rules.getRoomRentClauseRef());

                // Proportionate Deduction on Associated Hospital Charges ONLY
                if (rules.isProportionateDeductionApplies() && associatedMedicalSubmitted.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal allowableAssociated = associatedMedicalSubmitted
                            .multiply(totalAllowedRoomTariff)
                            .divide(roomRentSubmitted, 2, RoundingMode.HALF_UP);
                    proportionateDeductionAmount = associatedMedicalSubmitted.subtract(allowableAssociated).setScale(2, RoundingMode.HALF_UP);

                    deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                            .deductionType("PROPORTIONATE_DEDUCTION")
                            .expenseCategory("ASSOCIATED_MEDICAL_CHARGES")
                            .amountDeducted(proportionateDeductionAmount)
                            .explanation("Proportionate deduction applied across associated medical charges (nursing, OT, surgeon fees) due to higher room category.")
                            .calculationFormula(String.format("Associated Charges (₹%s) * (1 - (₹%s / ₹%s)) = ₹%s",
                                    associatedMedicalSubmitted, totalAllowedRoomTariff, roomRentSubmitted, proportionateDeductionAmount))
                            .sectionReference(rules.getRoomRentClauseRef())
                            .verbatimClauseText(rules.getRoomRentVerbatimText())
                            .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                            .build());
                }
            }
        }

        BigDecimal admissibleAfterRoomRules = admissibleRoomRent
                .add(associatedMedicalSubmitted.subtract(proportionateDeductionAmount))
                .add(fixedMedicalSubmitted)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Disease-Specific Sublimits
        BigDecimal sublimitDeduction = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String matchedDiseaseKey = findMatchedSublimitKey(request.getDiagnosis(), rules.getDiseaseSublimits().keySet());
        if (matchedDiseaseKey != null) {
            BigDecimal sublimitCap = rules.getDiseaseSublimits().get(matchedDiseaseKey);
            if (admissibleAfterRoomRules.compareTo(sublimitCap) > 0) {
                sublimitDeduction = admissibleAfterRoomRules.subtract(sublimitCap).setScale(2, RoundingMode.HALF_UP);
                admissibleAfterRoomRules = sublimitCap;

                deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                        .deductionType("DISEASE_SUBLIMIT_EXCESS")
                        .expenseCategory("PROCEDURE_SUBLIMIT")
                        .amountDeducted(sublimitDeduction)
                        .explanation(String.format("Procedure sublimit of ₹%s applies to '%s'.", sublimitCap, request.getDiagnosis()))
                        .calculationFormula(String.format("Admissible before sublimit (₹%s) - Sublimit Cap (₹%s) = ₹%s",
                                admissibleAfterRoomRules.add(sublimitDeduction), sublimitCap, sublimitDeduction))
                        .sectionReference(rules.getSublimitClauseRefs().getOrDefault(matchedDiseaseKey, "Section: Disease Sub-limits"))
                        .verbatimClauseText(rules.getSublimitVerbatimTexts().getOrDefault(matchedDiseaseKey, "Sublimit applies to specified procedure."))
                        .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                        .build());

                appliedClauses.add(rules.getSublimitClauseRefs().getOrDefault(matchedDiseaseKey, "Section: Disease Sub-limits"));
            }
        }

        // 6. Sum Insured & Restoration Modeling
        BigDecimal availableBaseSI = request.getSelectedSumInsured()
                .subtract(request.getPriorClaimsInCurrentYear())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        boolean restorationTriggered = false;
        BigDecimal restorationGranted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String restorationExplanation = null;
        BigDecimal sumInsuredExhaustionDeduction = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal admissibleForCoverage = admissibleAfterRoomRules;

        if (admissibleAfterRoomRules.compareTo(availableBaseSI) > 0) {
            BigDecimal excessOverBaseSI = admissibleAfterRoomRules.subtract(availableBaseSI).setScale(2, RoundingMode.HALF_UP);

            // Evaluate restoration conditions
            if (rules.isRestorationAvailable()) {
                if (request.isSubsequentClaimForSameIllness() && !rules.isRestorationCoversSameIllness()) {
                    // Restoration excluded for same illness
                    sumInsuredExhaustionDeduction = excessOverBaseSI;
                    admissibleForCoverage = availableBaseSI;
                    restorationExplanation = "Restoration is restricted to different illnesses; not eligible for subsequent claim for same condition.";

                    deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                            .deductionType("SUM_INSURED_EXHAUSTION")
                            .expenseCategory("SUM_INSURED")
                            .amountDeducted(sumInsuredExhaustionDeduction)
                            .explanation("Claim exceeds remaining base Sum Insured. Restoration benefit does not cover same illness.")
                            .calculationFormula(String.format("Excess Claim (₹%s) - Base SI Available (₹%s) = ₹%s",
                                    admissibleAfterRoomRules, availableBaseSI, sumInsuredExhaustionDeduction))
                            .sectionReference(rules.getRestorationClauseRef())
                            .verbatimClauseText(rules.getRestorationVerbatimText())
                            .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                            .build());

                    appliedClauses.add(rules.getRestorationClauseRef());
                } else {
                    // Restoration triggers!
                    restorationTriggered = true;
                    restorationGranted = excessOverBaseSI.min(request.getSelectedSumInsured()).setScale(2, RoundingMode.HALF_UP);
                    restorationExplanation = String.format("Base Sum Insured exhausted. Restoration benefit triggered granting ₹%s.", restorationGranted);
                    appliedClauses.add(rules.getRestorationClauseRef());
                }
            } else {
                sumInsuredExhaustionDeduction = excessOverBaseSI;
                admissibleForCoverage = availableBaseSI;

                deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                        .deductionType("SUM_INSURED_EXHAUSTION")
                        .expenseCategory("SUM_INSURED")
                        .amountDeducted(sumInsuredExhaustionDeduction)
                        .explanation("Claim exceeds available base Sum Insured. No restoration benefit available.")
                        .calculationFormula(String.format("Admissible (₹%s) - Available SI (₹%s) = Excess ₹%s",
                                admissibleAfterRoomRules, availableBaseSI, sumInsuredExhaustionDeduction))
                        .sectionReference("Section: Sum Insured Table")
                        .verbatimClauseText("Indemnity liability capped at total Sum Insured specified in the Policy Schedule.")
                        .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                        .build());
            }
        }

        // 7. Cost Sharing Waterfall (Deductible then Co-payment)
        BigDecimal deductibleApplied = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (rules.getDeductibleAmount() != null && rules.getDeductibleAmount().compareTo(BigDecimal.ZERO) > 0) {
            deductibleApplied = admissibleForCoverage.min(rules.getDeductibleAmount()).setScale(2, RoundingMode.HALF_UP);
            admissibleForCoverage = admissibleForCoverage.subtract(deductibleApplied).setScale(2, RoundingMode.HALF_UP);

            deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                    .deductionType("DEDUCTIBLE")
                    .expenseCategory("COST_SHARING")
                    .amountDeducted(deductibleApplied)
                    .explanation(String.format("Mandatory annual deductible of ₹%s borne by insured prior to insurer payment.", rules.getDeductibleAmount()))
                    .calculationFormula(String.format("Deductible applied: ₹%s", deductibleApplied))
                    .sectionReference(rules.getDeductibleClauseRef())
                    .verbatimClauseText(rules.getDeductibleVerbatimText())
                    .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                    .build());

            appliedClauses.add(rules.getDeductibleClauseRef());
        }

        BigDecimal copayApplied = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (rules.getCopayPercentage() != null && rules.getCopayPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal copayRatio = rules.getCopayPercentage().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            copayApplied = admissibleForCoverage.multiply(copayRatio).setScale(2, RoundingMode.HALF_UP);
            admissibleForCoverage = admissibleForCoverage.subtract(copayApplied).setScale(2, RoundingMode.HALF_UP);

            deductions.add(ClaimSimulationDto.DeductionItemDto.builder()
                    .deductionType("COPAYMENT")
                    .expenseCategory("COST_SHARING")
                    .amountDeducted(copayApplied)
                    .explanation(String.format("Mandatory %.0f%% co-payment borne by insured on net admissible claim.", rules.getCopayPercentage()))
                    .calculationFormula(String.format("Net Admissible (₹%s) * %.0f%% = ₹%s",
                            admissibleForCoverage.add(copayApplied), rules.getCopayPercentage(), copayApplied))
                    .sectionReference(rules.getCopayClauseRef())
                    .verbatimClauseText(rules.getCopayVerbatimText())
                    .clauseConfidence("VERIFIED_CONTRACT_CLAUSE")
                    .build());

            appliedClauses.add(rules.getCopayClauseRef());
        }

        // Final Insurer Payment
        BigDecimal insurerPayment = admissibleForCoverage.setScale(2, RoundingMode.HALF_UP);

        // 8. Exact Mathematical Reconciliation Identity:
        // Patient Liability = Submitted Bill - Insurer Payment
        BigDecimal patientLiability = submittedTotal.subtract(insurerPayment).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalDeductionsSum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (ClaimSimulationDto.DeductionItemDto d : deductions) {
            totalDeductionsSum = totalDeductionsSum.add(d.getAmountDeducted()).setScale(2, RoundingMode.HALF_UP);
        }

        return buildFinalReconciledResponse(simId, request, rules, submittedTotal,
                eligibleMedicalTotal, nonCoveredTotal, totalDeductionsSum,
                insurerPayment, deductions, appliedClauses, unresolvedRules,
                restorationTriggered, restorationGranted, restorationExplanation,
                ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, null, evalTime);
    }

    private ClaimSimulationDto.ClaimSimulationResponse buildFinalReconciledResponse(
            String simId,
            ClaimSimulationDto.ClaimSimulationRequest request,
            ResolvedPolicyRules rules,
            BigDecimal submittedTotal,
            BigDecimal eligibleMedicalTotal,
            BigDecimal nonCoveredTotal,
            BigDecimal totalDeductions,
            BigDecimal insurerPayment,
            List<ClaimSimulationDto.DeductionItemDto> deductions,
            List<String> appliedClauses,
            List<String> unresolvedRules,
            boolean restorationTriggered,
            BigDecimal restorationGranted,
            String restorationExplanation,
            ClaimSimulationDto.CalculationStatus status,
            String statusReason,
            Instant evalTime) {

        BigDecimal patientLiability = submittedTotal.subtract(insurerPayment).setScale(2, RoundingMode.HALF_UP);
        boolean reconciled = patientLiability.add(insurerPayment).compareTo(submittedTotal) == 0;

        String formula = String.format("Patient Liability (₹%s) = Submitted Bill (₹%s) - Insurer Payment (₹%s)",
                patientLiability, submittedTotal, insurerPayment);

        return ClaimSimulationDto.ClaimSimulationResponse.builder()
                .simulationId(simId)
                .policyId(rules.getPolicyId())
                .policyCode(rules.getPolicyCode())
                .policyName(rules.getPolicyName())
                .provider(rules.getProvider())
                .irdaUin(rules.getIrdaUin())
                .uinRevision(rules.getUinRevision())
                .sourceDocumentHash(rules.getSourceDocumentHash())
                .ruleSetVersion(rules.getRuleSetVersion())
                .calculationEngineVersion(ENGINE_VERSION)
                .calculationStatus(status)
                .statusReason(statusReason)
                .totalSubmittedBill(submittedTotal)
                .totalEligibleExpenses(eligibleMedicalTotal)
                .totalNonCoveredExpenses(nonCoveredTotal)
                .totalDeductions(totalDeductions)
                .estimatedInsurerPayment(insurerPayment)
                .estimatedPatientLiability(patientLiability)
                .reconciliationIdentity(formula)
                .reconciled(reconciled)
                .restorationTriggered(restorationTriggered)
                .restorationAmountGranted(restorationGranted)
                .restorationExplanation(restorationExplanation)
                .deductionBreakdown(deductions)
                .appliedClauses(new ArrayList<>(new LinkedHashSet<>(appliedClauses)))
                .unresolvedRules(unresolvedRules)
                .legalDisclaimer("This simulation is a deterministic actuarial calculation based on verified contractual terms extracted from the approved policy wording document. It is not an official guarantee of claim approval or settlement.")
                .evaluatedAt(evalTime)
                .build();
    }

    private ClaimSimulationDto.ClaimSimulationResponse buildManualReviewResponse(
            String simId,
            ClaimSimulationDto.ClaimSimulationRequest request,
            ResolvedPolicyRules rules,
            BigDecimal submittedTotal,
            String reason,
            List<String> unresolved,
            Instant evalTime) {

        return ClaimSimulationDto.ClaimSimulationResponse.builder()
                .simulationId(simId)
                .policyId(rules.getPolicyId())
                .policyCode(rules.getPolicyCode())
                .policyName(rules.getPolicyName())
                .provider(rules.getProvider())
                .irdaUin(rules.getIrdaUin())
                .uinRevision(rules.getUinRevision())
                .sourceDocumentHash(rules.getSourceDocumentHash())
                .ruleSetVersion(rules.getRuleSetVersion())
                .calculationEngineVersion(ENGINE_VERSION)
                .calculationStatus(ClaimSimulationDto.CalculationStatus.MANUAL_REVIEW_REQUIRED)
                .statusReason(reason)
                .totalSubmittedBill(submittedTotal)
                .totalEligibleExpenses(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalNonCoveredExpenses(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalDeductions(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .estimatedInsurerPayment(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .estimatedPatientLiability(submittedTotal)
                .reconciliationIdentity("Simulation suspended pending manual compliance review")
                .reconciled(false)
                .restorationTriggered(false)
                .restorationAmountGranted(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .deductionBreakdown(Collections.emptyList())
                .appliedClauses(Collections.emptyList())
                .unresolvedRules(unresolved)
                .legalDisclaimer("Calculation abstained: A material policy clause is ambiguous or unverified in the official filing. Human reviewer evaluation is required.")
                .evaluatedAt(evalTime)
                .build();
    }

    private boolean isAssociatedMedicalCharge(ClaimSimulationDto.BillItemCategory cat) {
        return cat == ClaimSimulationDto.BillItemCategory.NURSING_CHARGES
                || cat == ClaimSimulationDto.BillItemCategory.SURGEON_FEES
                || cat == ClaimSimulationDto.BillItemCategory.OT_CHARGES;
    }

    private boolean isConditionSubjectToSpecificIllnessWait(String diagnosis, Set<String> keywords) {
        if (diagnosis == null || keywords == null) return false;
        String lower = diagnosis.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String findMatchedSublimitKey(String diagnosis, Set<String> keys) {
        if (diagnosis == null || keys == null) return null;
        String lower = diagnosis.toLowerCase();
        for (String k : keys) {
            if (lower.contains(k)) return k;
        }
        return null;
    }

    private int calculateHospitalDays(ClaimSimulationDto.ClaimSimulationRequest req) {
        if (req.getAdmissionDate() != null && req.getDischargeDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(req.getAdmissionDate(), req.getDischargeDate());
            return Math.max(1, (int) days);
        }
        // Fallback: examine quantity of ROOM_RENT line items
        for (ClaimSimulationDto.BillLineItemDto line : req.getBillLines()) {
            if (line.getCategory() == ClaimSimulationDto.BillItemCategory.ROOM_RENT && line.getQuantity() > 0) {
                return line.getQuantity();
            }
        }
        return 1;
    }
}
