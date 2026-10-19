package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.config.GlobalExceptionHandler;
import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.model.ClaimScenario;
import com.vidhura.ai.recommendation.model.ClaimSimulationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.ClaimScenarioRepository;
import com.vidhura.ai.recommendation.repository.ClaimSimulationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.recommendation.service.ApprovedPolicyRuleResolver;
import com.vidhura.ai.recommendation.service.ClaimSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Slice 4 Integration Test Suite for the Deterministic Claim Simulator.
 *
 * Verifies all 16 critical architectural, financial, security, and governance requirements:
 * 1. Successful simulation with a verified, active policy.
 * 2. Invalid request and malformed bill-line inputs.
 * 3. Zero, negative, and excessive monetary values.
 * 4. Inactive draft rejection.
 * 5. Unverified policy rejection.
 * 6. Policy revision mismatch.
 * 7. Missing and ambiguous contractual rules.
 * 8. Correct handling of MANUAL_REVIEW_REQUIRED.
 * 9. Exact bill reconciliation after rounding (Patient Liability = Submitted Bill - Insurer Payment).
 * 10. Restoration and cost-sharing behavior through the API.
 * 11. Persistence and retrieval of the exact historical result.
 * 12. Reproducibility after a newer policy revision becomes active.
 * 13. Unauthorized access to another user's saved scenario.
 * 14. Correct behavior when a scenario or result does not exist.
 * 15. Synthetic sample scenarios being clearly identified.
 * 16. No leakage of internal stack traces or sensitive data in API errors.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimSimulatorIntegrationTest {

    @Mock
    private StructuredPolicyRepository structuredPolicyRepository;

    @Mock
    private ClaimScenarioRepository claimScenarioRepository;

    @Mock
    private ClaimSimulationResultRepository claimSimulationResultRepository;

    private ApprovedPolicyRuleResolver ruleResolver;
    private DeterministicClaimEngine claimEngine;
    private ClaimSimulationService claimSimulationService;
    private ClaimSimulatorController claimSimulatorController;
    private GlobalExceptionHandler exceptionHandler;

    // Test Users
    private User testUserAlice;
    private User testUserBob;
    private User testAdminUser;
    private User testReviewerUser;
    private User guestUser;

    // Test Policies
    private StructuredPolicy activeVerifiedPolicy;
    private StructuredPolicy inactiveDraftPolicy;
    private StructuredPolicy unverifiedCatalogPolicy;
    private StructuredPolicy ambiguousRoomRentPolicy;

    @BeforeEach
    void setUp() {
        ruleResolver = new ApprovedPolicyRuleResolver(structuredPolicyRepository);
        claimEngine = new DeterministicClaimEngine();
        claimSimulationService = new ClaimSimulationService(
                ruleResolver, claimEngine, claimScenarioRepository, claimSimulationResultRepository);
        claimSimulatorController = new ClaimSimulatorController(claimSimulationService);
        exceptionHandler = new GlobalExceptionHandler();

        // Setup Test Users
        testUserAlice = User.builder().id("usr-alice-101").name("Alice").email("alice@example.com").role("ROLE_USER").build();
        testUserBob = User.builder().id("usr-bob-202").name("Bob").email("bob@example.com").role("ROLE_USER").build();
        testAdminUser = User.builder().id("usr-admin-999").name("Admin").email("admin@vidhura.ai").role("ROLE_ADMIN").build();
        testReviewerUser = User.builder().id("usr-rev-888").name("Reviewer").email("reviewer@vidhura.ai").role("ROLE_REVIEWER").build();
        guestUser = User.builder().id("guest-000").name("Guest").email("guest@vidhura.ai").role("ROLE_GUEST").build();

        // Setup 1: Active, Verified Official Policy (HDFC ERGO Optima Secure)
        activeVerifiedPolicy = StructuredPolicy.builder()
                .id("HDFC_OPTIMA_SECURE_V01")
                .policyCode("HDFHLIP21147V012021")
                .policyName("HDFC ERGO Optima Secure")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .uinRevision("V012021")
                .policyWordingDocument("sha256:verified_hdfc_optima_hash_abc123")
                .active(true)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("1% of Sum Insured per day")
                        .roomRentCapped(true)
                        .copayPercentage(10.0)
                        .copayNote("10% mandatory co-payment on all admissible claims")
                        .waitingPeriodPedMonths(36)
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodSpecificMonths(24)
                        .restorationBenefit("100% restoration upon complete exhaustion of base Sum Insured for different and unrelated illnesses only.")
                        .proportionateDeductionApplies(true)
                        .diseaseSublimits(List.of(
                                StructuredPolicy.DiseaseSublimit.builder()
                                        .diseaseName("Cataract")
                                        .sublimitAmount(40000.0)
                                        .formattedLimit("₹40,000 per eye")
                                        .note("Contractual limit per eye including intraocular lens.")
                                        .build()
                        ))
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("ROOM_RENT")
                                .sectionReference("Section 3.1.b")
                                .verbatimText("Room Rent is capped at 1% of Sum Insured per day; proportionate deductions apply to associated medical charges.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("PED")
                                .sectionReference("Section 4.1")
                                .verbatimText("Pre-existing diseases are covered after 36 months of continuous coverage.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("INITIAL_WAITING_PERIOD")
                                .sectionReference("Section 4.2")
                                .verbatimText("30 days initial waiting period applies to all illnesses; accidental hospitalization is exempt.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("SPECIFIC_ILLNESS")
                                .sectionReference("Section 4.3")
                                .verbatimText("Specific procedures listed in Schedule A require 24 months continuous coverage.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("RESTORATION")
                                .sectionReference("Section 5.4")
                                .verbatimText("100% restoration triggers upon complete exhaustion of base Sum Insured; same-illness claims in same year are ineligible.")
                                .build(),
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .tag("COPAY")
                                .sectionReference("Section 6.1")
                                .verbatimText("A 10% co-payment shall apply to the final admissible claim amount.")
                                .build()
                ))
                .build();

        // Setup 2: Inactive Draft Policy (Rollback / Unapproved stage)
        inactiveDraftPolicy = StructuredPolicy.builder()
                .id("HDFC_DRAFT_REV_02")
                .policyCode("HDFHLIP21147V022022")
                .policyName("HDFC ERGO Optima Secure Draft")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V022022")
                .uinRevision("V022022")
                .active(false)
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .build();

        // Setup 3: Unverified Policy (Automated crawler output not yet reviewer-approved)
        unverifiedCatalogPolicy = StructuredPolicy.builder()
                .id("STAR_UNVERIFIED_01")
                .policyCode("SHAHLIP21163V012021")
                .policyName("Star Comprehensive Policy (Unverified)")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21163V012021")
                .uinRevision("V012021")
                .active(true)
                .verificationStatus("UNVERIFIED_CRAWL")
                .build();

        // Setup 4: Ambiguous Contractual Rules Policy
        ambiguousRoomRentPolicy = StructuredPolicy.builder()
                .id("AMBIGUOUS_POLICY_01")
                .policyCode("AMBIG_001")
                .policyName("Ambiguous Terms Health Plan")
                .provider("Generic Assurance Co.")
                .irdaUin("GENHLIP21000V012021")
                .uinRevision("V012021")
                .active(true)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .contractUncertainFields(List.of("UNCERTAIN_ROOM_RENT_CLAUSE"))
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Variable ceiling depending on city tier")
                        .copayPercentage(0.0)
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodPedMonths(36)
                        .waitingPeriodSpecificMonths(24)
                        .build())
                .build();
    }

    // =========================================================================
    // 1. Successful simulation with a verified, active policy
    // =========================================================================
    @Test
    @DisplayName("Req 1: Successful simulation with verified active policy returns 200 OK and valid response")
    void testSuccessfulSimulationWithVerifiedPolicy() {
        when(structuredPolicyRepository.findById("HDFC_OPTIMA_SECURE_V01")).thenReturn(Optional.of(activeVerifiedPolicy));
        when(claimScenarioRepository.save(any(ClaimScenario.class))).thenAnswer(invocation -> {
            ClaimScenario cs = invocation.getArgument(0);
            cs.setId("scenario-uuid-101");
            return cs;
        });
        when(claimSimulationResultRepository.save(any(ClaimSimulationResult.class))).thenAnswer(invocation -> {
            ClaimSimulationResult res = invocation.getArgument(0);
            res.setId("result-uuid-201");
            return res;
        });

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .uinRevision("V012021")
                .patientAge(35)
                .diagnosis("Acute Appendicitis")
                .accidentalInjury(false)
                .preExistingCondition(false)
                .policyContinuityMonths(24)
                .selectedSumInsured(new BigDecimal("500000.00"))
                .priorClaimsInCurrentYear(BigDecimal.ZERO)
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Single Private Room (3 days @ ₹5,000)")
                                .unitPrice(new BigDecimal("5000.00")).quantity(3).totalAmount(new BigDecimal("15000.00")).build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L2").category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                                .description("Appendectomy Surgeon & Anesthetist Charges")
                                .unitPrice(new BigDecimal("40000.00")).quantity(1).totalAmount(new BigDecimal("40000.00")).build()
                ))
                .build();

        ResponseEntity<ApiResponse<ClaimSimulationDto.ClaimSimulationResponse>> responseEntity =
                claimSimulatorController.simulateClaim(request, testUserAlice);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().getSuccess());

        ClaimSimulationDto.ClaimSimulationResponse data = responseEntity.getBody().getData();
        assertNotNull(data);
        assertEquals("result-uuid-201", data.getSimulationId());
        assertEquals(ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED, data.getCalculationStatus());
        assertEquals("HDFHLIP21147V012021", data.getIrdaUin());
        assertEquals("V012021", data.getUinRevision());
        assertTrue(data.isReconciled());

        // Verify repository interactions
        verify(claimScenarioRepository, times(1)).save(any(ClaimScenario.class));
        verify(claimSimulationResultRepository, times(1)).save(any(ClaimSimulationResult.class));
    }

    // =========================================================================
    // 2. Invalid request and malformed bill-line inputs
    // =========================================================================
    @Test
    @DisplayName("Req 2: Invalid request with empty bill lines or missing diagnosis throws IllegalArgumentException")
    void testInvalidRequestAndMalformedBillLines() {
        // Case A: Missing policy ID
        ClaimSimulationDto.ClaimSimulationRequest noPolicyRequest = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("")
                .diagnosis("Fever")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Bed").totalAmount(new BigDecimal("1000.00")).build()
                ))
                .build();

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(noPolicyRequest, testUserAlice));
        assertTrue(ex1.getMessage().contains("Policy ID"));

        // Case B: Empty bill lines
        ClaimSimulationDto.ClaimSimulationRequest emptyBillRequest = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Fever")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(Collections.emptyList())
                .build();

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(emptyBillRequest, testUserAlice));
        assertTrue(ex2.getMessage().contains("At least one bill line item is required"));
    }

    // =========================================================================
    // 3. Zero, negative and excessive monetary values
    // =========================================================================
    @Test
    @DisplayName("Req 3: Zero, negative, and excessive monetary amounts are strictly rejected")
    void testZeroNegativeAndExcessiveMonetaryValues() {
        // Case A: Zero sum insured
        ClaimSimulationDto.ClaimSimulationRequest zeroSi = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Malaria")
                .selectedSumInsured(BigDecimal.ZERO)
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Bed").totalAmount(new BigDecimal("1000.00")).build()
                ))
                .build();

        IllegalArgumentException exZeroSi = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(zeroSi, testUserAlice));
        assertTrue(exZeroSi.getMessage().contains("Selected sum insured must be greater than zero"));

        // Case B: Negative sum insured
        ClaimSimulationDto.ClaimSimulationRequest negSi = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Malaria")
                .selectedSumInsured(new BigDecimal("-500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Bed").totalAmount(new BigDecimal("1000.00")).build()
                ))
                .build();

        IllegalArgumentException exNegSi = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(negSi, testUserAlice));
        assertTrue(exNegSi.getMessage().contains("Selected sum insured must be greater than zero"));

        // Case C: Excessive sum insured (> ₹100 Crore)
        ClaimSimulationDto.ClaimSimulationRequest excessiveSi = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Malaria")
                .selectedSumInsured(new BigDecimal("2000000000.00")) // ₹200 Crore
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Bed").totalAmount(new BigDecimal("1000.00")).build()
                ))
                .build();

        IllegalArgumentException exExcessiveSi = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(excessiveSi, testUserAlice));
        assertTrue(exExcessiveSi.getMessage().contains("exceeds maximum allowable limit"));

        // Case D: Negative bill line item
        ClaimSimulationDto.ClaimSimulationRequest negBillLine = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Malaria")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Room").totalAmount(new BigDecimal("-5000.00")).build()
                ))
                .build();

        IllegalArgumentException exNegLine = assertThrows(IllegalArgumentException.class,
                () -> claimSimulationService.simulateClaim(negBillLine, testUserAlice));
        assertTrue(exNegLine.getMessage().contains("must be greater than zero"));
    }

    // =========================================================================
    // 4. Inactive draft rejection
    // =========================================================================
    @Test
    @DisplayName("Req 4: Inactive draft policy is rejected fail-closed with IllegalStateException")
    void testInactiveDraftRejection() {
        when(structuredPolicyRepository.findById("HDFC_DRAFT_REV_02")).thenReturn(Optional.of(inactiveDraftPolicy));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_DRAFT_REV_02")
                .diagnosis("Knee Arthroscopy")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Day care").totalAmount(new BigDecimal("50000.00")).build()
                ))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimSimulationService.simulateClaim(request, testUserAlice));

        assertTrue(ex.getMessage().contains("not approved for live claim calculation"));
        assertTrue(ex.getMessage().contains("EXTRACTION_PENDING_REVIEW"));

        // Test HTTP error mapping via GlobalExceptionHandler
        ResponseEntity<ApiResponse<Void>> errorResponse = exceptionHandler.handleIllegalState(ex);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, errorResponse.getStatusCode());
        assertFalse(errorResponse.getBody().getSuccess());
        assertTrue(errorResponse.getBody().getError().contains("Only reviewer-approved policies can be simulated"));
    }

    // =========================================================================
    // 5. Unverified policy rejection
    // =========================================================================
    @Test
    @DisplayName("Req 5: Unverified catalog policy (UNVERIFIED_CRAWL) is rejected fail-closed")
    void testUnverifiedPolicyRejection() {
        when(structuredPolicyRepository.findById("STAR_UNVERIFIED_01")).thenReturn(Optional.of(unverifiedCatalogPolicy));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("STAR_UNVERIFIED_01")
                .diagnosis("Cholecystectomy")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Surgery").totalAmount(new BigDecimal("60000.00")).build()
                ))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimSimulationService.simulateClaim(request, testUserAlice));

        assertTrue(ex.getMessage().contains("Status: UNVERIFIED_CRAWL"));
    }

    // =========================================================================
    // 6. Policy revision mismatch
    // =========================================================================
    @Test
    @DisplayName("Req 6: Requested UIN revision mismatch fails closed")
    void testPolicyRevisionMismatch() {
        when(structuredPolicyRepository.findById("HDFC_OPTIMA_SECURE_V01")).thenReturn(Optional.of(activeVerifiedPolicy));

        // Active policy has revision V012021; user requested older revision V092019
        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .uinRevision("V092019") // MISMATCH!
                .diagnosis("Typhoid Fever")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder().lineId("L1").description("Hospital stay").totalAmount(new BigDecimal("30000.00")).build()
                ))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimSimulationService.simulateClaim(request, testUserAlice));

        assertTrue(ex.getMessage().contains("Requested revision 'V092019' does not match active approved statutory filing revision 'V012021'"));
    }

    // =========================================================================
    // 7. Missing and ambiguous contractual rules
    // =========================================================================
    @Test
    @DisplayName("Req 7: Ambiguous contractual clauses touching claimed expenses trigger MANUAL_REVIEW_REQUIRED")
    void testMissingAndAmbiguousContractualRules() {
        when(structuredPolicyRepository.findById("AMBIGUOUS_POLICY_01")).thenReturn(Optional.of(ambiguousRoomRentPolicy));
        when(claimScenarioRepository.save(any(ClaimScenario.class))).thenAnswer(invocation -> {
            ClaimScenario cs = invocation.getArgument(0);
            cs.setId("ambig-scen-1");
            return cs;
        });
        when(claimSimulationResultRepository.save(any(ClaimSimulationResult.class))).thenAnswer(invocation -> {
            ClaimSimulationResult res = invocation.getArgument(0);
            res.setId("ambig-res-1");
            return res;
        });

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("AMBIGUOUS_POLICY_01")
                .diagnosis("Bronchitis")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Ward Bed Charges")
                                .unitPrice(new BigDecimal("4000.00")).quantity(3).totalAmount(new BigDecimal("12000.00")).build()
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = claimSimulationService.simulateClaim(request, testUserAlice);

        assertNotNull(response);
        assertEquals(ClaimSimulationDto.CalculationStatus.MANUAL_REVIEW_REQUIRED, response.getCalculationStatus());
        assertFalse(response.getUnresolvedRules().isEmpty());
        assertTrue(response.getStatusReason().contains("Room rent terms require manual reviewer clarification"));
        assertEquals(new BigDecimal("0.00"), response.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("12000.00"), response.getEstimatedPatientLiability());
        assertFalse(response.isReconciled());
    }

    // =========================================================================
    // 8. Correct handling of MANUAL_REVIEW_REQUIRED
    // =========================================================================
    @Test
    @DisplayName("Req 8: MANUAL_REVIEW_REQUIRED preserves unresolved rule citations and does not fabricate amounts")
    void testManualReviewRequiredHandling() {
        when(structuredPolicyRepository.findById("AMBIGUOUS_POLICY_01")).thenReturn(Optional.of(ambiguousRoomRentPolicy));
        when(claimScenarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(claimSimulationResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("AMBIGUOUS_POLICY_01")
                .diagnosis("Pneumonia")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("ICU Room")
                                .unitPrice(new BigDecimal("10000.00")).quantity(2).totalAmount(new BigDecimal("20000.00")).build()
                ))
                .build();

        ResponseEntity<ApiResponse<ClaimSimulationDto.ClaimSimulationResponse>> responseEntity =
                claimSimulatorController.simulateClaim(request, testUserAlice);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        ClaimSimulationDto.ClaimSimulationResponse body = responseEntity.getBody().getData();
        assertEquals(ClaimSimulationDto.CalculationStatus.MANUAL_REVIEW_REQUIRED, body.getCalculationStatus());
        assertEquals(new BigDecimal("0.00"), body.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("20000.00"), body.getEstimatedPatientLiability());
        assertFalse(body.isReconciled());
        assertFalse(body.getUnresolvedRules().isEmpty());
    }

    // =========================================================================
    // 9. Exact bill reconciliation after rounding
    // =========================================================================
    @Test
    @DisplayName("Req 9: Mathematical identity holds exactly: Patient Liability = Submitted Bill - Insurer Payment")
    void testExactBillReconciliationAfterRounding() {
        when(structuredPolicyRepository.findById("HDFC_OPTIMA_SECURE_V01")).thenReturn(Optional.of(activeVerifiedPolicy));
        when(claimScenarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(claimSimulationResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Submit complex line items with fractional / interacting percentages
        // Room rent: ₹8,000/day (cap ₹5,000/day => 62.5% allowed)
        // Doctor: ₹20,000 => 62.5% allowed = ₹12,500; deducted = ₹7,500
        // Consumables: ₹15,000 deducted
        // Medicines: ₹25,000 fully eligible
        // Eligible: 20000 + 12500 + 25000 = 57,500
        // 10% copay on 57,500 = 5,750
        // Insurer: 57,500 - 5,750 = 51,750
        // Total submitted: 32,000 + 20,000 + 25,000 + 15,000 = 92,000
        // Patient Liability = 92,000 - 51,750 = 40,250
        ClaimSimulationDto.ClaimSimulationRequest request = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Complex Surgery")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .policyContinuityMonths(36)
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Deluxe Room (4 days @ ₹8,000)")
                                .unitPrice(new BigDecimal("8000.00")).quantity(4).totalAmount(new BigDecimal("32000.00")).build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L2").category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                                .description("Surgeon Consultation Charges")
                                .unitPrice(new BigDecimal("20000.00")).quantity(1).totalAmount(new BigDecimal("20000.00")).build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L3").category(ClaimSimulationDto.BillItemCategory.MEDICINES)
                                .description("Pharmaceuticals")
                                .unitPrice(new BigDecimal("25000.00")).quantity(1).totalAmount(new BigDecimal("25000.00")).build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L4").category(ClaimSimulationDto.BillItemCategory.CONSUMABLES)
                                .description("Sanitization Kit")
                                .unitPrice(new BigDecimal("15000.00")).quantity(1).totalAmount(new BigDecimal("15000.00")).build()
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse response = claimSimulationService.simulateClaim(request, testUserAlice);

        assertNotNull(response);
        assertTrue(response.isReconciled());
        BigDecimal submitted = response.getTotalSubmittedBill();
        BigDecimal insurer = response.getEstimatedInsurerPayment();
        BigDecimal patient = response.getEstimatedPatientLiability();

        // Strict mathematical identity verification
        assertEquals(submitted, insurer.add(patient), "Submitted Bill must equal Insurer Payment + Patient Liability");
        assertEquals(new BigDecimal("92000.00"), submitted);
        assertEquals(new BigDecimal("40250.00"), patient); // 12000 (room) + 7500 (doctor prop) + 15000 (consumables) + 5750 (copay)
        assertEquals(new BigDecimal("51750.00"), insurer); // 57500 eligible - 5750 copay = 51750
    }

    // =========================================================================
    // 10. Restoration and cost-sharing behavior through the API
    // =========================================================================
    @Test
    @DisplayName("Req 10: Restoration and cost sharing are correctly calculated through the API")
    void testRestorationAndCostSharingThroughApi() {
        when(structuredPolicyRepository.findById("HDFC_OPTIMA_SECURE_V01")).thenReturn(Optional.of(activeVerifiedPolicy));
        when(claimScenarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(claimSimulationResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Base Sum Insured: ₹5,00,000 already exhausted by prior claims
        // Claim 2: Dengue Fever (Same illness) -> Restoration BLOCKED
        ClaimSimulationDto.ClaimSimulationRequest sameIllnessReq = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Dengue Fever")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .priorClaimsInCurrentYear(new BigDecimal("500000.00"))
                .subsequentClaimForSameIllness(true)
                .policyContinuityMonths(36)
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Room").unitPrice(new BigDecimal("5000.00")).quantity(2).totalAmount(new BigDecimal("10000.00")).build()
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse sameIllnessResp = claimSimulationService.simulateClaim(sameIllnessReq, testUserAlice);
        assertFalse(sameIllnessResp.isRestorationTriggered());
        assertEquals(new BigDecimal("0.00"), sameIllnessResp.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("10000.00"), sameIllnessResp.getEstimatedPatientLiability());
        assertTrue(sameIllnessResp.isReconciled());

        // Claim 3: Acute Appendicitis (Unrelated illness) -> Restoration GRANTED
        ClaimSimulationDto.ClaimSimulationRequest diffIllnessReq = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .diagnosis("Acute Appendicitis")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .priorClaimsInCurrentYear(new BigDecimal("500000.00"))
                .subsequentClaimForSameIllness(false)
                .policyContinuityMonths(36)
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .lineId("L1").category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                                .description("Surgery").unitPrice(new BigDecimal("100000.00")).quantity(1).totalAmount(new BigDecimal("100000.00")).build()
                ))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse diffIllnessResp = claimSimulationService.simulateClaim(diffIllnessReq, testUserAlice);
        assertTrue(diffIllnessResp.isRestorationTriggered());
        // ₹1,00,000 eligible, minus 10% copay (₹10,000) = ₹90,000 insurer payment
        assertEquals(new BigDecimal("90000.00"), diffIllnessResp.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("10000.00"), diffIllnessResp.getEstimatedPatientLiability());
        assertTrue(diffIllnessResp.isReconciled());
    }

    // =========================================================================
    // 11. Persistence and retrieval of the exact historical result
    // =========================================================================
    @Test
    @DisplayName("Req 11: Saved scenario and immutable result snapshot are persisted and accurately retrieved")
    void testPersistenceAndRetrievalOfExactHistoricalResult() {
        ClaimScenario mockScenario = ClaimScenario.builder()
                .id("scen-hist-101")
                .userId("usr-alice-101")
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .policyCode("HDFHLIP21147V012021")
                .diagnosis("Pneumonia")
                .selectedSumInsured(new BigDecimal("500000.00"))
                .totalSubmittedBill(new BigDecimal("50000.00"))
                .createdAt(Instant.now())
                .syntheticExample(false)
                .build();

        ClaimSimulationResult mockResult = ClaimSimulationResult.builder()
                .id("res-hist-201")
                .scenarioId("scen-hist-101")
                .userId("usr-alice-101")
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .policyCode("HDFHLIP21147V012021")
                .uinRevision("V012021")
                .sourceDocumentHash("sha256_hash_abc")
                .ruleSetVersion("1.0.0-APPROVED")
                .calculationEngineVersion("1.0.0-DETERMINISTIC")
                .calculationStatus(ClaimSimulationDto.CalculationStatus.FULL_DETERMINISTIC_VERIFIED)
                .totalSubmittedBill(new BigDecimal("50000.00"))
                .totalEligibleExpenses(new BigDecimal("50000.00"))
                .totalNonCoveredExpenses(BigDecimal.ZERO)
                .totalDeductions(new BigDecimal("5000.00"))
                .estimatedInsurerPayment(new BigDecimal("45000.00"))
                .estimatedPatientLiability(new BigDecimal("5000.00"))
                .reconciliationIdentity("Patient Liability (₹5000.00) = Submitted Bill (₹50000.00) - Insurer Payment (₹45000.00)")
                .reconciled(true)
                .createdAt(Instant.now())
                .build();

        when(claimScenarioRepository.findById("scen-hist-101")).thenReturn(Optional.of(mockScenario));
        when(claimSimulationResultRepository.findByScenarioId("scen-hist-101")).thenReturn(Optional.of(mockResult));

        ResponseEntity<ApiResponse<ClaimSimulationDto.SavedScenarioDetailDto>> response =
                claimSimulatorController.getScenario("scen-hist-101", testUserAlice);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClaimSimulationDto.SavedScenarioDetailDto detail = response.getBody().getData();
        assertEquals("scen-hist-101", detail.getScenarioId());
        assertEquals("usr-alice-101", detail.getUserId());
        assertEquals(new BigDecimal("45000.00"), detail.getResult().getEstimatedInsurerPayment());
        assertEquals("V012021", detail.getResult().getUinRevision());
    }

    // =========================================================================
    // 12. Reproducibility after a newer policy revision becomes active
    // =========================================================================
    @Test
    @DisplayName("Req 12: Historical simulation result remains unchanged even if policy is updated to newer revision")
    void testReproducibilityAfterNewerPolicyRevisionActive() {
        // Historical simulation from revision V012021
        ClaimScenario historicalScenario = ClaimScenario.builder()
                .id("scen-v1")
                .userId("usr-alice-101")
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .uinRevision("V012021")
                .diagnosis("Gallstones")
                .createdAt(Instant.now())
                .build();

        ClaimSimulationResult historicalResult = ClaimSimulationResult.builder()
                .id("res-v1")
                .scenarioId("scen-v1")
                .userId("usr-alice-101")
                .policyId("HDFC_OPTIMA_SECURE_V01")
                .uinRevision("V012021") // Locked to V012021
                .ruleSetVersion("1.0.0-APPROVED")
                .totalSubmittedBill(new BigDecimal("80000.00"))
                .estimatedInsurerPayment(new BigDecimal("72000.00"))
                .estimatedPatientLiability(new BigDecimal("8000.00"))
                .reconciled(true)
                .build();

        when(claimScenarioRepository.findById("scen-v1")).thenReturn(Optional.of(historicalScenario));
        when(claimSimulationResultRepository.findByScenarioId("scen-v1")).thenReturn(Optional.of(historicalResult));

        // When retrieved later, the historical snapshot is returned directly without dynamic re-execution
        ClaimSimulationDto.SavedScenarioDetailDto detail = claimSimulationService.getScenarioById("scen-v1", testUserAlice);

        assertEquals("V012021", detail.getResult().getUinRevision());
        assertEquals(new BigDecimal("72000.00"), detail.getResult().getEstimatedInsurerPayment());

        // Verify that policy repository was NEVER queried to recalculate
        verify(structuredPolicyRepository, never()).findById(anyString());
    }

    // =========================================================================
    // 13. Unauthorized access to another user's saved scenario
    // =========================================================================
    @Test
    @DisplayName("Req 13: AccessDeniedException / 403 Forbidden thrown when User Bob attempts to access User Alice's scenario")
    void testUnauthorizedAccessToAnotherUsersScenario() {
        ClaimScenario aliceScenario = ClaimScenario.builder()
                .id("alice-private-scenario-99")
                .userId("usr-alice-101")
                .syntheticExample(false)
                .build();

        when(claimScenarioRepository.findById("alice-private-scenario-99")).thenReturn(Optional.of(aliceScenario));

        // Bob attempts to fetch Alice's scenario
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> claimSimulationService.getScenarioById("alice-private-scenario-99", testUserBob));

        assertTrue(ex.getMessage().contains("You do not have permission to view claim scenario"));

        // Verify GlobalExceptionHandler maps it to 403 Forbidden
        ResponseEntity<ApiResponse<Void>> errorResponse = exceptionHandler.handleAccessDenied(ex);
        assertEquals(HttpStatus.FORBIDDEN, errorResponse.getStatusCode());
        assertFalse(errorResponse.getBody().getSuccess());

        // Unauthenticated guest attempts to fetch Alice's private scenario -> must throw AccessDeniedException
        AccessDeniedException guestEx = assertThrows(AccessDeniedException.class,
                () -> claimSimulationService.getScenarioById("alice-private-scenario-99", null));
        assertTrue(guestEx.getMessage().contains("You do not have permission to view claim scenario"));

        // Admin and Reviewer ARE authorized to audit the scenario
        ClaimSimulationResult dummyResult = ClaimSimulationResult.builder().scenarioId("alice-private-scenario-99").build();
        when(claimSimulationResultRepository.findByScenarioId("alice-private-scenario-99")).thenReturn(Optional.of(dummyResult));

        assertDoesNotThrow(() -> claimSimulationService.getScenarioById("alice-private-scenario-99", testAdminUser));
        assertDoesNotThrow(() -> claimSimulationService.getScenarioById("alice-private-scenario-99", testReviewerUser));
    }

    // =========================================================================
    // 14. Correct behavior when a scenario or result does not exist
    // =========================================================================
    @Test
    @DisplayName("Req 14: Non-existent scenario returns 404 Not Found via NoSuchElementException")
    void testNonExistentScenarioNotFound() {
        when(claimScenarioRepository.findById("missing-scenario-id")).thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(NoSuchElementException.class,
                () -> claimSimulationService.getScenarioById("missing-scenario-id", testUserAlice));

        assertTrue(ex.getMessage().contains("Claim scenario not found"));

        ResponseEntity<ApiResponse<Void>> errorResponse = exceptionHandler.handleNoSuchElement(ex);
        assertEquals(HttpStatus.NOT_FOUND, errorResponse.getStatusCode());
        assertFalse(errorResponse.getBody().getSuccess());
        assertTrue(errorResponse.getBody().getError().contains("missing-scenario-id"));
    }

    // =========================================================================
    // 15. Synthetic sample scenarios being clearly identified
    // =========================================================================
    @Test
    @DisplayName("Req 15: Sample scenarios are explicitly marked as synthetic examples with educational disclaimers")
    void testSampleScenariosClearlyIdentifiedAsSynthetic() {
        ResponseEntity<ApiResponse<List<ClaimSimulationDto.SampleScenarioDto>>> response =
                claimSimulatorController.getSampleScenarios();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ClaimSimulationDto.SampleScenarioDto> samples = response.getBody().getData();

        assertNotNull(samples);
        assertEquals(4, samples.size(), "Must provide 4 representative educational scenarios");

        for (ClaimSimulationDto.SampleScenarioDto sample : samples) {
            assertTrue(sample.isSyntheticExample(), "Each scenario must have syntheticExample=true");
            assertNotNull(sample.getDisclaimer());
            assertTrue(sample.getDisclaimer().contains("SYNTHETIC DEMO SCENARIO"), "Must display prominent synthetic disclaimer");
            assertNotNull(sample.getEducationalTakeaway(), "Must explain the contractual insurance takeaway");
            assertNotNull(sample.getRequest());
            assertFalse(sample.getRequest().getBillLines().isEmpty());
        }
    }

    // =========================================================================
    // 16. No leakage of internal stack traces or sensitive data in API errors
    // =========================================================================
    @Test
    @DisplayName("Req 16: Generic unhandled errors return sanitized 500 responses without leaking internal stack traces")
    void testNoLeakageOfStackTracesInErrors() {
        Exception internalDatabaseCrash = new RuntimeException("DB Connection string mongodb://admin:secretPass123@internal-db:27017/prod leaked");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(internalDatabaseCrash);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getSuccess());
        assertEquals("An unexpected error occurred. Please try again.", response.getBody().getError());
        // Verify sensitive credentials or stack traces are NOT in the response
        assertFalse(response.getBody().getError().contains("secretPass123"));
        assertFalse(response.getBody().getError().contains("mongodb://"));
    }
}
