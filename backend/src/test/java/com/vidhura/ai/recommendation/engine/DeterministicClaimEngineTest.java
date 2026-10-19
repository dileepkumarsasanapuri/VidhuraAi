package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.recommendation.service.ApprovedPolicyRuleResolver;
import com.vidhura.ai.recommendation.service.ApprovedPolicyRuleResolver.ResolvedPolicyRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Acceptance & Unit Tests for Slice 1 and Slice 2:
 * ApprovedPolicyRuleResolver & DeterministicClaimEngine.
 *
 * Verifies:
 * 1. Strict server-side gating (unapproved drafts and revision mismatches fail closed).
 * 2. Pure decimal-safe calculation arithmetic with exact mathematical reconciliation:
 *    Patient Liability = Submitted Bill - Insurer Payment
 * 3. Nuanced waiting period rules (accidental injury waiver, PED continuity credits).
 * 4. Room rent capping with proportionate deduction on associated hospital charges only.
 * 5. Disease-specific sublimits (Cataract).
 * 6. Non-medical consumables exclusion (IRDAI List 1).
 * 7. Sum Insured exhaustion vs Restoration triggers (same vs different illness).
 * 8. Deductible and co-payment order of operations.
 * 9. Fail-closed abstention (MANUAL_REVIEW_REQUIRED) on ambiguous or unverified clauses.
 */
class DeterministicClaimEngineTest {

    private StructuredPolicyRepository mockPolicyRepository;
    private ApprovedPolicyRuleResolver ruleResolver;
    private DeterministicClaimEngine engine;

    private StructuredPolicy verifiedNiaPolicy;
    private StructuredPolicy verifiedUiicPolicy;

    @BeforeEach
    void setUp() {
        mockPolicyRepository = Mockito.mock(StructuredPolicyRepository.class);
        ruleResolver = new ApprovedPolicyRuleResolver(mockPolicyRepository);
        engine = new DeterministicClaimEngine();

        // 1. Setup Approved New India Mediclaim Policy
        verifiedNiaPolicy = StructuredPolicy.builder()
                .id("pol-nia-1")
                .policyCode("THE_NEW_INDIA_ASSURANCE_COMPAN_V102425")
                .policyName("New India Mediclaim Policy")
                .provider("The New India Assurance Company Limited")
                .irdaUin("NIAHLIP25040V102425")
                .uinRevision("V102425")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .active(true)
                .policyWordingDocument("sha256:b37d2519f4fe00e75f28727168ca6aafa9093f8c723fafa7db2f12e887fcea1f")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private AC Room")
                        .roomRentCapped(false)
                        .proportionateDeductionApplies(false)
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodPedMonths(36)
                        .waitingPeriodSpecificMonths(24)
                        .copayPercentage(0.0)
                        .deductibleAmount(0.0)
                        .restorationBenefit("100% Instant recharge for same or different illness once per policy year.")
                        .diseaseSublimits(List.of(
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Cataract")
                                        .sublimitAmount(40000.0)
                                        .formattedLimit("₹40,000 per eye")
                                        .note("Contractual limit per eye including intraocular lens.")
                                        .build()
                        ))
                        .build())
                .exclusions(List.of("Non-medical consumables classified under IRDAI List 1"))
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .sectionReference("Section 2.43")
                                .verbatimText("Room rent means the amount charged by a Hospital towards Room and Boarding.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("PED")
                                .sectionReference("Section 2.36")
                                .verbatimText("Pre-existing disease covered after 36 months of continuous coverage.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("COPAY")
                                .sectionReference("Section 2.9")
                                .verbatimText("Nil co-payment for standard retail plans.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("RESTORATION")
                                .sectionReference("Section 3.2")
                                .verbatimText("100% instant recharge of sum insured for same or different illness.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("EXCLUSIONS")
                                .sectionReference("Section 4")
                                .verbatimText("Non-medical hospital consumables classified under IRDAI List 1 are excluded.")
                                .build()
                ))
                .build();

        // 2. Setup Approved UIIC Family Medicare Policy with Room Rent Cap & Different Illness Restoration
        verifiedUiicPolicy = StructuredPolicy.builder()
                .id("pol-uiic-1")
                .policyCode("UNITED_INDIA_INSURANCE_COMPANY_V042122")
                .policyName("Family Medicare Policy")
                .provider("United India Insurance Company Limited")
                .irdaUin("UIIHLIP22070V042122")
                .uinRevision("V042122")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .active(true)
                .policyWordingDocument("sha256:9728ea60fce9e3e99b21c8b70abbf5d0b43236cbbc78901856311f77920efb3b")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Capped at 1% of Sum Insured per day")
                        .roomRentCapped(true)
                        .proportionateDeductionApplies(true)
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodPedMonths(48)
                        .waitingPeriodSpecificMonths(24)
                        .copayPercentage(10.0) // 10% co-payment
                        .deductibleAmount(5000.0) // ₹5,000 deductible
                        .restorationBenefit("100% restoration of Sum Insured once in policy year for different illness.")
                        .build())
                .exclusions(List.of("Non-medical consumables under IRDAI List 1"))
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .sectionReference("Section 38")
                                .verbatimText("Room rent shall mean amount charged by hospital capped at 1% of sum insured.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("PED")
                                .sectionReference("Section 32")
                                .verbatimText("Pre-existing disease covered after 48 months continuous coverage.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("RESTORATION")
                                .sectionReference("Section 3.2")
                                .verbatimText("Restoration is eligible only for subsequent hospitalization for different illness.")
                                .build()
                ))
                .build();

        when(mockPolicyRepository.findById("pol-nia-1")).thenReturn(Optional.of(verifiedNiaPolicy));
        when(mockPolicyRepository.findById("pol-uiic-1")).thenReturn(Optional.of(verifiedUiicPolicy));
    }

