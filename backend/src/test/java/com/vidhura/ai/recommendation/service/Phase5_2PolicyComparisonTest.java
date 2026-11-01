package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.ClaimScenarioRepository;
import com.vidhura.ai.recommendation.repository.ClaimSimulationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 5.2: Evidence-Backed Policy Comparison Acceptance & Regression Test Suite.
 */
class Phase5_2PolicyComparisonTest {

    private StructuredPolicyRepository policyRepository;
    private PolicyIngestionService policyIngestionService;
    private ExternalEvidenceService externalEvidenceService;
    private RankingEngine rankingEngine;
    private ApprovedPolicyRuleResolver ruleResolver;
    private DeterministicClaimEngine claimEngine;
    private ClaimSimulationService claimSimulationService;
    private User testUser;

    @BeforeEach
    void setUp() {
        policyRepository = Mockito.mock(StructuredPolicyRepository.class);
        policyIngestionService = new PolicyIngestionService(policyRepository);

        // Populate mock repository with real authentic catalog policies
        List<StructuredPolicy> authenticCatalog = policyIngestionService.getAuthenticAcquiredCatalog();
        for (StructuredPolicy p : authenticCatalog) {
            when(policyRepository.findById(p.getId())).thenReturn(Optional.of(p));
            when(policyRepository.findByPolicyCode(p.getPolicyCode())).thenReturn(Optional.of(p));
            if (p.getPolicyAliases() != null) {
                for (String alias : p.getPolicyAliases()) {
                    when(policyRepository.findByPolicyCode(alias)).thenReturn(Optional.of(p));
                }
            }
            if (p.getIrdaUin() != null) {
                when(policyRepository.findByIrdaUin(p.getIrdaUin())).thenReturn(Optional.of(p));
            }
            if (p.getHistoricalUins() != null) {
                for (String hu : p.getHistoricalUins()) {
                    when(policyRepository.findByIrdaUin(hu)).thenReturn(Optional.of(p));
                }
            }
        }

        externalEvidenceService = Mockito.mock(ExternalEvidenceService.class);
        ruleResolver = new ApprovedPolicyRuleResolver(policyRepository, policyIngestionService);
        claimEngine = new DeterministicClaimEngine();
        rankingEngine = new RankingEngine(claimEngine, ruleResolver, externalEvidenceService);

        ClaimScenarioRepository scenarioRepo = Mockito.mock(ClaimScenarioRepository.class);
        ClaimSimulationResultRepository resultRepo = Mockito.mock(ClaimSimulationResultRepository.class);
        when(scenarioRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resultRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        claimSimulationService = new ClaimSimulationService(
                ruleResolver,
                claimEngine,
                scenarioRepo,
                resultRepo,
                policyRepository,
                policyIngestionService,
                externalEvidenceService,
                rankingEngine
        );

        testUser = User.builder().id("usr-test-compare-01").email("consumer@vidhura.ai").role("ROLE_CONSUMER").build();
    }

    @Test
    @DisplayName("5.2.1 Compare two distinct authentic acquired policy contracts with real SHA-256 hashes")
    void testCompareTwoDistinctAuthenticPolicies() {
        String policyAId = "THE_NEW_INDIA_ASSURANCE_COMPAN_V102425"; // New India Mediclaim
        String policyBId = "UNITED_INDIA_INSURANCE_COMPANY_V042122"; // UIIC Health 2068

        ClaimSimulationDto.ClaimSimulationRequest scenario = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId(policyAId)
                .patientAge(42)
                .diagnosis("Acute Appendicitis (Laparoscopic Appendectomy)")
                .hospitalType("NETWORK_CASHLESS")
                .hospitalRoomCategoryOpted("SINGLE_PRIVATE_AC")
                .selectedSumInsured(BigDecimal.valueOf(500000))
                .policyContinuityMonths(36)
                .admissionDate(LocalDate.now().minusDays(3))
                .dischargeDate(LocalDate.now())
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Single Private Room (3 days @ ₹6,500)")
                                .unitPrice(BigDecimal.valueOf(6500))
                                .quantity(3)
                                .totalAmount(BigDecimal.valueOf(19500))
                                .build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                                .description("Surgeon & Anesthesia Professional Charges")
                                .unitPrice(BigDecimal.valueOf(45000))
                                .quantity(1)
                                .totalAmount(BigDecimal.valueOf(45000))
                                .build(),
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .category(ClaimSimulationDto.BillItemCategory.OT_CHARGES)
                                .description("Operation Theater & Laparoscopy Consumables")
                                .unitPrice(BigDecimal.valueOf(25000))
                                .quantity(1)
                                .totalAmount(BigDecimal.valueOf(25000))
                                .build()
                ))
                .build();

