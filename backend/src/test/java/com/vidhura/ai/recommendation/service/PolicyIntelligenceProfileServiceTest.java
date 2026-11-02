package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.dto.PolicyIntelligenceProfileDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyIntelligenceProfileServiceTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private ApprovedPolicyRuleResolver ruleResolver;

    @Mock
    private DeterministicClaimEngine claimEngine;

    @Mock
    private ExternalEvidenceService externalEvidenceService;

    private PolicyIntelligenceProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new PolicyIntelligenceProfileService(
                policyRepository,
                ruleResolver,
                claimEngine,
                externalEvidenceService
        );
    }

    @Test
    @DisplayName("Should aggregate complete 360-degree profile for a policy")
    void testGetPolicyIntelligenceProfileComplete() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .id("pol-101")
                .policyCode("UIIC_FAMILY_MEDICARE")
                .policyName("UIIC Family Medicare 2024")
                .provider("United India Insurance Co. Ltd.")
                .irdaUin("UIIHLIP21234V022324")
                .uinRevision("V022324")
                .planName("Gold Plan")
                .category("Family Floater")
                .description("Standard retail health plan with 1% room rent cap.")
                .syntheticFixture(false)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .officialDocumentUrl("https://uiic.co.in/family-medicare.pdf")
                .policyWordingDocument("UIIC_Family_Medicare_Wordings.pdf")
                .regulatoryFilingSource("IRDAI Product Filing")
                .lastAuditedDate("2024-Q1")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(true)
                        .proportionateDeductionApplies(true)
                        .dayCareProceduresCount(540)
                        .restorationBenefit("100% restoration")
                        .noClaimBonus("50% NCB")
                        .build())
                .build();

        ApprovedPolicyRuleResolver.ResolvedPolicyRules rules = ApprovedPolicyRuleResolver.ResolvedPolicyRules.builder()
                .policyId("pol-101")
                .policyCode("UIIC_FAMILY_MEDICARE")
                .policyName("UIIC Family Medicare 2024")
                .roomRentCapped(true)
                .roomRentCapPerDay(new BigDecimal("5000.00"))
                .proportionateDeductionApplies(true)
                .copayPercentage(BigDecimal.ZERO)
                .deductibleAmount(BigDecimal.ZERO)
                .waitingPeriodInitialDays(30)
                .waitingPeriodPedMonths(48)
                .waitingPeriodSpecificMonths(24)
                .build();

        ExternalEvidenceDto.InsurerPerformanceSummaryDto insurerPerf = ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                .insurerCode("UNITED_INDIA")
                .insurerName("United India Insurance Co. Ltd.")
                .reportingPeriod("FY2023-24")
                .statutorySourceTitle("IRDAI Annual Report 2023-24")
                .statutoryDocumentHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .incurredClaimRatio("91.50%")
                .incurredClaimRatioValue(new BigDecimal("91.50"))
                .annualSettlementRatio("92.80%")
                .annualSettlementRatioValue(new BigDecimal("92.80"))
                .solvencyRatio("1.62")
                .overallPerformanceRating("VERY_GOOD")
                .verificationStatus("VERIFIED_STATUTORY")
                .verifiedStatutory(true)
                .build();

        ExternalEvidenceDto.HospitalNetworkSummaryDto hospitalNet = ExternalEvidenceDto.HospitalNetworkSummaryDto.builder()
                .insurerCode("UNITED_INDIA")
                .city("Mumbai")
                .totalNetworkHospitalsInCity(14)
                .preferredTier1Count(5)
                .secondaryCount(9)
                .cashlessAccessibilityScore(95.0)
                .verificationBadge("VERIFIED_HOSPITAL_REGISTRY")
                .build();

        ExternalEvidenceDto.QuoteDetailDto quote = ExternalEvidenceDto.QuoteDetailDto.builder()
                .quoteId("q-test-1")
                .policyCode("UIIC_FAMILY_MEDICARE")
                .quotedAnnualPremium(new BigDecimal("18500.00"))
                .quotedBasePremium(new BigDecimal("15677.97"))
                .quotedGst(new BigDecimal("2822.03"))
                .quotingChannel("OFFICIAL_PORTAL")
                .quoteReferenceNumber("UIIC-2024-998811")
                .isAuthentic(true)
                .verificationStatus("VERIFIED_AUTHENTIC_QUOTE")
                .isStale(false)
                .formattedAnnualPremium("₹18,500.00")
                .build();

        ClaimSimulationDto.ClaimSimulationResponse simResp = ClaimSimulationDto.ClaimSimulationResponse.builder()
                .simulationId("sim-bench-1")
                .estimatedInsurerPayment(new BigDecimal("90000.00"))
                .estimatedPatientLiability(new BigDecimal("33000.00"))
                .reconciled(true)
                .deductionBreakdown(List.of(
                        ClaimSimulationDto.DeductionItemDto.builder()
                                .deductionType("ROOM_RENT_EXCESS")
                                .amountDeducted(new BigDecimal("3000.00"))
                                .sectionReference("Clause 2.1")
                                .explanation("1% Room Rent Cap")
                                .build(),
                        ClaimSimulationDto.DeductionItemDto.builder()
                                .deductionType("PROPORTIONATE_DEDUCTION")
                                .amountDeducted(new BigDecimal("18000.00"))
                                .sectionReference("Clause 2.1.b")
                                .explanation("Proportionate Deduction")
                                .build(),
                        ClaimSimulationDto.DeductionItemDto.builder()
                                .deductionType("NON_MEDICAL_CONSUMABLES")
                                .amountDeducted(new BigDecimal("12000.00"))
                                .sectionReference("IRDAI Guidelines")
                                .explanation("List 1 Excluded Items")
                                .build()
                ))
                .build();

        when(policyRepository.findByPolicyCode("UIIC_FAMILY_MEDICARE")).thenReturn(Optional.of(policy));
        when(ruleResolver.resolveRules(eq(policy), eq("V022324"), eq(new BigDecimal("1000000.00")))).thenReturn(rules);
        when(externalEvidenceService.getInsurerPerformanceMetrics(eq("UNITED_INDIA"), any())).thenReturn(insurerPerf);
        when(externalEvidenceService.getHospitalNetworkSummary(eq("UNITED_INDIA"), eq("Mumbai"), isNull())).thenReturn(hospitalNet);
        when(externalEvidenceService.getAuthenticOrModelledQuote(eq("UIIC_FAMILY_MEDICARE"), eq(35), eq(new BigDecimal("1000000.00")), isNull(), eq("INDIVIDUAL"))).thenReturn(quote);
        when(claimEngine.calculateClaim(any(), eq(rules))).thenReturn(simResp);

        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                "UIIC_FAMILY_MEDICARE", 35, new BigDecimal("1000000.00"), "Mumbai", null, "INDIVIDUAL");

        assertNotNull(profile);

        // 1. Contract Overview
        assertEquals("UIIC_FAMILY_MEDICARE", profile.getContractOverview().getPolicyCode());
        assertTrue(profile.getContractOverview().isRoomRentCapped());
        assertEquals(new BigDecimal("5000.00"), profile.getContractOverview().getRoomRentCapPerDay());

        // 2. Insurer Performance
        assertTrue(profile.getInsurerPerformance().isVerifiedStatutory());
        assertEquals("91.50%", profile.getInsurerPerformance().getIncurredClaimRatio());
        assertEquals("92.80%", profile.getInsurerPerformance().getAnnualSettlementRatio());

        // 3. Hospital Accessibility
        assertEquals(14, profile.getHospitalAccessibility().getTotalNetworkHospitalsInCity());
        assertEquals(95.0, profile.getHospitalAccessibility().getCashlessAccessibilityScore());

        // 4. Premium Quotation
        assertTrue(profile.getPremiumQuotation().isAuthentic());
        assertEquals(new BigDecimal("18500.00"), profile.getPremiumQuotation().getQuotedAnnualPremium());
        assertEquals(new BigDecimal("2822.03"), profile.getPremiumQuotation().getQuotedGst());

        // 5. Benchmark Claim Simulation
        assertEquals(new BigDecimal("123000.00"), profile.getBenchmarkClaimSimulation().getTotalSubmittedBill());
        assertEquals(new BigDecimal("90000.00"), profile.getBenchmarkClaimSimulation().getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("33000.00"), profile.getBenchmarkClaimSimulation().getEstimatedPatientLiability());
        assertTrue(profile.getBenchmarkClaimSimulation().isReconciled());
        assertEquals(new BigDecimal("3000.00"), profile.getBenchmarkClaimSimulation().getRoomRentExcessDeduction());
        assertEquals(new BigDecimal("18000.00"), profile.getBenchmarkClaimSimulation().getProportionateDeduction());
        assertEquals(new BigDecimal("12000.00"), profile.getBenchmarkClaimSimulation().getNonMedicalConsumablesDeduction());

        // 6. Evidence Quality Scorecard
        assertEquals("VERIFIED_OFFICIAL_CATALOG", profile.getEvidenceQualityScorecard().getContractualGroundTruthStatus());
        assertEquals("VERIFIED_STATUTORY", profile.getEvidenceQualityScorecard().getExternalStatutoryEvidenceStatus());
        assertEquals("VERIFIED_AUTHENTIC_QUOTE", profile.getEvidenceQualityScorecard().getQuoteVerificationStatus());
        assertEquals("VERIFIED_HOSPITAL_REGISTRY", profile.getEvidenceQualityScorecard().getHospitalRegistryStatus());
        assertEquals(100.0, profile.getEvidenceQualityScorecard().getOverallQualityScore());
        assertNotNull(profile.getEvidenceQualityScorecard().getDisclaimer());

        // 7. Decoupled Triad Validation
        assertNotNull(profile.getPolicySuitability());
        assertTrue(profile.getPolicySuitability().isIndependentOfExternalEvidence());
        assertTrue(profile.getPolicySuitability().getSuitabilityScore() > 0);

        assertNotNull(profile.getEvidenceCompleteness());
        assertEquals(100.0, profile.getEvidenceCompleteness().getCompletenessScore());
        assertEquals("COMPLETE", profile.getEvidenceCompleteness().getCompletenessStatus());
        assertEquals(4, profile.getEvidenceCompleteness().getVerifiedCount());
        assertTrue(profile.getEvidenceCompleteness().isContractualTermsVerified());
        assertTrue(profile.getEvidenceCompleteness().isStatutoryPerformanceVerified());
        assertTrue(profile.getEvidenceCompleteness().isAuthenticQuoteVerified());
        assertTrue(profile.getEvidenceCompleteness().isHospitalNetworkVerified());
    }

    @Test
    @DisplayName("Should display missing external evidence and unverified statuses explicitly")
    void testGetPolicyIntelligenceProfileWithMissingExternalEvidence() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .id("pol-demo")
                .policyCode("DEMO_POLICY")
                .policyName("Demo Health Guard")
                .provider("Demo Insurance Ltd.")
                .syntheticFixture(true)
                .verificationStatus("UNVERIFIED")
                .build();

        ApprovedPolicyRuleResolver.ResolvedPolicyRules rules = ApprovedPolicyRuleResolver.ResolvedPolicyRules.builder()
                .policyId("pol-demo")
                .policyCode("DEMO_POLICY")
                .copayPercentage(BigDecimal.ZERO)
                .build();

        // Insurer with no verified statutory records
        ExternalEvidenceDto.InsurerPerformanceSummaryDto unverifiedPerf = ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                .insurerCode("UNKNOWN_INSURER")
                .verificationStatus("UNVERIFIED")
                .verifiedStatutory(false)
                .overallPerformanceRating("UNVERIFIED_PENDING_DISCLOSURE")
                .build();

        // Empty hospital network
        ExternalEvidenceDto.HospitalNetworkSummaryDto emptyHospitals = ExternalEvidenceDto.HospitalNetworkSummaryDto.builder()
                .totalNetworkHospitalsInCity(0)
                .cashlessAccessibilityScore(0.0)
                .verificationBadge("UNVERIFIED")
                .build();

        // Modelled quote
        ExternalEvidenceDto.QuoteDetailDto modelledQuote = ExternalEvidenceDto.QuoteDetailDto.builder()
                .isAuthentic(false)
                .verificationStatus("ESTIMATED_ACTUARIAL_MODEL")
                .quotingChannel("ESTIMATED_ACTUARIAL_MODEL")
                .quotedAnnualPremium(new BigDecimal("12000.00"))
                .build();

        ClaimSimulationDto.ClaimSimulationResponse simResp = ClaimSimulationDto.ClaimSimulationResponse.builder()
                .estimatedInsurerPayment(new BigDecimal("111000.00"))
                .estimatedPatientLiability(new BigDecimal("12000.00"))
                .reconciled(true)
                .build();

        when(policyRepository.findByPolicyCode("DEMO_POLICY")).thenReturn(Optional.of(policy));
        when(ruleResolver.resolveRules(eq(policy), isNull(), eq(new BigDecimal("500000.00")))).thenReturn(rules);
        when(externalEvidenceService.getInsurerPerformanceMetrics(any(), any())).thenReturn(unverifiedPerf);
        when(externalEvidenceService.getHospitalNetworkSummary(any(), any(), any())).thenReturn(emptyHospitals);
        when(externalEvidenceService.getAuthenticOrModelledQuote(any(), anyInt(), any(), any(), any())).thenReturn(modelledQuote);
        when(claimEngine.calculateClaim(any(), any())).thenReturn(simResp);

        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                "DEMO_POLICY", 30, new BigDecimal("500000.00"), "Pune", null, "INDIVIDUAL");

        assertNotNull(profile);
        assertFalse(profile.getInsurerPerformance().isVerifiedStatutory());
        assertFalse(profile.getPremiumQuotation().isAuthentic());
        assertEquals("UNVERIFIED", profile.getEvidenceQualityScorecard().getExternalStatutoryEvidenceStatus());
        assertEquals("ESTIMATED_ACTUARIAL_MODEL", profile.getEvidenceQualityScorecard().getQuoteVerificationStatus());
        assertEquals("UNVERIFIED", profile.getEvidenceQualityScorecard().getHospitalRegistryStatus());

        // Missing items must be explicitly listed in pendingOrMissingDisclosures
        List<String> missing = profile.getEvidenceQualityScorecard().getPendingOrMissingDisclosures();
        assertTrue(missing.stream().anyMatch(m -> m.contains("Insurer Performance")));
        assertTrue(missing.stream().anyMatch(m -> m.contains("Hospital Registry")));
        assertTrue(missing.stream().anyMatch(m -> m.contains("actuarial model estimate")));

        // Decoupled Triad with Missing Data: Suitability remains independent, Evidence Completeness shows LIMITED
        assertNotNull(profile.getPolicySuitability());
        assertTrue(profile.getPolicySuitability().isIndependentOfExternalEvidence());
        assertTrue(profile.getPolicySuitability().getSuitabilityScore() >= 60.0);

        assertNotNull(profile.getEvidenceCompleteness());
        assertEquals(0.0, profile.getEvidenceCompleteness().getCompletenessScore());
        assertEquals("LIMITED", profile.getEvidenceCompleteness().getCompletenessStatus());
        assertEquals(0, profile.getEvidenceCompleteness().getVerifiedCount());
    }
}