    // =========================================================================
    // TEST 1 — SERVER-SIDE PUBLICATION GATE & REVISION VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Fail-Closed: Rule resolver strictly rejects unapproved policy drafts and revision mismatches")
    void testRuleResolverGateChecks() {
        // Unapproved draft (status = EXTRACTION_PENDING_REVIEW, active = false)
        StructuredPolicy unapprovedDraft = StructuredPolicy.builder()
                .id("pol-draft-1")
                .policyCode("HDFC_OPTIMA_DRAFT")
                .policyName("Optima Secure Draft")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .build();
        when(mockPolicyRepository.findById("pol-draft-1")).thenReturn(Optional.of(unapprovedDraft));

        assertThrows(IllegalStateException.class, () ->
                ruleResolver.resolveRulesForSimulation("pol-draft-1", null, new BigDecimal("500000.00")),
                "Must reject policies not in VERIFIED_OFFICIAL_CATALOG with active=true");

        // Approved policy with revision mismatch
        assertThrows(IllegalStateException.class, () ->
                ruleResolver.resolveRulesForSimulation("pol-nia-1", "V092324", new BigDecimal("500000.00")),
                "Must reject requested revision that differs from approved statutory revision V102425");
    }

    // =========================================================================
    // TEST 2 — STANDARD ELIGIBLE CLAIM WITH NO ROOM RENT CAP
    // =========================================================================

    @Test
    @DisplayName("Standard Claim: No room-rent cap, 100% room rent eligible, consumables excluded, exact reconciliation")
    void testStandardEligibleClaimNoRoomRentCap() {
        ResolvedPolicyRules rules = ruleResolver.resolveRulesForSimulation("pol-nia-1", "V102425", new BigDecimal("500000.00"));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-nia-1")
                .diagnosis("Dengue Fever")
                .patientAge(35)
                .accidentalInjury(false)
                .preExistingCondition(false)
                .policyContinuityMonths(24) // passed initial 30 days
                .admissionDate(LocalDate.of(2026, 8, 10))
                .dischargeDate(LocalDate.of(2026, 8, 14)) // 4 days
                .hospitalRoomCategoryOpted("SINGLE_PRIVATE_AC")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        createLine("line-1", ClaimSimulationDto.BillItemCategory.ROOM_RENT, "Single Private AC Room (4 days)", "5000.00", 4, "20000.00"),
                        createLine("line-2", ClaimSimulationDto.BillItemCategory.NURSING_CHARGES, "Nursing Care", "1500.00", 4, "6000.00"),
                        createLine("line-3", ClaimSimulationDto.BillItemCategory.MEDICINES, "Pharmacy & IV fluids", "24000.00", 1, "24000.00"),
                        createLine("line-4", ClaimSimulationDto.BillItemCategory.DIAGNOSTICS, "Platelet count & Blood tests", "12000.00", 1, "12000.00"),
                        createLine("line-5", ClaimSimulationDto.BillItemCategory.CONSUMABLES, "Gloves, PPE, Admin kit", "8000.00", 1, "8000.00")
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = engine.calculateClaim(request, rules);

        assertEquals(new BigDecimal("70000.00"), response.getTotalSubmittedBill());
        assertEquals(new BigDecimal("62000.00"), response.getTotalEligibleExpenses());
        assertEquals(new BigDecimal("8000.00"), response.getTotalNonCoveredExpenses());
        assertEquals(new BigDecimal("8000.00"), response.getTotalDeductions());
        assertEquals(new BigDecimal("62000.00"), response.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("8000.00"), response.getEstimatedPatientLiability());

        // Mathematical identity guarantee: Patient Liability = Submitted Bill - Insurer Payment
        assertTrue(response.isReconciled());
        assertEquals(new BigDecimal("70000.00"), response.getEstimatedPatientLiability().add(response.getEstimatedInsurerPayment()));
        assertEquals(ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, response.getCalculationStatus());
    }