        ClaimSimulationDto.ClaimComparisonRequest req = ClaimSimulationDto.ClaimComparisonRequest.builder()
                .policyIds(List.of(policyAId, policyBId))
                .scenario(scenario)
                .build();

        ClaimSimulationDto.ClaimComparisonResponseDto response = claimSimulationService.compareClaimAcrossPolicies(req, testUser);

        assertNotNull(response);
        assertEquals(2, response.getComparisons().size());
        assertEquals(BigDecimal.valueOf(89500), response.getTotalSubmittedBill());

        ClaimSimulationDto.PolicyClaimComparisonItemDto compA = response.getComparisons().get(0);
        ClaimSimulationDto.PolicyClaimComparisonItemDto compB = response.getComparisons().get(1);

        // Verify UIN and physical SHA-256 integrity
        assertEquals("NIAHLIP21277V042021", compA.getIrdaUin());
        assertTrue(compA.getSourceDocumentHash().contains("bfc304901063fa21b1e145c22d2ee15bd5f8190e82b4caca069cfe96dbcedc9f"));
        assertTrue(compA.isAuthenticAcquiredDocument());
        assertFalse(compA.isSyntheticExample());

        assertEquals("UIIHLIP22070V042122", compB.getIrdaUin());
        assertTrue(compB.getSourceDocumentHash().contains("6bb20e21fa1c97b514f57ae6764b2e9b7a22df71d8afb2ece0539e0e17eea267"));
        assertTrue(compB.isAuthenticAcquiredDocument());
        assertFalse(compB.isSyntheticExample());

        // Verify mathematical reconciliation
        assertTrue(compA.isReconciled());
        assertTrue(compB.isReconciled());
        assertEquals(compA.getTotalSubmittedBill(), compA.getEstimatedInsurerPayment().add(compA.getEstimatedPatientLiability()));
        assertEquals(compB.getTotalSubmittedBill(), compB.getEstimatedInsurerPayment().add(compB.getEstimatedPatientLiability()));

        // Verify Contractual Comparison Matrix
        assertNotNull(response.getContractualMatrix());
        assertTrue(response.getContractualMatrix().size() >= 12, "Should contain comprehensive contractual features");

        // Verify exact clause citations in matrix
        ClaimSimulationDto.ContractualFeatureComparisonDto roomRentFeature = response.getContractualMatrix().stream()
                .filter(f -> "ROOM_RENT_ICU".equals(f.getFeatureKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(roomRentFeature);
        assertEquals(2, roomRentFeature.getPolicyValues().size());
        assertNotNull(roomRentFeature.getPolicyValues().get(0).getClauseCitation());
        assertNotNull(roomRentFeature.getPolicyValues().get(0).getVerbatimText());

        // Verify Personalized Analysis
        assertNotNull(response.getPersonalizedAnalysis());
        assertFalse(response.getPersonalizedAnalysis().getPolicyAAdvantages().isEmpty());
        assertFalse(response.getPersonalizedAnalysis().getPolicyBAdvantages().isEmpty());
    }

    @Test
    @DisplayName("5.2.2 Verify external evidence status enforcement: NIA/UIIC verified vs private unverified")
    void testExternalEvidenceVerificationEnforcement() {
        // Setup mock statutory evidence ONLY for NEW_INDIA_ASSURANCE
        when(externalEvidenceService.getInsurerPerformanceMetrics("NEW_INDIA_ASSURANCE", null))
                .thenReturn(com.vidhura.ai.recommendation.dto.ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                        .insurerCode("NEW_INDIA_ASSURANCE")
                        .reportingPeriod("FY 2022-23")
                        .settlementRatioLessThan3Months("98.5%")
                        .incurredClaimRatio("92.1%")
                        .solvencyRatio("1.87x")
                        .repudiationRatio("1.2%")
                        .verifiedStatutory(true)
                        .statutorySourceTitle("IRDAI Annual Report 2022-23 (Table 4.5)")
                        .build());

        // Care Health has NO verified statutory record
        when(externalEvidenceService.getInsurerPerformanceMetrics("CARE_HEALTH", null)).thenReturn(null);

        String policyNia = "THE_NEW_INDIA_ASSURANCE_COMPAN_V102425";
        String policyCare = "CARE_HEALTH_CARE_ULTIMATE"; // Care Ultimate

        ClaimSimulationDto.ClaimSimulationRequest scenario = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId(policyNia)
                .patientAge(35)
                .diagnosis("Dengue Fever")
                .hospitalType("NETWORK_CASHLESS")
                .hospitalRoomCategoryOpted("SINGLE_PRIVATE_AC")
                .selectedSumInsured(BigDecimal.valueOf(500000))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Room charges")
                                .unitPrice(BigDecimal.valueOf(5000))
                                .quantity(2)
                                .totalAmount(BigDecimal.valueOf(10000))
                                .build()
                ))
                .build();

