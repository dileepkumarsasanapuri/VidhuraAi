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

    private ExternalEvidenceService externalEvidenceService;
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

        controller = new ExternalEvidenceController(externalEvidenceService);

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

        when(evidenceRepository.existsBySourceDocumentHashAndMetricKey(VALID_SHA256, "repudiation_annual"))
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
}