    // =========================================================================
    // TEST 3 — ROOM RENT CAP & PROPORTIONATE DEDUCTIONS (ASSOCIATED CHARGES ONLY)
    // =========================================================================

    @Test
    @DisplayName("Room Rent Cap: Proportionate deduction applied strictly to associated medical charges, NOT medicines")
    void testRoomRentCapAndProportionateDeduction() {
        // UIIC Family Medicare has 1% SI room rent cap (₹5,000/day on ₹5,00,000 SI)
        ResolvedPolicyRules rules = ruleResolver.resolveRulesForSimulation("pol-uiic-1", "V042122", new BigDecimal("500000.00"));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-uiic-1")
                .diagnosis("Cardiac Evaluation")
                .patientAge(45)
                .accidentalInjury(false)
                .preExistingCondition(false)
                .policyContinuityMonths(36)
                .admissionDate(LocalDate.of(2026, 9, 1))
                .dischargeDate(LocalDate.of(2026, 9, 5)) // 4 days
                .hospitalRoomCategoryOpted("DELUXE_SUITE")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        // Actual Room Tariff: ₹10,000/day (Total ₹40,000) vs Allowed ₹5,000/day (Allowed ₹20,000) -> Ratio 50%
                        createLine("line-1", ClaimSimulationDto.BillItemCategory.ROOM_RENT, "Deluxe Suite (4 days)", "10000.00", 4, "40000.00"),
                        // Associated Charges: Surgeon (₹1,00,000) + OT (₹50,000) + Nursing (₹10,000) = ₹1,60,000 -> 50% deducted = ₹80,000
                        createLine("line-2", ClaimSimulationDto.BillItemCategory.SURGEON_FEES, "Surgeon Fee", "100000.00", 1, "100000.00"),
                        createLine("line-3", ClaimSimulationDto.BillItemCategory.OT_CHARGES, "Operation Theatre", "50000.00", 1, "50000.00"),
                        createLine("line-4", ClaimSimulationDto.BillItemCategory.NURSING_CHARGES, "Nursing Charges", "10000.00", 1, "10000.00"),
                        // Fixed Medical Charges: Pharmacy (₹40,000) - MUST NOT suffer proportionate deduction!
                        createLine("line-5", ClaimSimulationDto.BillItemCategory.MEDICINES, "Cardiovascular Drugs", "40000.00", 1, "40000.00")
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = engine.calculateClaim(request, rules);

        assertEquals(new BigDecimal("240000.00"), response.getTotalSubmittedBill());

        // Room rent excess = ₹40,000 - ₹20,000 = ₹20,000
        // Proportionate deduction on associated charges = 50% of ₹1,60,000 = ₹80,000
        // Admissible before cost-sharing = Allowed Room (₹20,000) + Allowed Associated (₹80,000) + Medicines (₹40,000) = ₹1,40,000
        // Deductible = ₹5,000 -> Net Admissible = ₹1,35,000
        // Co-pay = 10% of ₹1,35,000 = ₹13,500
        // Insurer Payment = ₹1,35,000 - ₹13,500 = ₹1,21,500.00
        // Patient Liability = ₹240,000 - ₹121,500 = ₹118,500.00
        assertEquals(new BigDecimal("121500.00"), response.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("118500.00"), response.getEstimatedPatientLiability());

        // Exact reconciliation verification
        assertTrue(response.isReconciled());
        assertEquals(new BigDecimal("240000.00"), response.getEstimatedPatientLiability().add(response.getEstimatedInsurerPayment()));
    }