        ClaimSimulationDto.ClaimComparisonRequest req = ClaimSimulationDto.ClaimComparisonRequest.builder()
                .policyIds(List.of(policyNia, policyCare))
                .scenario(scenario)
                .build();

        ClaimSimulationDto.ClaimComparisonResponseDto response = claimSimulationService.compareClaimAcrossPolicies(req, testUser);

        assertNotNull(response.getInsurerIntelligences());
        assertEquals(2, response.getInsurerIntelligences().size());

        var niaIntel = response.getInsurerIntelligences().get(0);
        var careIntel = response.getInsurerIntelligences().get(1);

        // New India is strictly VERIFIED_STATUTORY
        assertEquals("VERIFIED_STATUTORY", niaIntel.getVerificationStatus());
        assertTrue(niaIntel.isVerifiedStatutory());
        assertFalse(niaIntel.isEvidenceUnavailable());
        assertEquals("98.5%", niaIntel.getClaimSettlementRatio());
        assertEquals("1.87x", niaIntel.getSolvencyRatio());

        // Care Health is strictly EVIDENCE_UNAVAILABLE (no fake statutory approval)
        assertEquals("EVIDENCE_UNAVAILABLE", careIntel.getVerificationStatus());
        assertFalse(careIntel.isVerifiedStatutory());
        assertTrue(careIntel.isEvidenceUnavailable());
        assertEquals("Evidence unavailable", careIntel.getSolvencyRatio(), "Must NOT invent fake 1.78x solvency");
        assertEquals("Evidence unavailable", careIntel.getRepudiationRatio(), "Must NOT invent fake repudiation");
    }

    @Test
    @DisplayName("5.2.3 Handle omitted or invalid policies with clear non-rejection explanations")
    void testOmittedPolicyHandling() {
        String validPolicy = "THE_NEW_INDIA_ASSURANCE_COMPAN_V102425";
        String missingPolicy = "UNKNOWN_NONEXISTENT_UIN_V99";

        ClaimSimulationDto.ClaimSimulationRequest scenario = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId(validPolicy)
                .patientAge(35)
                .diagnosis("Fever")
                .hospitalType("NETWORK_CASHLESS")
                .hospitalRoomCategoryOpted("SINGLE_PRIVATE_AC")
                .selectedSumInsured(BigDecimal.valueOf(500000))
                .billLines(List.of(
                        ClaimSimulationDto.BillLineItemDto.builder()
                                .category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                                .description("Room charges")
                                .unitPrice(BigDecimal.valueOf(5000))
                                .quantity(1)
                                .totalAmount(BigDecimal.valueOf(5000))
                                .build()
                ))
                .build();

        ClaimSimulationDto.ClaimComparisonRequest req = ClaimSimulationDto.ClaimComparisonRequest.builder()
                .policyIds(List.of(validPolicy, missingPolicy))
                .scenario(scenario)
                .build();

        ClaimSimulationDto.ClaimComparisonResponseDto response = claimSimulationService.compareClaimAcrossPolicies(req, testUser);

        assertNotNull(response);
        assertEquals(1, response.getComparisons().size());
        assertFalse(response.getOmittedPolicies().isEmpty());

        var omitted = response.getOmittedPolicies().get(0);
        assertEquals(missingPolicy, omitted.getPolicyIdOrCode());
        assertTrue(omitted.getOmissionReason().contains("Incomplete contractual data") || omitted.getOmissionReason().contains("not found"));
        assertFalse(omitted.getOmissionReason().contains("rejected applicant"), "Must NOT imply insurer rejected user");
    }
}
