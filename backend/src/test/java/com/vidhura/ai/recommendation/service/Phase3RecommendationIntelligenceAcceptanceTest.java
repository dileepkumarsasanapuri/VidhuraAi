package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Phase 3: Recommendation Intelligence & Provenance Hardening Acceptance Test.
 *
 * Verifies:
 * 1. Actuarial Benchmark Line-by-Line Reconciliation (₹2.20L bill = ₹1.125L insurer + ₹1.075L patient liability).
 * 2. Provenance Gate Hardening (Strict isolation of synthetic demo fixtures from production winner rankings).
 * 3. Hard Eligibility Filters (Strict budget, senior copay tolerance, critical exclusions).
 * 4. Personalized Claim Simulation blended directly into recommendation scoring and cards.
 */
public class Phase3RecommendationIntelligenceAcceptanceTest {

    private StructuredPolicyRepository policyRepository;
    private RecommendationProfileRepository profileRepository;
    private RecommendationResultRepository resultRepository;

    private DeterministicClaimEngine claimEngine;
    private ApprovedPolicyRuleResolver ruleResolver;
    private EligibilityEngine eligibilityEngine;
    private RankingEngine rankingEngine;
    private PolicyIngestionService ingestionService;
    private RecommendationService recommendationService;

    private final Map<String, StructuredPolicy> simulatedDb = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        simulatedDb.clear();

        policyRepository = Mockito.mock(StructuredPolicyRepository.class);
        profileRepository = Mockito.mock(RecommendationProfileRepository.class);
        resultRepository = Mockito.mock(RecommendationResultRepository.class);

        claimEngine = new DeterministicClaimEngine();
        ruleResolver = new ApprovedPolicyRuleResolver(policyRepository);
        eligibilityEngine = new EligibilityEngine();
        rankingEngine = new RankingEngine(claimEngine, ruleResolver);
        ingestionService = new PolicyIngestionService(policyRepository);

        recommendationService = new RecommendationService(
                policyRepository, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, ingestionService, null);

        // Seed 3 authentic acquired policies + 7 synthetic demo fixtures into mock database
        List<StructuredPolicy> allPolicies = ingestionService.getAllCatalogPolicies();
        for (StructuredPolicy p : allPolicies) {
            if (p.getId() == null) {
                p.setId("pol-" + UUID.randomUUID().toString().substring(0, 8));
            }
            simulatedDb.put(p.getId(), p);
        }

