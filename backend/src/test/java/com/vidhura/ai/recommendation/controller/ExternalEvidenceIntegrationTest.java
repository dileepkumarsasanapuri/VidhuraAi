package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
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
import com.vidhura.ai.recommendation.service.ExternalEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalEvidenceIntegrationTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private HospitalNetworkRepository hospitalRepository;

    @Mock
    private PremiumQuoteRepository quoteRepository;

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository validationRepository;

    @Mock
    private com.vidhura.ai.recommendation.service.OfficialSourceAcquisitionService acquisitionService;

    private ExternalEvidenceService externalEvidenceService;
    private com.vidhura.ai.recommendation.service.ExternalEvidenceValidationService validationService;
    private ExternalEvidenceController controller;

    private User adminUser;
    private User normalUser;

    private static final String VALID_SHA256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @BeforeEach
    void setUp() {
        externalEvidenceService = new ExternalEvidenceService(
                evidenceRepository,
                hospitalRepository,
                quoteRepository,
                policyRepository
        );

        validationService = new com.vidhura.ai.recommendation.service.ExternalEvidenceValidationService(
                evidenceRepository,
                validationRepository,
                new com.vidhura.ai.recommendation.service.EvidenceAcceptanceGovernancePolicy(),
                acquisitionService
        );

        controller = new ExternalEvidenceController(externalEvidenceService, validationService);

        adminUser = User.builder()
                .id("admin-1")
                .email("admin@vidhura.ai")
                .role("ROLE_ADMIN")
                .build();

        normalUser = User.builder()
                .id("usr-1")
                .email("patient@example.com")
                .role("ROLE_USER")
                .build();
    }

    @Test
    @DisplayName("GET /metrics/{insurerCode} should return statutory metrics from repository")
    void testGetInsurerMetrics() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("rec-1")
                .insurerCode("HDFC_ERGO")
                .insurerName("HDFC ERGO General Insurance Co. Ltd.")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceDocumentHash(VALID_SHA256)
                .verificationStatus("VERIFIED_STATUTORY")
                .build();

        when(evidenceRepository.findByInsurerCodeAndReportingPeriod("HDFC_ERGO", "FY2023-24"))
                .thenReturn(List.of(record));

        ResponseEntity<ApiResponse<ExternalEvidenceDto.InsurerPerformanceSummaryDto>> response =
                controller.getInsurerMetrics("HDFC_ERGO", "FY2023-24");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getSuccess());

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary = response.getBody().getData();
        assertEquals("HDFC_ERGO", summary.getInsurerCode());
        assertEquals("98.40%", summary.getSettlementRatioLessThan3Months());
        assertTrue(summary.isVerifiedStatutory());
    }

    @Test
    @DisplayName("GET /hospitals should return network hospitals for insurer in specified city")
    void testGetHospitalNetwork() {
        HospitalNetworkNode h = HospitalNetworkNode.builder()
                .hospitalId("hosp-1")
                .hospitalName("Apollo Hospitals Bannerghatta")
                .registryRohiniCode("8900080002002")
                .city("Bengaluru")
                .pinCode("560076")
                .cashlessAgreements(List.of(
                        HospitalNetworkNode.CashlessAgreement.builder()
                                .insurerCode("STAR_HEALTH")
                                .cashlessActive(true)
                                .tier("TIER_1_PREFERRED")
                                .build()
                ))
                .build();

        when(hospitalRepository.findByCityAndCashlessInsurer("Bengaluru", "STAR_HEALTH"))
                .thenReturn(List.of(h));

        ResponseEntity<ApiResponse<ExternalEvidenceDto.HospitalNetworkSummaryDto>> response =
                controller.getHospitalNetwork("STAR_HEALTH", "Bengaluru", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCount());

        ExternalEvidenceDto.HospitalNetworkSummaryDto summary = response.getBody().getData();
        assertEquals(1, summary.getTotalNetworkHospitalsInCity());
        assertEquals(1, summary.getPreferredTier1Count());
        assertEquals(25.0, summary.getCashlessAccessibilityScore());
    }

    @Test
    @DisplayName("GET /quotes should return authentic quote if available, or non-binding actuarial estimate")
    void testGetQuoteAuthenticAndFallback() {
        // Authentic quote test
        PremiumQuoteRecord authRecord = PremiumQuoteRecord.builder()
                .quoteId("q-real-1")
                .policyCode("STAR_COMPREHENSIVE")
                .sumInsured(new BigDecimal("1000000"))
                .age(40)
                .quotedAnnualPremium(new BigDecimal("18200.00"))
                .quotedBasePremium(new BigDecimal("15423.73"))
                .quotedGst(new BigDecimal("2776.27"))
                .quotingChannel("OFFICIAL_PORTAL")
                .quoteReferenceNumber("STAR-PORTAL-8821")
                .sourceDocumentHash(VALID_SHA256)
                .verificationStatus("VERIFIED_AUTHENTIC_QUOTE")
                .expiryTimestamp(Instant.now().plus(20, ChronoUnit.DAYS))
                .build();

        when(quoteRepository.findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
                eq("STAR_COMPREHENSIVE"), eq(40), eq(new BigDecimal("1000000"))))
                .thenReturn(Optional.of(authRecord));

        ResponseEntity<ApiResponse<ExternalEvidenceDto.QuoteDetailDto>> response =
                controller.getQuote("STAR_COMPREHENSIVE", 40, new BigDecimal("1000000"), "560001", "INDIVIDUAL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExternalEvidenceDto.QuoteDetailDto data = response.getBody().getData();
        assertTrue(data.isAuthentic());
        assertEquals("VERIFIED_AUTHENTIC_QUOTE", data.getVerificationStatus());
        assertEquals("STAR-PORTAL-8821", data.getQuoteReferenceNumber());
    }

    @Test
    @DisplayName("POST /ingest should stage new evidence with SHA-256 validation")
    void testIngestEvidenceEndpoint() {
        ExternalEvidenceDto.IngestEvidenceRequest req = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("NL_DISCLOSURE")
                .insurerCode("NIVA_BUPA")
                .metricCategory("REPUDIATION_RATIO")
                .metricKey("repudiation_annual")
                .metricValue("1.85%")
                .numericValue(new BigDecimal("1.85"))
                .reportingPeriod("FY2023-24")
                .sourceDocumentTitle("Niva Bupa Public Disclosure Form NL-24")
                .sourceUrl("https://nivabupa.com/public-disclosure/nl-24.pdf")
                .sourceDocumentHash(VALID_SHA256)
                .build();

        when(evidenceRepository.existsByInsurerCodeAndMetricKeyAndReportingPeriodAndRevision(
                eq("NIVA_BUPA"), eq("repudiation_annual"), eq("FY2023-24"), eq(1)))
                .thenReturn(false);

        when(evidenceRepository.save(any(ExternalEvidenceRecord.class)))
                .thenAnswer(inv -> {
                    ExternalEvidenceRecord r = inv.getArgument(0);
                    r.setId("ev-niva-1");
                    return r;
                });

        ResponseEntity<ApiResponse<ExternalEvidenceDto.EvidenceRecordDto>> response =
                controller.ingestEvidence(req, normalUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertEquals("UNVERIFIED_PENDING_REVIEW", response.getBody().getData().getVerificationStatus());
    }

    @Test
    @DisplayName("POST /verify/{id} should allow human reviewer sign-off")
    void testVerifyEvidenceEndpoint() {
        ExternalEvidenceRecord staged = ExternalEvidenceRecord.builder()
                .id("ev-niva-1")
                .insurerCode("NIVA_BUPA")
                .metricKey("repudiation_annual")
                .reportingPeriod("FY2023-24")
                .revision(1)
                .sourceDocumentHash(VALID_SHA256)
                .citationTableReference("Form NL-24 Table 1")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        when(evidenceRepository.findById("ev-niva-1")).thenReturn(Optional.of(staged));
        when(evidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExternalEvidenceDto.VerifyEvidenceRequest verifyReq = ExternalEvidenceDto.VerifyEvidenceRequest.builder()
                .verificationStatus("VERIFIED_STATUTORY")
                .verificationNotes("Cross-referenced with quarterly public disclosure filing.")
                .build();

        ResponseEntity<ApiResponse<ExternalEvidenceDto.EvidenceRecordDto>> response =
                controller.verifyEvidence("ev-niva-1", verifyReq, adminUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("VERIFIED_STATUTORY", response.getBody().getData().getVerificationStatus());
        assertEquals("admin@vidhura.ai", response.getBody().getData().getVerifiedBy());
    }

    @Test
    @DisplayName("GET /records should return all evidence records for reviewer audit")
    void testGetEvidenceRecordsEndpoint() {
        ExternalEvidenceRecord r = ExternalEvidenceRecord.builder()
                .id("rec-1")
                .insurerCode("NEW_INDIA")
                .metricKey("INCURRED_CLAIM_RATIO_FY2022_23")
                .reportingPeriod("FY 2022-23")
                .revision(2)
                .revisionType("STAGED_CORRECTION")
                .sourceDocumentHash(VALID_SHA256)
                .citationTableReference("Statement 10")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        when(evidenceRepository.findByInsurerCodeOrderByReportingPeriodDescRevisionDesc("NEW_INDIA"))
                .thenReturn(List.of(r));

        ResponseEntity<ApiResponse<List<ExternalEvidenceDto.EvidenceRecordDto>>> response =
                controller.getEvidenceRecords("NEW_INDIA", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getCount());
        assertEquals(2, response.getBody().getData().get(0).getRevision());
    }

    @Test
    @DisplayName("GET /grievances/{insurerCode} should return distinct grievance channels with normalized metrics")
    void testGetGrievancesEndpoint() {
        ResponseEntity<ApiResponse<ExternalEvidenceDto.InsurerGrievanceIntelligenceDto>> response =
                controller.getInsurerGrievances("NEW_INDIA", "FY 2022-23");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        ExternalEvidenceDto.InsurerGrievanceIntelligenceDto dto = response.getBody().getData();
        assertEquals("NEW_INDIA", dto.getInsurerCode());
        assertNotNull(dto.getInternalGrievances());
        assertEquals(new BigDecimal("5567"), dto.getInternalGrievances().getReceived());
        assertNotNull(dto.getOmbudsmanHealth());
        assertEquals(new BigDecimal("1300"), dto.getOmbudsmanHealth().getReceived());
        assertTrue(dto.getDuplicateCountingWarning().contains("double-counting"));
    }

    @Test
    @DisplayName("GET /regulatory-slas should return mandated IRDAI SLAs with actualCompliance as UNAVAILABLE")
    void testGetRegulatorySlasEndpoint() {
        ResponseEntity<ApiResponse<List<ExternalEvidenceDto.RegulatorySlaBenchmarkDto>>> response =
                controller.getRegulatorySlas();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        List<ExternalEvidenceDto.RegulatorySlaBenchmarkDto> slas = response.getBody().getData();
        assertEquals(4, slas.size());

        // 1-hour pre-auth
        ExternalEvidenceDto.RegulatorySlaBenchmarkDto preAuth = slas.stream()
                .filter(s -> "SLA_PREAUTH_CASHLESS_1H".equals(s.getRuleKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(preAuth);
        assertEquals(60, preAuth.getTargetTimelineMinutes());
        assertEquals("UNAVAILABLE", preAuth.getActualComplianceStatus());
        assertTrue(preAuth.getRegulatoryCitation().contains("Clause 15(b)"));

        // 3-hour discharge
        ExternalEvidenceDto.RegulatorySlaBenchmarkDto discharge = slas.stream()
                .filter(s -> "SLA_DISCHARGE_CASHLESS_3H".equals(s.getRuleKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(discharge);
        assertEquals(180, discharge.getTargetTimelineMinutes());
        assertEquals("UNAVAILABLE", discharge.getActualComplianceStatus());
        assertTrue(discharge.getRegulatoryCitation().contains("Clause 16(a)"));
    }

    @Test
    @DisplayName("POST /validate/{recordId} should trigger automated validation and return structured result")
    void testValidateEvidenceRecordEndpoint() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-test-val-1")
                .insurerCode("NEW_INDIA")
                .metricKey("claims_settlement_ratio")
                .numericValue(new BigDecimal("98.4"))
                .sourceUrl("https://irdai.gov.in/report.pdf")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .citationPageNumber(10)
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .revision(1)
                .build();

        when(evidenceRepository.findById("ev-test-val-1")).thenReturn(Optional.of(record));
        when(validationRepository.save(any(com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ApiResponse<ExternalEvidenceDto.ValidationResultDto>> response =
                controller.validateEvidenceRecord("ev-test-val-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertEquals("VALIDATION_PASSED", response.getBody().getData().getOverallOutcome());
        assertEquals("ev-test-val-1", response.getBody().getData().getEvidenceRecordId());
        // Status remains UNVERIFIED_PENDING_REVIEW
        assertEquals("UNVERIFIED_PENDING_REVIEW", record.getVerificationStatus());
    }

    @Test
    @DisplayName("GET /review-queue should return prioritized list with priority scores and tiers")
    void testGetReviewQueueEndpoint() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-queue-1")
                .insurerCode("UNITED_INDIA")
                .metricKey("complaints_per_10k_policies")
                .metricValue("8.28")
                .latestReviewPriorityScore(16)
                .latestPriorityTier("MEDIUM")
                .latestValidationOutcome("VALIDATION_PASSED_WITH_WARNINGS")
                .revision(1)
                .build();

        when(evidenceRepository.findAll()).thenReturn(List.of(record));

        ResponseEntity<ApiResponse<List<ExternalEvidenceDto.ReviewQueueItemDto>>> response =
                controller.getReviewQueue(null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("ev-queue-1", response.getBody().getData().get(0).getEvidenceRecordId());
        assertEquals(16, response.getBody().getData().get(0).getReviewPriorityScore());
        assertEquals("MEDIUM", response.getBody().getData().get(0).getPriorityTier());
    }

    @Test
    @DisplayName("POST /validate-all should scan and return summary statistics across all records")
    void testValidateAllEvidenceEndpoint() {
        ExternalEvidenceRecord r1 = ExternalEvidenceRecord.builder()
                .id("ev-b-1")
                .insurerCode("NEW_INDIA")
                .metricKey("test_metric_1")
                .sourceUrl("https://irdai.gov.in/doc.pdf")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .build();

        when(evidenceRepository.findAll()).thenReturn(List.of(r1));
        when(validationRepository.save(any(com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ApiResponse<ExternalEvidenceDto.BatchValidationSummaryDto>> response =
                controller.validateAllEvidence();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().getTotalRecordsScanned());
    }
}
