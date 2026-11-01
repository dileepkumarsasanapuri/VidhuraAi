package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.dto.PolicyIntelligenceProfileDto;
import com.vidhura.ai.recommendation.engine.DeterministicClaimEngine;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.HospitalNetworkNode;
import com.vidhura.ai.recommendation.model.PremiumQuoteRecord;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.HospitalNetworkRepository;
import com.vidhura.ai.recommendation.repository.PremiumQuoteRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3.1 Real-Data Validation and Recommendation Readiness Test Suite.
 *
 * Verifies:
 * 1. Contractual ground truth against all three authentic acquired policies.
 * 2. Small, verified external-data cohort (IRDAI statutory metrics, ROHINI hospital nodes, authentic quotes).
 * 3. Decoupled Triad: Policy Suitability, Evidence Completeness, and Insurer Performance are strictly separated.
 * 4. Missing external evidence does not degrade suitability score nor fabricate performance ratings.
 * 5. Stale / expired quotes gracefully fall back to non-binding actuarial estimates with explicit warnings.
 * 6. Hospital network listings contain explicit pre-authorization disclaimers.
 * 7. Synthetic demonstration fixture isolation (HDFC_OPTIMA_SECURE).
 * 8. Reconciled claim simulation arithmetic.
 */
@ExtendWith(MockitoExtension.class)
class Phase3RealDataValidationTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private HospitalNetworkRepository hospitalRepository;

    @Mock
    private PremiumQuoteRepository quoteRepository;

    private ApprovedPolicyRuleResolver ruleResolver;
    private DeterministicClaimEngine claimEngine;
    private ExternalEvidenceService externalEvidenceService;
    private PolicyIntelligenceProfileService profileService;
    private PolicyIngestionService policyIngestionService;
    private ExternalEvidenceDataSeeder dataSeeder;

    // Authentic Policies
    private StructuredPolicy niaMediclaim;
    private StructuredPolicy uiicFamilyMedicare;
    private StructuredPolicy niaPremierMediclaim;
    private StructuredPolicy hdfcOptimaDemo;

    @BeforeEach
    void setUp() {
        ruleResolver = new ApprovedPolicyRuleResolver(policyRepository);
        claimEngine = new DeterministicClaimEngine();
        externalEvidenceService = new ExternalEvidenceService(
                evidenceRepository,
                hospitalRepository,
                quoteRepository,
                policyRepository
        );
        profileService = new PolicyIntelligenceProfileService(
                policyRepository,
                ruleResolver,
                claimEngine,
                externalEvidenceService
        );
        policyIngestionService = new PolicyIngestionService(policyRepository);
        dataSeeder = new ExternalEvidenceDataSeeder(
                evidenceRepository,
                hospitalRepository,
                quoteRepository
        );

        List<StructuredPolicy> authenticCatalog = policyIngestionService.getAuthenticAcquiredCatalog();
        niaMediclaim = authenticCatalog.stream()
                .filter(p -> "THE_NEW_INDIA_ASSURANCE_COMPAN_V102425".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
        uiicFamilyMedicare = authenticCatalog.stream()
                .filter(p -> "UNITED_INDIA_FAMILY_MEDICARE".equals(p.getPolicyCode())
                        || "UNITED_INDIA_INSURANCE_COMPANY_V042122".equals(p.getPolicyCode())
                        || (p.getPolicyAliases() != null && p.getPolicyAliases().contains("UNITED_INDIA_INSURANCE_COMPANY_V042122")))
                .findFirst().orElseThrow();
        niaPremierMediclaim = authenticCatalog.stream()
                .filter(p -> "THE_NEW_INDIA_PREMIER_MEDICLAIM".equals(p.getPolicyCode())
                        || "NEW_INDIA_PREMIER_MEDICLAIM_V032021".equals(p.getPolicyCode())
                        || (p.getPolicyAliases() != null && p.getPolicyAliases().contains("NEW_INDIA_PREMIER_MEDICLAIM_V032021")))
                .findFirst().orElseThrow();

        hdfcOptimaDemo = policyIngestionService.getSeedCatalog().stream()
                .filter(p -> "HDFC_OPTIMA_SECURE".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
    }

    // =========================================================================
    // 1. AUDIT OF THE THREE AUTHENTIC CONTRACT RECORDS
    // =========================================================================

    @Test
    @DisplayName("Audit 1: New India Mediclaim Policy contractual facts and UIN revision integrity")
    void testAuditNewIndiaMediclaimContractualGroundTruth() {
        assertEquals("THE_NEW_INDIA_ASSURANCE_COMPAN_V102425", niaMediclaim.getPolicyCode());
        assertEquals("NIAHLIP21277V042021", niaMediclaim.getIrdaUin());
        assertEquals("V042021", niaMediclaim.getUinRevision());
        assertFalse(niaMediclaim.isSyntheticFixture());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", niaMediclaim.getVerificationStatus());
        assertEquals("bfc304901063fa21b1e145c22d2ee15bd5f8190e82b4caca069cfe96dbcedc9f", niaMediclaim.getDocumentChecksum());
        assertEquals("NIAHLIP21277V042021_2020-2021.pdf", niaMediclaim.getPolicyWordingDocument());

        // Contractual Specs: Single Private AC Room (uncapped), 0% copay, 36m PED
        assertFalse(niaMediclaim.getContractTerms().isRoomRentCapped());
        assertFalse(niaMediclaim.getContractTerms().isProportionateDeductionApplies());
        assertEquals(0.0, niaMediclaim.getContractTerms().getCopayPercentage());
        assertEquals(36, niaMediclaim.getContractTerms().getWaitingPeriodPedMonths());

        // Clause Evidence
        List<StructuredPolicy.PolicyEvidenceItem> clauses = niaMediclaim.getDocumentEvidence();
        assertNotNull(clauses);
        assertEquals(4, clauses.size());
        assertTrue(clauses.stream().anyMatch(c -> "ROOM_RENT".equals(c.getTag()) && c.getSectionReference().equals("Section 3.1")));
        assertTrue(clauses.stream().anyMatch(c -> "PED".equals(c.getTag()) && c.getSectionReference().equals("Section 3.3")));
        assertTrue(clauses.stream().anyMatch(c -> "COPAY".equals(c.getTag()) && c.getSectionReference().equals("Section 3.4")));
    }

    @Test
    @DisplayName("Audit 2: United India Family Medicare Policy contractual facts and room cap limits")
    void testAuditUiicFamilyMedicareContractualGroundTruth() {
        assertEquals("UNITED_INDIA_FAMILY_MEDICARE", uiicFamilyMedicare.getPolicyCode());
        assertTrue(uiicFamilyMedicare.matchesPolicyCodeOrAlias("UNITED_INDIA_INSURANCE_COMPANY_V042122"));
        assertEquals("UIIHLIP22070V042122", uiicFamilyMedicare.getIrdaUin());
        assertEquals("V042122", uiicFamilyMedicare.getUinRevision());
        assertFalse(uiicFamilyMedicare.isSyntheticFixture());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", uiicFamilyMedicare.getVerificationStatus());
        assertEquals("6bb20e21fa1c97b514f57ae6764b2e9b7a22df71d8afb2ece0539e0e17eea267", uiicFamilyMedicare.getDocumentChecksum());
        assertEquals("UIIHLIP22070V042122_HEALTH2068.pdf", uiicFamilyMedicare.getPolicyWordingDocument());

        // Contractual Specs: 1% SI room rent limit with proportionate deductions, 48m PED
        assertTrue(uiicFamilyMedicare.getContractTerms().isRoomRentCapped());
        assertTrue(uiicFamilyMedicare.getContractTerms().isProportionateDeductionApplies());
        assertEquals(48, uiicFamilyMedicare.getContractTerms().getWaitingPeriodPedMonths());

        // Clause Evidence
        List<StructuredPolicy.PolicyEvidenceItem> clauses = uiicFamilyMedicare.getDocumentEvidence();
        assertNotNull(clauses);
        assertEquals(3, clauses.size());
        assertTrue(clauses.stream().anyMatch(c -> "ROOM_RENT".equals(c.getTag()) && c.getSectionReference().equals("Section 2.38")));
        assertTrue(clauses.stream().anyMatch(c -> "PED".equals(c.getTag()) && c.getSectionReference().equals("Section 2.32")));
    }

    @Test
    @DisplayName("Audit 3: New India Premier Mediclaim Policy high sum insured facts")
    void testAuditNewIndiaPremierMediclaimContractualGroundTruth() {
        assertEquals("THE_NEW_INDIA_PREMIER_MEDICLAIM", niaPremierMediclaim.getPolicyCode());
        assertTrue(niaPremierMediclaim.matchesPolicyCodeOrAlias("NEW_INDIA_PREMIER_MEDICLAIM_V032021"));
        assertEquals("NIAHLIP21279V032021", niaPremierMediclaim.getIrdaUin());
        assertEquals("V032021", niaPremierMediclaim.getUinRevision());
        assertFalse(niaPremierMediclaim.isSyntheticFixture());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", niaPremierMediclaim.getVerificationStatus());
        assertEquals("17c54a21891f9be3f5e0cfd8579eb120f2eceb14aaba2fa695b62c1ab45f3191", niaPremierMediclaim.getDocumentChecksum());

        // Contractual Specs: Any room category (uncapped), 0% copay, 24m expedited PED
        assertFalse(niaPremierMediclaim.getContractTerms().isRoomRentCapped());
        assertFalse(niaPremierMediclaim.getContractTerms().isProportionateDeductionApplies());
        assertEquals(0.0, niaPremierMediclaim.getContractTerms().getCopayPercentage());
        assertEquals(24, niaPremierMediclaim.getContractTerms().getWaitingPeriodPedMonths());
    }

    // =========================================================================
    // 2. SMALL VERIFIED EXTERNAL-DATA COHORT INTEGRATION
    // =========================================================================

    @Test
    @DisplayName("External Cohort: Statutory IRDAI metrics for New India Assurance and United India")
    void testVerifiedStatutoryMetricsCohort() {
        List<ExternalEvidenceRecord> statutoryCohort = dataSeeder.getVerifiedStatutoryRecordsCohort();
        assertNotNull(statutoryCohort);
        assertEquals(12, statutoryCohort.size()); // 6 metrics for NIA + 6 metrics for UIIC

        // Check New India Assurance ICR and Settlement
        Optional<ExternalEvidenceRecord> niaIcr = statutoryCohort.stream()
                .filter(r -> "NEW_INDIA".equals(r.getInsurerCode()) && "INCURRED_CLAIM_RATIO".equals(r.getMetricCategory()))
                .findFirst();
        assertTrue(niaIcr.isPresent());
        assertEquals("91.20%", niaIcr.get().getMetricValue());
        assertEquals(ExternalEvidenceDataSeeder.NIA_STATUTORY_HASH, niaIcr.get().getSourceDocumentHash());
        assertEquals("UNVERIFIED_PENDING_REVIEW", niaIcr.get().getVerificationStatus());
        assertNull(niaIcr.get().getVerifiedBy());

        // Check United India Insurance Solvency and Turnaround
        Optional<ExternalEvidenceRecord> uiicSolvency = statutoryCohort.stream()
                .filter(r -> "UNITED_INDIA".equals(r.getInsurerCode()) && "SOLVENCY_RATIO".equals(r.getMetricCategory()))
                .findFirst();
        assertTrue(uiicSolvency.isPresent());
        assertEquals("1.62", uiicSolvency.get().getMetricValue());
        assertEquals(ExternalEvidenceDataSeeder.UIIC_STATUTORY_HASH, uiicSolvency.get().getSourceDocumentHash());
        assertEquals("UNVERIFIED_PENDING_REVIEW", uiicSolvency.get().getVerificationStatus());
        assertNull(uiicSolvency.get().getVerifiedBy());
    }

    @Test
    @DisplayName("External Cohort: City-level hospital network nodes with official ROHINI codes and bilateral unconfirmed status")
    void testVerifiedHospitalNetworkNodesCohort() {
        List<HospitalNetworkNode> hospitalCohort = dataSeeder.getVerifiedHospitalNodesCohort();
        assertNotNull(hospitalCohort);
        assertEquals(4, hospitalCohort.size());

        // Lilavati Hospital (Mumbai, 400050)
        HospitalNetworkNode lilavati = hospitalCohort.stream()
                .filter(h -> "HOSP-MUM-001".equals(h.getHospitalId()))
                .findFirst().orElseThrow();
        assertEquals("8900080001001", lilavati.getRegistryRohiniCode());
        assertEquals("Mumbai", lilavati.getCity());
        assertEquals("400050", lilavati.getPinCode());
        assertTrue(lilavati.getCashlessAgreements().stream().anyMatch(a -> "NEW_INDIA".equals(a.getInsurerCode()) && a.isCashlessActive()));
        assertTrue(lilavati.getCashlessAgreements().stream().anyMatch(a -> "UNITED_INDIA".equals(a.getInsurerCode()) && a.isCashlessActive()));
        assertEquals("UNVERIFIED_PENDING_BILATERAL_CONFIRMATION", lilavati.getCashlessAgreements().get(0).getVerificationStatus());
        assertNull(lilavati.getCashlessAgreements().get(0).getSourceDocumentHash());

        // Breach Candy Hospital (Mumbai, 400026)
        HospitalNetworkNode breachCandy = hospitalCohort.stream()
                .filter(h -> "HOSP-MUM-002".equals(h.getHospitalId()))
                .findFirst().orElseThrow();
        assertEquals("8900080002002", breachCandy.getRegistryRohiniCode());
        assertEquals("400026", breachCandy.getPinCode());

        // Apollo Greams Road (Chennai, 600006)
        HospitalNetworkNode apollo = hospitalCohort.stream()
                .filter(h -> "HOSP-CHE-001".equals(h.getHospitalId()))
                .findFirst().orElseThrow();
        assertEquals("8900080003003", apollo.getRegistryRohiniCode());
        assertEquals("Chennai", apollo.getCity());

        // AIIMS (New Delhi, 110029)
        HospitalNetworkNode aiims = hospitalCohort.stream()
                .filter(h -> "HOSP-DEL-001".equals(h.getHospitalId()))
                .findFirst().orElseThrow();
        assertEquals("8900080004004", aiims.getRegistryRohiniCode());
        assertEquals("New Delhi", aiims.getCity());
    }

    @Test
    @DisplayName("External Cohort: Historical quotes with 18% GST calculation, expired timestamps and stale flags")
    void testVerifiedAuthenticQuotesCohort() {
        List<PremiumQuoteRecord> quoteCohort = dataSeeder.getVerifiedQuotesCohort();
        assertNotNull(quoteCohort);
        assertEquals(3, quoteCohort.size());

        // Quote 1: New India Mediclaim
        PremiumQuoteRecord niaQuote = quoteCohort.stream()
                .filter(q -> "THE_NEW_INDIA_ASSURANCE_COMPAN_V102425".equals(q.getPolicyCode()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("14160.00"), niaQuote.getQuotedAnnualPremium());
        assertEquals(new BigDecimal("12000.00"), niaQuote.getQuotedBasePremium());
        assertEquals(new BigDecimal("2160.00"), niaQuote.getQuotedGst());
        assertEquals("NIA-PROP-2024-882194", niaQuote.getQuoteReferenceNumber());
        assertEquals("EXPIRED_AUTHENTIC_QUOTE", niaQuote.getVerificationStatus());
        assertTrue(niaQuote.isStale());

        // Quote 2: United India Family Medicare
        PremiumQuoteRecord uiicQuote = quoteCohort.stream()
                .filter(q -> "UNITED_INDIA_INSURANCE_COMPANY_V042122".equals(q.getPolicyCode()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("13452.00"), uiicQuote.getQuotedAnnualPremium());
        assertEquals(new BigDecimal("11400.00"), uiicQuote.getQuotedBasePremium());
        assertEquals(new BigDecimal("2052.00"), uiicQuote.getQuotedGst());
        assertEquals("UIIC-PROP-2024-551029", uiicQuote.getQuoteReferenceNumber());
        assertEquals("EXPIRED_AUTHENTIC_QUOTE", uiicQuote.getVerificationStatus());
        assertTrue(uiicQuote.isStale());
    }

    // =========================================================================
    // 3. DECOUPLED TRIAD: SUITABILITY | EVIDENCE COMPLETENESS | INSURER PERFORMANCE
    // =========================================================================

    @Test
    @DisplayName("Decoupled Triad: Contract suitability is decoupled from external evidence completeness")
    void testDecoupledTriadIndependentSuitability() {
        // Setup mock: New India Mediclaim with verified external data
        when(policyRepository.findByPolicyCode(niaMediclaim.getPolicyCode())).thenReturn(Optional.of(niaMediclaim));

        ExternalEvidenceRecord verifiedIcr = ExternalEvidenceRecord.builder()
                .insurerCode("NEW_INDIA")
                .metricCategory("INCURRED_CLAIM_RATIO")
                .metricKey("INCURRED_CLAIM_RATIO_FY2022_23")
                .metricValue("91.20%")
                .numericValue(new BigDecimal("91.20"))
                .verificationStatus("VERIFIED_STATUTORY")
                .reportingPeriod("FY 2022-23")
                .sourceDocumentTitle("IRDAI Annual Report 2022-23")
                .sourceDocumentHash(ExternalEvidenceDataSeeder.NIA_STATUTORY_HASH)
                .verifiedBy("reviewer@vidhura.ai")
                .build();
        ExternalEvidenceRecord verifiedSettlement = ExternalEvidenceRecord.builder()
                .insurerCode("NEW_INDIA")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("SETTLEMENT_RATIO_LESS_3_MONTHS_FY2022_23")
                .metricValue("93.80%")
                .numericValue(new BigDecimal("93.80"))
                .verificationStatus("VERIFIED_STATUTORY")
                .reportingPeriod("FY 2022-23")
                .sourceDocumentTitle("IRDAI Annual Report 2022-23")
                .sourceDocumentHash(ExternalEvidenceDataSeeder.NIA_STATUTORY_HASH)
                .verifiedBy("reviewer@vidhura.ai")
                .build();
        when(evidenceRepository.findByInsurerCode("NEW_INDIA")).thenReturn(List.of(verifiedIcr, verifiedSettlement));

        HospitalNetworkNode verifiedHosp = HospitalNetworkNode.builder()
                .hospitalId("HOSP-MUM-001")
                .hospitalName("Lilavati Hospital and Research Centre")
                .city("Mumbai")
                .cashlessAgreements(List.of(
                        HospitalNetworkNode.CashlessAgreement.builder()
                                .insurerCode("NEW_INDIA")
                                .cashlessActive(true)
                                .tier("TIER_1_PREFERRED")
                                .verificationStatus("VERIFIED_ACTIVE_AGREEMENT")
                                .build()
                ))
                .build();
        when(hospitalRepository.findByCityAndCashlessInsurer("Mumbai", "NEW_INDIA")).thenReturn(List.of(verifiedHosp));

        PremiumQuoteRecord verifiedQuote = PremiumQuoteRecord.builder()
                .policyCode(niaMediclaim.getPolicyCode())
                .age(35)
                .sumInsured(new BigDecimal("1000000.00"))
                .quotedAnnualPremium(new BigDecimal("14160.00"))
                .quotedBasePremium(new BigDecimal("12000.00"))
                .quotedGst(new BigDecimal("2160.00"))
                .quotingChannel("DIRECT_INSURER_PORTAL")
                .quoteReferenceNumber("NIA-PROP-2024-882194")
                .quoteTimestamp(Instant.now())
                .expiryTimestamp(Instant.now().plus(30, ChronoUnit.DAYS))
                .stale(false)
                .verificationStatus("VERIFIED_AUTHENTIC_QUOTE")
                .build();
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
                eq(niaMediclaim.getPolicyCode()), eq(35), eq(new BigDecimal("1000000.00"))))
                .thenReturn(Optional.of(verifiedQuote));

        PolicyIntelligenceProfileDto completeProfile = profileService.getPolicyIntelligenceProfile(
                niaMediclaim.getPolicyCode(), 35, new BigDecimal("1000000.00"), "Mumbai", "400050", "INDIVIDUAL", 92.0);

        assertNotNull(completeProfile);

        // Pillar 1: Suitability
        assertEquals(92.0, completeProfile.getPolicySuitability().getSuitabilityScore());
        assertEquals("EXCELLENT_FIT", completeProfile.getPolicySuitability().getSuitabilityRating());
        assertTrue(completeProfile.getPolicySuitability().isIndependentOfExternalEvidence());

        // Pillar 2: Evidence Completeness (All 4 dimensions verified)
        assertEquals(100.0, completeProfile.getEvidenceCompleteness().getCompletenessScore());
        assertEquals("COMPLETE", completeProfile.getEvidenceCompleteness().getCompletenessStatus());
        assertEquals(4, completeProfile.getEvidenceCompleteness().getVerifiedCount());
        assertTrue(completeProfile.getEvidenceCompleteness().isContractualTermsVerified());
        assertTrue(completeProfile.getEvidenceCompleteness().isStatutoryPerformanceVerified());
        assertTrue(completeProfile.getEvidenceCompleteness().isAuthenticQuoteVerified());
        assertTrue(completeProfile.getEvidenceCompleteness().isHospitalNetworkVerified());

        // Pillar 3: Insurer Performance
        assertEquals("VERY_GOOD", completeProfile.getInsurerPerformance().getOverallPerformanceRating());
        assertTrue(completeProfile.getInsurerPerformance().isVerifiedStatutory());
        assertEquals("91.20%", completeProfile.getInsurerPerformance().getIncurredClaimRatio());

        // Hospital Network Disclaimer check
        assertNotNull(completeProfile.getHospitalAccessibility().getNetworkDisclaimer());
        assertTrue(completeProfile.getHospitalAccessibility().getNetworkDisclaimer().contains("not constitute guaranteed cashless approval"));

        // Now test the exact same policy WITHOUT external statutory records
        when(evidenceRepository.findByInsurerCode("NEW_INDIA")).thenReturn(Collections.emptyList());
        when(hospitalRepository.findByCityAndCashlessInsurer("Mumbai", "NEW_INDIA")).thenReturn(Collections.emptyList());
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        PolicyIntelligenceProfileDto unverifiedExternalProfile = profileService.getPolicyIntelligenceProfile(
                niaMediclaim.getPolicyCode(), 35, new BigDecimal("1000000.00"), "Mumbai", "400050", "INDIVIDUAL", 92.0);

        // Critical Test: Suitability remains intact at 92.0% despite external data being completely missing!
        assertEquals(92.0, unverifiedExternalProfile.getPolicySuitability().getSuitabilityScore(),
                "Policy suitability must NEVER degrade simply because insurer statutory disclosures are missing");
        assertTrue(unverifiedExternalProfile.getPolicySuitability().isIndependentOfExternalEvidence());

        // Evidence Completeness correctly drops to 25% (only contract terms verified)
        assertEquals(25.0, unverifiedExternalProfile.getEvidenceCompleteness().getCompletenessScore());
        assertEquals("LIMITED", unverifiedExternalProfile.getEvidenceCompleteness().getCompletenessStatus());
        assertEquals(1, unverifiedExternalProfile.getEvidenceCompleteness().getVerifiedCount());

        // Insurer Performance reports UNVERIFIED_PENDING_DISCLOSURE without fabricating numbers or poor ratings
        assertEquals("UNVERIFIED_PENDING_DISCLOSURE", unverifiedExternalProfile.getInsurerPerformance().getOverallPerformanceRating());
        assertFalse(unverifiedExternalProfile.getInsurerPerformance().isVerifiedStatutory());
        assertNull(unverifiedExternalProfile.getInsurerPerformance().getIncurredClaimRatio());
    }

    // =========================================================================
    // 4. STALE QUOTE AND ACTUARIAL MODEL FALLBACK
    // =========================================================================

    @Test
    @DisplayName("Quotes: Expired authentic quote triggers stale flag and falls back to actuarial model")
    void testExpiredQuoteFallbackToModelledEstimate() {
        PremiumQuoteRecord expiredQuote = PremiumQuoteRecord.builder()
                .quoteId("q-expired-1")
                .policyCode(niaMediclaim.getPolicyCode())
                .age(35)
                .sumInsured(new BigDecimal("1000000.00"))
                .quotedAnnualPremium(new BigDecimal("14160.00"))
                .expiryTimestamp(Instant.now().minus(10, ChronoUnit.DAYS)) // Expired 10 days ago
                .stale(false)
                .verificationStatus("VERIFIED_AUTHENTIC_QUOTE")
                .build();

        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
                eq(niaMediclaim.getPolicyCode()), eq(35), eq(new BigDecimal("1000000.00"))))
                .thenReturn(Optional.of(expiredQuote));
        when(policyRepository.findByPolicyCode(niaMediclaim.getPolicyCode())).thenReturn(Optional.of(niaMediclaim));

        ExternalEvidenceDto.QuoteDetailDto quote = externalEvidenceService.getAuthenticOrModelledQuote(
                niaMediclaim.getPolicyCode(), 35, new BigDecimal("1000000.00"), "400050", "INDIVIDUAL");

        assertNotNull(quote);
        // Falls back to actuarial estimate
        assertFalse(quote.isAuthentic());
        assertEquals("ESTIMATED_ACTUARIAL_MODEL", quote.getVerificationStatus());
        assertEquals("ESTIMATED_ACTUARIAL_MODEL", quote.getQuotingChannel());
        assertTrue(quote.getQuotedAnnualPremium().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(quote.getQuotedBasePremium());
        assertNotNull(quote.getQuotedGst());
    }

    // =========================================================================
    // 5. SYNTHETIC DEMO FIXTURE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Fixtures: Synthetic demo fixture (Niva Acti Assure) is strictly flagged syntheticFixture=true and isolated")
    void testSyntheticFixtureIsolation() {
        StructuredPolicy demoPolicy = policyIngestionService.getSeedCatalog().stream()
                .filter(p -> "NIVA_ACTI_ASSURE".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();

        assertEquals("NIVA_ACTI_ASSURE", demoPolicy.getPolicyCode());
        assertTrue(demoPolicy.isSyntheticFixture(), "Niva Acti Assure must be strictly marked as synthetic fixture");
        assertEquals("SYNTHETIC_DEMO", demoPolicy.getReviewerApprovalStatus());

        when(policyRepository.findByPolicyCode("NIVA_ACTI_ASSURE")).thenReturn(Optional.of(demoPolicy));
        when(evidenceRepository.findByInsurerCode(any())).thenReturn(Collections.emptyList());
        when(hospitalRepository.findByCityAndCashlessInsurer(any(), any())).thenReturn(Collections.emptyList());
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                "NIVA_ACTI_ASSURE", 35, new BigDecimal("1000000.00"), "Mumbai", null, "INDIVIDUAL", null);

        assertNotNull(profile);
        assertTrue(profile.getContractOverview().isSyntheticFixture());
        assertFalse(profile.getEvidenceCompleteness().isContractualTermsVerified(),
                "Synthetic demo fixtures must not be marked as verified contractual terms in evidence completeness");
        assertTrue(profile.getEvidenceQualityScorecard().getPendingOrMissingDisclosures().stream()
                .anyMatch(d -> d.contains("Synthetic demonstration fixture")));
    }

    // =========================================================================
    // 6. BENCHMARK CLAIM SIMULATION WATERFALL ARITHMETIC
    // =========================================================================

    @Test
    @DisplayName("Claims: Standard benchmark hospitalization bill (₹1,23,000) produces exact mathematical liability")
    void testBenchmarkClaimSimulationReconciled() {
        when(policyRepository.findByPolicyCode(uiicFamilyMedicare.getPolicyCode())).thenReturn(Optional.of(uiicFamilyMedicare));
        when(evidenceRepository.findByInsurerCode(any())).thenReturn(Collections.emptyList());
        when(hospitalRepository.findByCityAndCashlessInsurer(any(), any())).thenReturn(Collections.emptyList());
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        // UIIC has 1% SI room rent limit: on 5L SI = ₹5,000/day. On ₹1,23,000 bill (2 days @ ₹6,500 room):
        // Room excess: (₹6,500 - ₹5,000) * 2 = ₹3,000
        // Proportionate reduction: (1 - 5000/6500) * ₹78,000 associated charges = ₹18,000
        // Non-medical consumables: ₹12,000
        // Total patient liability = ₹3,000 + ₹18,000 + ₹12,000 = ₹33,000.00
        // Total insurer payment = ₹1,23,000 - ₹33,000 = ₹90,000.00
        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                uiicFamilyMedicare.getPolicyCode(), 45, new BigDecimal("500000.00"), "Mumbai", null, "INDIVIDUAL", null);

        PolicyIntelligenceProfileDto.BenchmarkClaimSimulationSummaryDto claim = profile.getBenchmarkClaimSimulation();
        assertNotNull(claim);
        assertEquals(new BigDecimal("123000.00"), claim.getTotalSubmittedBill());
        assertEquals(new BigDecimal("90000.00"), claim.getEstimatedInsurerPayment());
        assertEquals(new BigDecimal("33000.00"), claim.getEstimatedPatientLiability());
        assertTrue(claim.isReconciled());
        assertEquals(new BigDecimal("3000.00"), claim.getRoomRentExcessDeduction());
        assertEquals(new BigDecimal("18000.00"), claim.getProportionateDeduction());
        assertEquals(new BigDecimal("12000.00"), claim.getNonMedicalConsumablesDeduction());
        assertTrue(claim.getContractualExplanation().contains("proportionate deduction"));
    }

    @Test
    @DisplayName("Audit Gate: Raw seeded cohort with unverified statutory, unconfirmed hospital, and expired quote does not inflate scores or claim verification")
    void testAuditedSeededCohortIntegrity() {
        when(policyRepository.findByPolicyCode(niaMediclaim.getPolicyCode())).thenReturn(Optional.of(niaMediclaim));
        when(evidenceRepository.findByInsurerCode("NEW_INDIA")).thenReturn(dataSeeder.getVerifiedStatutoryRecordsCohort().subList(0, 6));
        when(hospitalRepository.findByCityAndCashlessInsurer("Mumbai", "NEW_INDIA")).thenReturn(dataSeeder.getVerifiedHospitalNodesCohort().subList(0, 2));
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
                eq(niaMediclaim.getPolicyCode()), eq(35), eq(new BigDecimal("1000000.00"))))
                .thenReturn(Optional.of(dataSeeder.getVerifiedQuotesCohort().get(0)));

        PolicyIntelligenceProfileDto profile = profileService.getPolicyIntelligenceProfile(
                niaMediclaim.getPolicyCode(), 35, new BigDecimal("1000000.00"), "Mumbai", "400050", "INDIVIDUAL", 92.0);

        assertNotNull(profile);

        // 1. Contract suitability remains intact (decoupled!)
        assertEquals(92.0, profile.getPolicySuitability().getSuitabilityScore());
        assertTrue(profile.getPolicySuitability().isIndependentOfExternalEvidence());

        // 2. Insurer performance reports UNVERIFIED_PENDING_DISCLOSURE because seeded records are UNVERIFIED_PENDING_REVIEW
        assertEquals("UNVERIFIED_PENDING_DISCLOSURE", profile.getInsurerPerformance().getOverallPerformanceRating());
        assertFalse(profile.getInsurerPerformance().isVerifiedStatutory());

        // 3. Hospital network badge is UNVERIFIED_PENDING_REGISTRY_AUDIT and accessibility score is 0.0
        assertEquals(0.0, profile.getHospitalAccessibility().getCashlessAccessibilityScore());
        assertEquals("UNVERIFIED_PENDING_REGISTRY_AUDIT", profile.getHospitalAccessibility().getVerificationBadge());

        // 4. Quote falls back to non-binding actuarial model because 2024 quote is expired and stale
        assertFalse(profile.getPremiumQuotation().isAuthentic());
        assertEquals("ESTIMATED_ACTUARIAL_MODEL", profile.getPremiumQuotation().getVerificationStatus());

        // 5. Evidence Completeness reports LIMITED (25%) - only contractual terms are verified!
        assertEquals(25.0, profile.getEvidenceCompleteness().getCompletenessScore());
        assertEquals("LIMITED", profile.getEvidenceCompleteness().getCompletenessStatus());
        assertEquals(1, profile.getEvidenceCompleteness().getVerifiedCount());
    }
}
