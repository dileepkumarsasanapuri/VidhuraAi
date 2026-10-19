package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalEvidenceServiceTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private HospitalNetworkRepository hospitalRepository;

    @Mock
    private PremiumQuoteRepository quoteRepository;

    @Mock
    private StructuredPolicyRepository policyRepository;

    private ExternalEvidenceService evidenceService;

    private User standardUser;
    private User reviewerUser;

    private static final String VALID_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        evidenceService = new ExternalEvidenceService(
                evidenceRepository,
                hospitalRepository,
                quoteRepository,
                policyRepository
        );

        standardUser = User.builder()
                .id("usr-1")
                .email("consumer@example.com")
                .role("ROLE_USER")
                .build();

        reviewerUser = User.builder()
                .id("rev-1")
                .email("compliance.officer@vidhura.ai")
                .role("ROLE_REVIEWER")
                .build();
    }

    @Test
    @DisplayName("Should ingest evidence and stage as UNVERIFIED_PENDING_REVIEW when submitted by standard user")
    void testIngestEvidenceStandardUser() {
        ExternalEvidenceDto.IngestEvidenceRequest req = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .insurerCode("HDFC_ERGO")
                .insurerName("HDFC ERGO General Insurance")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceUrl("https://irdai.gov.in/annual-report-2023-24.pdf")
                .sourceDocumentHash(VALID_SHA256)
                .citationPageNumber(142)
                .citationTableReference("Table IV.12")
                .build();

        when(evidenceRepository.existsBySourceDocumentHashAndMetricKey(eq(VALID_SHA256), eq("settlement_ratio_3m")))
                .thenReturn(false);

        when(evidenceRepository.save(any(ExternalEvidenceRecord.class)))
                .thenAnswer(inv -> {
                    ExternalEvidenceRecord r = inv.getArgument(0);
                    r.setId("ev-101");
                    return r;
                });

        ExternalEvidenceDto.EvidenceRecordDto result = evidenceService.ingestEvidenceRecord(req, standardUser);

        assertNotNull(result);
        assertEquals("ev-101", result.getId());
        assertEquals("UNVERIFIED_PENDING_REVIEW", result.getVerificationStatus());
        assertNull(result.getVerifiedBy());
        assertEquals("HDFC_ERGO", result.getInsurerCode());
    }

    @Test
    @DisplayName("Should ingest evidence directly as VERIFIED_STATUTORY when submitted by reviewer/admin")
    void testIngestEvidenceReviewerUser() {
        ExternalEvidenceDto.IngestEvidenceRequest req = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .insurerCode("STAR_HEALTH")
                .metricCategory("INCURRED_CLAIM_RATIO")
                .metricKey("icr_annual")
                .metricValue("64.20%")
                .numericValue(new BigDecimal("64.20"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceUrl("https://irdai.gov.in/annual-report.pdf")
                .sourceDocumentHash(VALID_SHA256)
                .build();

        when(evidenceRepository.existsBySourceDocumentHashAndMetricKey(eq(VALID_SHA256), eq("icr_annual")))
                .thenReturn(false);

        when(evidenceRepository.save(any(ExternalEvidenceRecord.class)))
                .thenAnswer(inv -> {
                    ExternalEvidenceRecord r = inv.getArgument(0);
                    r.setId("ev-102");
                    return r;
                });

        ExternalEvidenceDto.EvidenceRecordDto result = evidenceService.ingestEvidenceRecord(req, reviewerUser);

        assertEquals("VERIFIED_STATUTORY", result.getVerificationStatus());
        assertEquals("compliance.officer@vidhura.ai", result.getVerifiedBy());
    }

    @Test
    @DisplayName("Should reject evidence ingestion if source document hash is not a valid 64-character SHA-256")
    void testRejectInvalidSha256() {
        ExternalEvidenceDto.IngestEvidenceRequest req = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .insurerCode("CARE")
                .metricKey("settlement_ratio_3m")
                .sourceDocumentHash("invalid-short-hash")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                evidenceService.ingestEvidenceRecord(req, standardUser));

        assertTrue(ex.getMessage().contains("64-character"));
    }

    @Test
    @DisplayName("Should reject duplicate evidence ingestion for same document hash and metric key")
    void testRejectDuplicateEvidence() {
        ExternalEvidenceDto.IngestEvidenceRequest req = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .insurerCode("HDFC_ERGO")
                .metricKey("settlement_ratio_3m")
                .sourceDocumentHash(VALID_SHA256)
                .build();

        when(evidenceRepository.existsBySourceDocumentHashAndMetricKey(eq(VALID_SHA256), eq("settlement_ratio_3m")))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                evidenceService.ingestEvidenceRecord(req, standardUser));
    }

    @Test
    @DisplayName("Should require ROLE_REVIEWER or ROLE_ADMIN for human verification gate")
    void testReviewerGateEnforcement() {
        ExternalEvidenceDto.VerifyEvidenceRequest verifyReq = ExternalEvidenceDto.VerifyEvidenceRequest.builder()
                .verificationStatus("VERIFIED_STATUTORY")
                .verificationNotes("Confirmed against IRDAI official annual report publication.")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                evidenceService.verifyEvidenceRecord("ev-101", verifyReq, standardUser));

        ExternalEvidenceRecord staged = ExternalEvidenceRecord.builder()
                .id("ev-101")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        when(evidenceRepository.findById("ev-101")).thenReturn(Optional.of(staged));
        when(evidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExternalEvidenceDto.EvidenceRecordDto verified =
                evidenceService.verifyEvidenceRecord("ev-101", verifyReq, reviewerUser);

        assertEquals("VERIFIED_STATUTORY", verified.getVerificationStatus());
        assertEquals("compliance.officer@vidhura.ai", verified.getVerifiedBy());
    }

    @Test
    @DisplayName("Should return unverified shell without fabricating numbers when insurer has no statutory records")
    void testGetInsurerMetricsUnverified() {
        when(evidenceRepository.findByInsurerCode("UNKNOWN_INSURER")).thenReturn(List.of());

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary =
                evidenceService.getInsurerPerformanceMetrics("UNKNOWN_INSURER", null);

        assertNotNull(summary);
        assertFalse(summary.isVerifiedStatutory());
        assertEquals("UNVERIFIED", summary.getVerificationStatus());
        assertNull(summary.getSettlementRatioLessThan3Months());
        assertNull(summary.getIncurredClaimRatio());
    }

    @Test
    @DisplayName("Should aggregate verified statutory metrics and compute objective rating")
    void testGetInsurerMetricsVerified() {
        ExternalEvidenceRecord r1 = ExternalEvidenceRecord.builder()
                .insurerCode("HDFC_ERGO")
                .insurerName("HDFC ERGO General Insurance")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceDocumentHash(VALID_SHA256)
                .verificationStatus("VERIFIED_STATUTORY")
                .build();

        ExternalEvidenceRecord r2 = ExternalEvidenceRecord.builder()
                .insurerCode("HDFC_ERGO")
                .metricCategory("INCURRED_CLAIM_RATIO")
                .metricValue("68.10%")
                .numericValue(new BigDecimal("68.10"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceDocumentHash(VALID_SHA256)
                .verificationStatus("VERIFIED_STATUTORY")
                .build();

        when(evidenceRepository.findByInsurerCodeAndReportingPeriod("HDFC_ERGO", "FY2023-24"))
                .thenReturn(List.of(r1, r2));

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary =
                evidenceService.getInsurerPerformanceMetrics("HDFC_ERGO", "FY2023-24");

        assertTrue(summary.isVerifiedStatutory());
        assertEquals("98.40%", summary.getSettlementRatioLessThan3Months());
        assertEquals("68.10%", summary.getIncurredClaimRatio());
        assertEquals("EXCELLENT", summary.getOverallPerformanceRating());
    }

    @Test
    @DisplayName("Should query hospital network and compute accessibility score")
    void testHospitalNetworkSummary() {
        HospitalNetworkNode h1 = HospitalNetworkNode.builder()
                .hospitalId("hosp-1")
                .hospitalName("Lilavati Hospital & Research Centre")
                .registryRohiniCode("8900080001001")
                .city("Mumbai")
                .pinCode("400050")
                .cashlessAgreements(List.of(
                        HospitalNetworkNode.CashlessAgreement.builder()
                                .insurerCode("HDFC_ERGO")
                                .cashlessActive(true)
                                .tier("TIER_1_PREFERRED")
                                .build()
                ))
                .build();

        when(hospitalRepository.findByCityAndCashlessInsurer("Mumbai", "HDFC_ERGO"))
                .thenReturn(List.of(h1));

        ExternalEvidenceDto.HospitalNetworkSummaryDto result =
                evidenceService.getHospitalNetworkSummary("HDFC_ERGO", "Mumbai", null);

        assertNotNull(result);
        assertEquals(1, result.getTotalNetworkHospitalsInCity());
        assertEquals(1, result.getPreferredTier1Count());
        assertEquals(25.0, result.getCashlessAccessibilityScore());
        assertEquals("VERIFIED_HOSPITAL_REGISTRY", result.getVerificationBadge());
    }

    @Test
    @DisplayName("Should return authentic quote when verified prospect quote exists in repository")
    void testAuthenticQuoteHit() {
        PremiumQuoteRecord authentic = PremiumQuoteRecord.builder()
                .quoteId("q-auth-1")
                .policyCode("HDFC_ERGO_OPTIIMA_SECURE")
                .insurerCode("HDFC_ERGO")
                .age(35)
                .sumInsured(new BigDecimal("1000000"))
                .quotedAnnualPremium(new BigDecimal("14500.00"))
                .quotedBasePremium(new BigDecimal("12288.14"))
                .quotedGst(new BigDecimal("2211.86"))
                .quotingChannel("OFFICIAL_PORTAL")
                .quoteReferenceNumber("HDFC-QUO-991203")
                .sourceDocumentHash(VALID_SHA256)
                .verificationStatus("VERIFIED_AUTHENTIC_QUOTE")
                .expiryTimestamp(Instant.now().plus(15, ChronoUnit.DAYS))
                .build();

        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
                eq("HDFC_ERGO_OPTIIMA_SECURE"), eq(35), eq(new BigDecimal("1000000"))))
                .thenReturn(Optional.of(authentic));

        ExternalEvidenceDto.QuoteDetailDto quote = evidenceService.getAuthenticOrModelledQuote(
                "HDFC_ERGO_OPTIIMA_SECURE", 35, new BigDecimal("1000000"), "400001", "INDIVIDUAL");

        assertNotNull(quote);
        assertTrue(quote.isAuthentic());
        assertEquals("VERIFIED_AUTHENTIC_QUOTE", quote.getVerificationStatus());
        assertEquals(new BigDecimal("14500.00"), quote.getQuotedAnnualPremium());
        assertEquals("HDFC-QUO-991203", quote.getQuoteReferenceNumber());
    }

    @Test
    @DisplayName("Should fallback to modelled actuarial estimate when no authentic quote exists")
    void testModelledQuoteFallback() {
        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(anyString(), anyInt(), any()))
                .thenReturn(Optional.empty());

        StructuredPolicy policy = StructuredPolicy.builder()
                .policyCode("CARE_SUPREME")
                .provider("Care Health Insurance")
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000L)
                                .premiumAge18To35(12500.0)
                                .build()
                ))
                .build();

        when(policyRepository.findByPolicyCode("CARE_SUPREME")).thenReturn(Optional.of(policy));

        ExternalEvidenceDto.QuoteDetailDto quote = evidenceService.getAuthenticOrModelledQuote(
                "CARE_SUPREME", 35, new BigDecimal("1000000"), "400001", "INDIVIDUAL");

        assertNotNull(quote);
        assertFalse(quote.isAuthentic());
        assertEquals("ESTIMATED_ACTUARIAL_MODEL", quote.getVerificationStatus());
        // For age 35, factor is 1.25 -> 10000 * 1.25 = 12500
        assertEquals(new BigDecimal("12500.00"), quote.getQuotedAnnualPremium());
    }
}