    // =========================================================================
    // TEST 4 — WAITING PERIOD NUANCES (ACCIDENTAL INJURY WAIVER & CONTINUITY)
    // =========================================================================

    @Test
    @DisplayName("Waiting Period: Illness rejected in initial 30 days, but Accidental Trauma is waived and paid")
    void testWaitingPeriodAccidentalTraumaWaiver() {
        ResolvedPolicyRules rules = ruleResolver.resolveRulesForSimulation("pol-nia-1", "V102425", new BigDecimal("500000.00"));

        // Case A: Illness admission on Day 15 (Continuity = 0 months) -> Rejected
        ClaimSimulationDto.ClaimSimulationRequest illnessReq = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-nia-1")
                .diagnosis("Typhoid Fever")
                .accidentalInjury(false)
                .policyContinuityMonths(0)
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(createLine("l-1", ClaimSimulationDto.BillItemCategory.MEDICINES, "Med", "25000.00", 1, "25000.00")))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse illnessResp = engine.calculateClaim(illnessReq, rules);
        assertEquals(BigDecimal.ZERO.setScale(2), illnessResp.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("25000.00"), illnessResp.getEstimatedPatientLiability());
        assertTrue(illnessResp.getDeductionBreakdown().stream().anyMatch(d -> "WAITING_PERIOD_INELIGIBLE".equals(d.getDeductionType())));