        when(policyRepository.findAll()).thenAnswer(inv -> new ArrayList<>(simulatedDb.values()));
        when(policyRepository.findByActiveTrue()).thenAnswer(inv ->
                simulatedDb.values().stream().filter(StructuredPolicy::isActive).toList());
        when(policyRepository.count()).thenAnswer(inv -> (long) simulatedDb.size());
        when(policyRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(simulatedDb.get(inv.getArgument(0))));
        when(policyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return simulatedDb.values().stream().filter(p -> code.equals(p.getPolicyCode())).findFirst();
        });
        when(policyRepository.findByIrdaUin(anyString())).thenAnswer(inv -> {
            String uin = inv.getArgument(0);
            return simulatedDb.values().stream().filter(p -> uin != null && uin.equalsIgnoreCase(p.getIrdaUin())).findFirst();
        });
        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId("pol-" + UUID.randomUUID().toString().substring(0, 8));
            simulatedDb.put(p.getId(), p);
            return p;
        });

        recommendationService.refreshCatalog();
    }

    // =========================================================================
    // 1. ACTUARIAL BENCHMARK LINE-BY-LINE RECONCILIATION
    // =========================================================================

    @Test
    @DisplayName("Actuarial Benchmark Audit: ₹2,20,000 Bill reconciles line-by-line explaining the ₹10,000 consumables line item")
    void testActuarialBenchmarkAuditLineByLine() {
        // Construct standard 1% room rent capped policy rule set (₹5,00,000 Sum Insured)
        ApprovedPolicyRuleResolver.ResolvedPolicyRules rules = ApprovedPolicyRuleResolver.ResolvedPolicyRules.builder()
                .policyCode("TEST_BENCHMARK_CAPPED")
                .policyName("Actuarial Benchmark Policy")
                .provider("Benchmark Assurance Ltd.")
                .irdaUin("BENCHHLIP12345V012024")
                .sourceDocumentHash("d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3")
                .roomRentCapped(true)
                .roomRentCapPerDay(BigDecimal.valueOf(5000.00)) // 1% of ₹5L
                .proportionateDeductionApplies(true)
                .copayPercentage(BigDecimal.valueOf(10.0)) // 10% co-payment
                .deductibleAmount(BigDecimal.valueOf(5000.00)) // ₹5,000 deductible
                .build();

        // 6-line realistic hospitalization bill totaling ₹2,20,000
        List<ClaimSimulationDto.BillLineItemDto> billLines = List.of(
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-1")
                        .category(ClaimSimulationDto.BillItemCategory.ROOM_RENT)
                        .description("Deluxe Room (4 days @ ₹10,000/day)")
                        .unitPrice(BigDecimal.valueOf(10000.00))
                        .quantity(4)
                        .totalAmount(BigDecimal.valueOf(40000.00))
                        .build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-2")
                        .category(ClaimSimulationDto.BillItemCategory.SURGEON_FEES)
                        .description("Chief Surgeon & Anesthesia Fees")
                        .unitPrice(BigDecimal.valueOf(80000.00))
                        .quantity(1)
                        .totalAmount(BigDecimal.valueOf(80000.00))
                        .build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-3")
                        .category(ClaimSimulationDto.BillItemCategory.OT_CHARGES)
                        .description("Operation Theatre Charges")
                        .unitPrice(BigDecimal.valueOf(40000.00))
                        .quantity(1)
                        .totalAmount(BigDecimal.valueOf(40000.00))
                        .build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-4")
                        .category(ClaimSimulationDto.BillItemCategory.MEDICINES)
                        .description("Injectable Antibiotics & Pharmacy")
                        .unitPrice(BigDecimal.valueOf(30000.00))
                        .quantity(1)
                        .totalAmount(BigDecimal.valueOf(30000.00))
                        .build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-5")
                        .category(ClaimSimulationDto.BillItemCategory.DIAGNOSTICS)
                        .description("CT Scan & Pathology Labs")
                        .unitPrice(BigDecimal.valueOf(20000.00))
                        .quantity(1)
                        .totalAmount(BigDecimal.valueOf(20000.00))
                        .build(),
                ClaimSimulationDto.BillLineItemDto.builder()
                        .lineId("LINE-6")
                        .category(ClaimSimulationDto.BillItemCategory.CONSUMABLES)
                        .description("Non-Medical Hospital Consumables (IRDAI List 1)")
                        .unitPrice(BigDecimal.valueOf(10000.00))
                        .quantity(1)
                        .totalAmount(BigDecimal.valueOf(10000.00))
                        .build()
        );

        ClaimSimulationDto.ClaimSimulationRequest req = ClaimSimulationDto.ClaimSimulationRequest.builder()
                .policyId("TEST_BENCHMARK_CAPPED")
                .patientAge(45)
                .diagnosis("Complex Laparoscopic Surgery")
                .hospitalRoomCategoryOpted("DELUXE_SUITE")
                .selectedSumInsured(BigDecimal.valueOf(500000.00))
                .billLines(billLines)
                .build();

        ClaimSimulationDto.ClaimSimulationResponse resp = claimEngine.calculateClaim(req, rules);

        // Mathematical identity guarantee
        assertEquals(0, BigDecimal.valueOf(220000.00).compareTo(resp.getTotalSubmittedBill()));
        assertEquals(0, BigDecimal.valueOf(112500.00).compareTo(resp.getEstimatedInsurerPayment()));
        assertEquals(0, BigDecimal.valueOf(107500.00).compareTo(resp.getEstimatedPatientLiability()));
        assertTrue(resp.isReconciled());

        // Line-by-line deductions audit
        Map<String, BigDecimal> deductionsByType = new HashMap<>();
        for (ClaimSimulationDto.DeductionItemDto d : resp.getDeductionBreakdown()) {
            deductionsByType.merge(d.getDeductionType(), d.getAmountDeducted(), BigDecimal::add);
        }

        // 1. Non-medical consumables (IRDAI List 1 excluded upfront) = ₹10,000
        assertEquals(0, BigDecimal.valueOf(10000.00).compareTo(deductionsByType.get("NON_MEDICAL_CONSUMABLES")),
                "Line 6 consumables must account for the ₹10,000 excluded upfront");

        // 2. Room rent excess = ₹20,000 (₹40,000 billed - ₹20,000 allowed at ₹5k/day)
        assertEquals(0, BigDecimal.valueOf(20000.00).compareTo(deductionsByType.get("ROOM_RENT_EXCESS")),
                "Room rent excess must be ₹20,000");

        // 3. Proportionate haircut = ₹60,000 (50% on ₹80k surgeon + 50% on ₹40k OT)
        assertEquals(0, BigDecimal.valueOf(60000.00).compareTo(deductionsByType.get("PROPORTIONATE_DEDUCTION")),
                "Proportionate haircut across surgeon and OT must be ₹60,000");

        // 4. Policy deductible = ₹5,000
        assertEquals(0, BigDecimal.valueOf(5000.00).compareTo(deductionsByType.get("DEDUCTIBLE")),
                "Policy deductible must be ₹5,000");

        // 5. Co-payment = ₹12,500 (10% of ₹1,25,000 admissible base)
        assertEquals(0, BigDecimal.valueOf(12500.00).compareTo(deductionsByType.get("COPAYMENT")),
                "10% co-payment on ₹1,25,000 admissible base must be ₹12,500");

        // Total patient liability sum = 10,000 + 20,000 + 60,000 + 5,000 + 12,500 = ₹1,07,500.00
        BigDecimal expectedTotalLiability = BigDecimal.valueOf(10000.00)
                .add(BigDecimal.valueOf(20000.00))
                .add(BigDecimal.valueOf(60000.00))
                .add(BigDecimal.valueOf(5000.00))
                .add(BigDecimal.valueOf(12500.00));
        assertEquals(0, expectedTotalLiability.compareTo(resp.getEstimatedPatientLiability()));
        assertEquals(0, resp.getTotalSubmittedBill().compareTo(resp.getEstimatedInsurerPayment().add(resp.getEstimatedPatientLiability())));
    }

    // =========================================================================
    // 2. PROVENANCE GATE HARDENING & SYNTHETIC FIXTURE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Provenance Gate: Synthetic demonstration fixtures are strictly disqualified when authentic acquired policies exist")
    void testProvenanceGateSyntheticIsolation() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(35)
                .annualBudget(30000.0)
                .budgetType("PREFERRED")
                .cityOrPincode("Mumbai")
                .insuranceType("INDIVIDUAL")
                .primaryPriority("BALANCED")
                .includeDemoFixtures(false) // Default production setting
                .build();

        RecommendationResponseDto resp = recommendationService.generateRecommendations("user-p3-1", req);
        assertFalse(resp.getRankedPolicies().isEmpty(), "Authentic policies must produce recommendations");

        // All recommended policies MUST be authentic acquired documents
        for (RecommendationResponseDto.RankedPolicyCard card : resp.getRankedPolicies()) {
            assertTrue(card.isAuthenticAcquiredDocument(),
                    "Policy " + card.getPolicyName() + " must be flagged as authentic acquired document");
            assertFalse(card.isSyntheticFixture(),
                    "Synthetic fixture " + card.getPolicyName() + " must not appear in production winners");
            assertNotNull(card.getDocumentChecksum(), "Must possess valid cryptographic document checksum");
            assertEquals(64, card.getDocumentChecksum().length(), "Document checksum must be full 64-char SHA-256");
            assertEquals("REVIEWER_APPROVED", card.getReviewerApprovalStatus(), "Must have reviewer approval sign-off");
            assertNotNull(card.getReviewerName(), "Must possess verified reviewer identity");
        }

        // When includeDemoFixtures is explicitly requested, synthetic fixtures are allowed but score-capped
        RecommendationRequestDto demoReq = RecommendationRequestDto.builder()
                .primaryAge(35)
                .annualBudget(30000.0)
                .budgetType("PREFERRED")
                .cityOrPincode("Mumbai")
                .insuranceType("INDIVIDUAL")
                .primaryPriority("BALANCED")
                .includeDemoFixtures(true) // Explicitly allow demo fixtures
                .build();

        RecommendationResponseDto demoResp = recommendationService.generateRecommendations("user-p3-demo", demoReq);
        boolean containsSynthetic = demoResp.getRankedPolicies().stream().anyMatch(RecommendationResponseDto.RankedPolicyCard::isSyntheticFixture);
        assertTrue(containsSynthetic, "Synthetic demo fixtures should be present when explicitly requested");

        for (RecommendationResponseDto.RankedPolicyCard card : demoResp.getRankedPolicies()) {
            if (card.isSyntheticFixture()) {
                assertTrue(card.getOverallMatchScore() <= 88.0, "Synthetic fixture match score must be capped at 88.0");
                assertTrue(card.getMatchBadges().contains("Synthetic Demo Fixture"));
            }
        }
    }

    // =========================================================================
    // 3. HARD ELIGIBILITY FILTERS
    // =========================================================================

    @Test
    @DisplayName("Hard Eligibility Filter: Strict budget cap disqualifies policies exceeding user budget")
    void testStrictBudgetHardFilter() {
        // User with strict budget of ₹6,000/yr (only lower-priced tier should be eligible)
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(40)
                .annualBudget(6000.0)
                .budgetType("STRICT") // Strict hard cap
                .cityOrPincode("Delhi")
                .insuranceType("INDIVIDUAL")
                .primaryPriority("AFFORDABLE_PREMIUMS")
                .includeDemoFixtures(true)
                .build();

        RecommendationResponseDto resp = recommendationService.generateRecommendations("user-p3-budget", req);

        for (RecommendationResponseDto.RankedPolicyCard card : resp.getRankedPolicies()) {
            assertTrue(card.getCalculatedAnnualPremium() <= 6000.0,
                    "Policy " + card.getPolicyName() + " premium ₹" + card.getCalculatedAnnualPremium() + " exceeds strict budget of ₹6,000");
        }
    }

    @Test
    @DisplayName("Hard Eligibility Filter: Senior Citizen with zero copay tolerance disqualifies high mandatory copay plans")
    void testSeniorCitizenCopayHardFilter() {
        // Senior citizen (age 68) with ZERO copay tolerance
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(68)
                .annualBudget(50000.0)
                .budgetType("PREFERRED")
                .cityOrPincode("Bengaluru")
                .insuranceType("INDIVIDUAL")
                .copayTolerance("NONE") // Strict zero co-pay requirement
                .includeDemoFixtures(true)
                .build();

        RecommendationResponseDto resp = recommendationService.generateRecommendations("user-p3-senior", req);

        // Star Senior Citizens Red Carpet requires 20% mandatory co-payment and MUST be disqualified
        boolean containsSeniorRedCarpet = resp.getRankedPolicies().stream()
                .anyMatch(c -> c.getPolicyCode().contains("STAR_SENIOR_RED_CARPET"));
        assertFalse(containsSeniorRedCarpet,
                "Star Senior Red Carpet (20% mandatory copay) must be disqualified for senior desiring zero co-payment");
    }

    // =========================================================================
    // 4. PERSONALIZED CLAIM SIMULATION IN RECOMMENDATIONS
    // =========================================================================

    @Test
    @DisplayName("Phase 3 Claim Simulation: Recommended policy cards include grounded deterministic claim simulation comparing room rent outcomes")
    void testPersonalizedClaimSimulationInRecommendationCards() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(35)
                .annualBudget(25000.0)
                .budgetType("PREFERRED")
                .cityOrPincode("Mumbai")
                .insuranceType("INDIVIDUAL")
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .strictRoomPreference("DELUXE_SUITE") // Opting for Deluxe room to test room-rent penalty contrast
                .includeDemoFixtures(false)
                .build();

        RecommendationResponseDto resp = recommendationService.generateRecommendations("user-p3-sim", req);
        assertFalse(resp.getRankedPolicies().isEmpty());

        RecommendationResponseDto.RankedPolicyCard topCard = resp.getRankedPolicies().get(0);
        assertNotNull(topCard.getPersonalizedClaimSimulation(), "Top card must contain personalized claim simulation");

        RecommendationResult.PersonalizedClaimSimulationSummary sim = topCard.getPersonalizedClaimSimulation();
        assertTrue(sim.getSubmittedBill() > 0, "Submitted bill must be non-zero");
        assertTrue(sim.getInsurerPayment() > 0, "Insurer payment must be non-zero");
        assertTrue(sim.isReconciled(), "Simulation must be mathematically reconciled");
        assertNotNull(sim.getRoomRentImpactText(), "Room rent impact explanation must be provided");

        // Inspect comparison: New India Mediclaim has Single Private AC coverage with no proportionate deduction
        // whereas UIIC Family Medicare has 1% capping with proportionate deductions
        Optional<RecommendationResponseDto.RankedPolicyCard> uiicCard = resp.getRankedPolicies().stream()
                .filter(c -> c.getPolicyCode().contains("UNITED_INDIA"))
                .findFirst();

        Optional<RecommendationResponseDto.RankedPolicyCard> niaCard = resp.getRankedPolicies().stream()
                .filter(c -> c.getPolicyCode().contains("NEW_INDIA_MEDICLAIM"))
                .findFirst();

        if (uiicCard.isPresent() && niaCard.isPresent()) {
            RecommendationResult.PersonalizedClaimSimulationSummary uiicSim = uiicCard.get().getPersonalizedClaimSimulation();
            RecommendationResult.PersonalizedClaimSimulationSummary niaSim = niaCard.get().getPersonalizedClaimSimulation();

            assertNotNull(uiicSim);
            assertNotNull(niaSim);

            // UIIC (with 1% room cap) on Deluxe room will have higher patient liability due to room excess/haircut
            assertTrue(uiicSim.getPatientLiability() > niaSim.getPatientLiability(),
                    "Under Deluxe Suite stay, UIIC (1% cap) should have higher patient liability than New India Mediclaim");
        }
    }
}