        // Case B: Road Traffic Accident on Day 15 (Continuity = 0 months) -> Waived!
        ClaimSimulationDto.ClaimSimulationRequest accidentReq = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-nia-1")
                .diagnosis("Compound Fracture Femur (Road Accident)")
                .accidentalInjury(true)
                .policyContinuityMonths(0)
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        createLine("l-1", ClaimSimulationDto.BillItemCategory.SURGEON_FEES, "Orthopedic Surgery", "40000.00", 1, "40000.00"),
                        createLine("l-2", ClaimSimulationDto.BillItemCategory.MEDICINES, "Pain relief & Antibiotics", "10000.00", 1, "10000.00")
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse accidentResp = engine.calculateClaim(accidentReq, rules);
        assertEquals(new BigDecimal("50000.00"), accidentResp.getEstimatedInsurerPayment());
        assertEquals(BigDecimal.ZERO.setScale(2), accidentResp.getEstimatedPatientLiability());
        assertTrue(accidentResp.isReconciled());
    }

    // =========================================================================
    // TEST 5 — DISEASE SUBLIMITS (CATARACT)
    // =========================================================================

    @Test
    @DisplayName("Sublimits: Cataract surgery capped at ₹40,000 contractual ceiling")
    void testDiseaseSublimits() {
        ResolvedPolicyRules rules = ruleResolver.resolveRulesForSimulation("pol-nia-1", "V102425", new BigDecimal("500000.00"));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-nia-1")
                .diagnosis("Cataract Surgery Left Eye")
                .policyContinuityMonths(36) // passed specific illness waiting period
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        createLine("l-1", ClaimSimulationDto.BillItemCategory.SURGEON_FEES, "Phacoemulsification Surgery", "45000.00", 1, "45000.00"),
                        createLine("l-2", ClaimSimulationDto.BillItemCategory.IMPLANTS, "Foldable Hydrophobic IOL Lens", "20000.00", 1, "20000.00")
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = engine.calculateClaim(request, rules);

        assertEquals(new BigDecimal("65000.00"), response.getTotalSubmittedBill());
        // Sublimit of ₹40,000 applies -> Excess ₹25,000 deducted
        assertEquals(new BigDecimal("40000.00"), response.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("25000.00"), response.getEstimatedPatientLiability());
        assertTrue(response.isReconciled());
        assertTrue(response.getDeductionBreakdown().stream().anyMatch(d -> "DISEASE_SUBLIMIT_EXCESS".equals(d.getDeductionType())));
    }

    // =========================================================================
    // TEST 6 — SUM INSURED EXHAUSTION & RESTORATION CONDITIONS
    // =========================================================================

    @Test
    @DisplayName("Restoration: Triggered for same or different illness in New India, but blocked for same illness in UIIC")
    void testRestorationBenefitConditions() {
        // UIIC: Restoration explicitly blocked for same illness
        ResolvedPolicyRules uiicRules = ruleResolver.resolveRulesForSimulation("pol-uiic-1", "V042122", new BigDecimal("500000.00"));

        ClaimSimulationDto.ClaimSimulationRequest sameIllnessReq = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-uiic-1")
                .diagnosis("Chronic Renal Failure Follow-up")
                .policyContinuityMonths(48)
                .selectedSumInsured(new BigDecimal("500000.00"))
                .priorClaimsInCurrentYear(new BigDecimal("500000.00")) // Base SI completely exhausted!
                .subsequentClaimForSameIllness(true)
                .billLines(List.of(createLine("l-1", ClaimSimulationDto.BillItemCategory.MEDICINES, "Dialysis & Care", "100000.00", 1, "100000.00")))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse uiicResp = engine.calculateClaim(sameIllnessReq, uiicRules);
        assertFalse(uiicResp.isRestorationTriggered());
        assertEquals(BigDecimal.ZERO.setScale(2), uiicResp.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("100000.00"), uiicResp.getEstimatedPatientLiability());
        assertTrue(uiicResp.getDeductionBreakdown().stream().anyMatch(d -> "SUM_INSURED_EXHAUSTION".equals(d.getDeductionType())));

        // New India: Restoration triggered for same or different illness
        ResolvedPolicyRules niaRules = ruleResolver.resolveRulesForSimulation("pol-nia-1", "V102425", new BigDecimal("500000.00"));
        ClaimSimulationDto.ClaimSimulationResponse niaResp = engine.calculateClaim(sameIllnessReq, niaRules);

        assertTrue(niaResp.isRestorationTriggered());
        assertEquals(new BigDecimal("100000.00"), niaResp.getRestorationAmountGranted());
        assertEquals(new BigDecimal("100000.00"), niaResp.getEstimatedInsurerPayment());
        assertEquals(BigDecimal.ZERO.setScale(2), niaResp.getEstimatedPatientLiability());
        assertTrue(niaResp.isReconciled());
    }

    // =========================================================================
    // TEST 7 — FIRST-CLASS UNRESOLVED RULES (MANUAL_REVIEW_REQUIRED)
    // =========================================================================

    @Test
    @DisplayName("Fail-Closed Abstention: Material uncertainty in policy wording halts simulation with MANUAL_REVIEW_REQUIRED")
    void testMaterialUncertaintyAbstention() {
        // Policy with uncertain room rent clause
        StructuredPolicy uncertainPolicy = StructuredPolicy.builder()
                .id("pol-uncertain-1")
                .policyCode("UNCERTAIN_HEALTH_POLICY")
                .policyName("Uncertain Health Care")
                .irdaUin("UNCHLIP21000V012024")
                .uinRevision("V012024")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .active(true)
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentCapped(true).build())
                .contractUncertainFields(List.of("UNCERTAIN_ROOM_RENT_CLAUSE"))
                .build();
        when(mockPolicyRepository.findById("pol-uncertain-1")).thenReturn(Optional.of(uncertainPolicy));

        ResolvedPolicyRules rules = ruleResolver.resolveRulesForSimulation("pol-uncertain-1", "V012024", new BigDecimal("500000.00"));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("pol-uncertain-1")
                .diagnosis("Pneumonia")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(createLine("l-1", ClaimSimulationDto.BillItemCategory.ROOM_RENT, "Private Room", "8000.00", 2, "16000.00")))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = engine.calculateClaim(request, rules);

        assertEquals(ClaimSimulationDto.CalculationStatus.MANUAL_REVIEW_REQUIRED, response.getCalculationStatus());
        assertTrue(response.getStatusReason().contains("Room rent"));
        assertFalse(response.getUnresolvedRules().isEmpty());
    }

    private ClaimSimulationDto.BillLineItemDto createLine(String id, ClaimSimulationDto.BillItemCategory cat, String desc, String price, int qty, String total) {
        return ClaimSimulationDto.BillLineItemDto.builder()
                .lineId(id)
                .category(cat)
                .description(desc)
                .unitPrice(new BigDecimal(price))
                .quantity(qty)
                .totalAmount(new BigDecimal(total))
                .build();
    }
}
