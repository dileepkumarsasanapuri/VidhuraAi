package com.vidhura.ai.recommendation.service;

import com.sun.net.httpserver.HttpServer;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5.4: Official Statutory Source Acquisition & Evidence Freshness Test Suite.
 *
 * Verifies that:
 * 1. No evidence becomes AUTO_ACCEPTED or PUBLISHED_ELIGIBLE without verified, locally cached physical source bytes.
 * 2. Source acquisition enforces strict SSRF defenses, redirect validation, and allowlisted statutory domains.
 * 3. Soft-404 error pages returned under HTTP 200 are detected and rejected.
 * 4. HTML disclosures are normalized for deterministic snapshot digests.
 * 5. Freshness lifecycle states (FRESH, REFRESH_DUE, STALE_REVALIDATION_REQUIRED, ACQUISITION_FAILED)
 *    are accurately tracked and stale evidence is withheld from recommendations.
 * 6. Changes in origin source documents trigger REVALIDATION_REQUIRED rather than silent acceptance.
 * 7. Cached artifacts within freshness windows avoid redundant network calls.
 */
@ExtendWith(MockitoExtension.class)
class Phase5_4SourceAcquisitionAndFreshnessTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private ExternalEvidenceValidationRepository validationRepository;

    private OfficialSourceAcquisitionService acquisitionService;
    private EvidenceAcceptanceGovernancePolicy governancePolicy;
    private ExternalEvidenceValidationService validationService;
    private ExternalEvidenceService evidenceService;

    private HttpServer testServer;
    private int testServerPort;
    private String testServerBaseUrl;

    @TempDir
    Path tempCacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // Start lightweight in-memory HTTP server on ephemeral port for deterministic local testing
        testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        testServerPort = testServer.getAddress().getPort();
        testServerBaseUrl = "http://127.0.0.1:" + testServerPort;
        testServer.start();

        // Configure acquisition service with loopback allowed for test harness and temporary cache directory
        acquisitionService = new OfficialSourceAcquisitionService(true);
        acquisitionService.setCacheDirectory(tempCacheDir.toString());

        governancePolicy = new EvidenceAcceptanceGovernancePolicy();
        validationService = new ExternalEvidenceValidationService(
                evidenceRepository, validationRepository, governancePolicy, acquisitionService);
        evidenceService = new ExternalEvidenceService(evidenceRepository, null, null, null, validationService);

        lenient().when(validationRepository.save(any(ExternalEvidenceValidationResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(evidenceRepository.save(any(ExternalEvidenceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Case 1: Successful PDF acquisition with SHA-256 match leads to AUTO_ACCEPTED and publication")
    void testSuccessfulPdfAcquisitionWithSha256MatchAndGovernanceAcceptance() {
        byte[] pdfBytes = "%PDF-1.5\n%Valid Statutory Annual Report Content\n1 0 obj\n<< /Title (IRDAI Return) >>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
        String actualHash = computeSha256(pdfBytes);

        testServer.createContext("/statutory/report.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
            }
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-pdf-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .insurerName("The New India Assurance Co. Ltd.")
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .metricUnit("PERCENTAGE")
                .reportingPeriod("FY 2023-24")
                .sourceDocumentTitle("IRDAI Annual Report 2023-24")
                .sourceUrl(testServerBaseUrl + "/statutory/report.pdf")
                .sourceDocumentHash(actualHash)
                .citationTableReference("STATEMENT H5")
                .citationPageNumber(65)
                .revision(1)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertNotNull(result);
        assertTrue(record.isArtifactBacked(), "Record must be marked as artifact-backed after physical download");
        assertNotNull(record.getArtifactStoragePath(), "Artifact storage path must be populated");
        assertEquals("application/pdf", record.getAcquiredMediaType());
        assertEquals(actualHash, record.getAcquiredSha256());
        assertEquals("FRESH", record.getFreshnessStatus());
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals("ACCEPTED", record.getAcceptanceDecision());
        assertEquals("PUBLISHED_ELIGIBLE", record.getPublicationStatus());
        assertEquals("VERIFIED_STATUTORY", record.getVerificationStatus());
        assertTrue(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 2: HTML snapshot acquisition normalizes ephemeral content and computes stable digest")
    void testSuccessfulHtmlSnapshotAcquisitionAndNormalization() {
        String rawHtml = "<!DOCTYPE html><html><head><script>var now = Date.now();</script><style>.ad{display:none}</style></head>"
                + "<body><!-- Timestamp 2026-10-27 --><h1>NL-39 Ageing of Claims</h1>"
                + "<table><tr><td>Settlement < 3 Months</td><td>95.50%</td></tr></table></body></html>";

        byte[] rawHtmlBytes = rawHtml.getBytes(StandardCharsets.UTF_8);

        // Normalize expectation
        String normalized = acquisitionService.normalizeHtml(rawHtml);
        byte[] normalizedBytes = normalized.getBytes(StandardCharsets.UTF_8);
        String normalizedHash = computeSha256(normalizedBytes);

        testServer.createContext("/disclosures/nl39.html", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, rawHtmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(rawHtmlBytes);
            }
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-html-1")
                .insurerCode("UNITED_INDIA_INSURANCE")
                .insurerName("United India Insurance Co. Ltd.")
                .evidenceType("NL_DISCLOSURE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .metricValue("95.50%")
                .numericValue(new BigDecimal("95.50"))
                .reportingPeriod("Q3-FY2024-25")
                .sourceDocumentTitle("Form NL-39 Public Disclosures")
                .sourceUrl(testServerBaseUrl + "/disclosures/nl39.html")
                .sourceDocumentHash(normalizedHash)
                .citationTableReference("NL-39 Ageing of Claims")
                .revision(1)
                .build();

        validationService.validateRecord(record);

        assertTrue(record.isArtifactBacked());
        assertEquals("text/html", record.getAcquiredMediaType());
        assertEquals(normalizedHash, record.getAcquiredSha256());
        assertEquals("CONTENT_VERIFIED", record.getContentValidationStatus());
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals("PUBLISHED_ELIGIBLE", record.getPublicationStatus());
    }

    @Test
    @DisplayName("Case 3: SSRF defense blocks loopback and private IPs when loopback override is disabled")
    void testSsrfProtectionBlocksLoopbackAndPrivateIpWithoutOverride() {
        OfficialSourceAcquisitionService strictAcquisition = new OfficialSourceAcquisitionService(false); // loopback disallowed
        strictAcquisition.setCacheDirectory(tempCacheDir.toString());

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-ssrf-1")
                .sourceUrl("http://127.0.0.1:" + testServerPort + "/sensitive-internal")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .build();

        OfficialSourceAcquisitionService.AcquisitionResult res = strictAcquisition.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_SSRF, res.getStatus());
        assertFalse(record.isArtifactBacked());
        assertEquals("ACQUISITION_FAILED", record.getFreshnessStatus());
        assertTrue(res.getErrorMessage().contains("SSRF"));
    }

    @Test
    @DisplayName("Case 4: SSRF redirect defense blocks initial allowlisted URL redirecting to cloud metadata IP")
    void testSsrfRedirectDefenseBlocksRedirectToInternalMetadata() {
        testServer.createContext("/legit-looking-redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-ssrf-redirect")
                .sourceUrl(testServerBaseUrl + "/legit-looking-redirect")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .build();

        OfficialSourceAcquisitionService.AcquisitionResult res = acquisitionService.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_SSRF, res.getStatus());
        assertFalse(record.isArtifactBacked());
        assertEquals("ACQUISITION_FAILED", record.getFreshnessStatus());
    }

    @Test
    @DisplayName("Case 5: Soft-404 error page returned with HTTP 200 is detected and rejected")
    void testSoft404ErrorPageDetection() {
        byte[] soft404Bytes = "<html><head><title>Error</title></head><body><h1>404 Not Found</h1><p>The requested policy filing does not exist.</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);

        testServer.createContext("/soft404/filing.html", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, soft404Bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(soft404Bytes);
            }
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-soft404")
                .sourceUrl(testServerBaseUrl + "/soft404/filing.html")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .build();

        OfficialSourceAcquisitionService.AcquisitionResult res = acquisitionService.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_SOFT_404, res.getStatus());
        assertFalse(record.isArtifactBacked());
        assertEquals("ACQUISITION_FAILED", record.getFreshnessStatus());
    }

    @Test
    @DisplayName("Case 6: Cryptographic hash mismatch between downloaded bytes and record claims is quarantined")
    void testHashMismatchRejectionAndQuarantine() {
        byte[] payloadBytes = "%PDF-1.4\nSome Content Here\n%%EOF".getBytes(StandardCharsets.UTF_8);

        testServer.createContext("/tampered.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, payloadBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payloadBytes);
            }
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-mismatch")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .sourceUrl(testServerBaseUrl + "/tampered.pdf")
                .sourceDocumentHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff") // Different hash!
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .build();

        validationService.validateRecord(record);

        assertFalse(record.isArtifactBacked());
        assertEquals("ACQUISITION_FAILED", record.getFreshnessStatus());
        assertEquals("PENDING_REVIEW", record.getApprovalMode());
        assertEquals("STAGED_INTERNAL", record.getPublicationStatus());
        assertFalse(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 7: Non-allowlisted external domain is rejected before any connection is opened")
    void testNonAllowlistedDomainRejected() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-untrusted")
                .sourceUrl("https://unverified-third-party-aggregator.com/filing.pdf")
                .sourceDocumentHash("00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb")
                .build();

        OfficialSourceAcquisitionService.AcquisitionResult res = acquisitionService.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_NON_ALLOWLISTED_DOMAIN, res.getStatus());
        assertFalse(record.isArtifactBacked());
    }

    @Test
    @DisplayName("Case 8: Missing physical artifact fails closed; valid hash alone is NOT auto-accepted")
    void testMissingPhysicalArtifactFailsClosedToPendingReview() {
        // Record with valid 64-char hash and allowlisted URL, but remote file does NOT exist and no local artifact cached
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-unacquired-gate")
                .insurerCode("HDFC_ERGO")
                .insurerName("HDFC ERGO General Insurance")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .numericValue(new BigDecimal("97.20"))
                .sourceUrl(testServerBaseUrl + "/nonexistent-file.pdf")
                .sourceDocumentHash("6c80ff19a379821c4dc8a795342ea218a9b1b4001674bc58cb4022029f66fcdf")
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .build();

        validationService.validateRecord(record);

        assertFalse(record.isArtifactBacked(), "Artifact backing must be false when physical retrieval fails");
        assertEquals("PENDING_REVIEW", record.getApprovalMode());
        assertEquals("PENDING", record.getAcceptanceDecision());
        assertEquals("STAGED_INTERNAL", record.getPublicationStatus());
        assertTrue(record.getFailedMandatoryChecks().stream()
                .anyMatch(c -> c.contains("DISQUALIFIED_UNVERIFIED_ARTIFACT")));
        assertFalse(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 9: Freshness lifecycle tracks FRESH, REFRESH_DUE, and STALE states, withholding stale from publication")
    void testFreshnessLifecycleTransitions() {
        File dummyArtifact = new File(tempCacheDir.toFile(), "dummy_artifact.pdf");
        dummyArtifact.deleteOnExit();

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-freshness")
                .artifactBacked(true)
                .artifactStoragePath(dummyArtifact.getPath())
                .metricCategory("SETTLEMENT_RATIO_ANNUAL")
                .lastAcquiredAt(Instant.now().minus(200, ChronoUnit.DAYS))
                .nextFreshnessCheckDue(Instant.now().minus(20, ChronoUnit.DAYS)) // Due 20 days ago
                .publicationStatus("PUBLISHED_ELIGIBLE")
                .verificationStatus("VERIFIED_STATUTORY")
                .build();

        try {
            dummyArtifact.createNewFile();
            java.nio.file.Files.write(dummyArtifact.toPath(), "%PDF-1.4\nContent".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        // 1. Within 30 days after due: REFRESH_DUE
        String status = acquisitionService.evaluateFreshness(record);
        assertEquals("REFRESH_DUE", status);

        // 2. Past 30-day grace period: STALE_REVALIDATION_REQUIRED
        record.setNextFreshnessCheckDue(Instant.now().minus(45, ChronoUnit.DAYS));
        status = acquisitionService.evaluateFreshness(record);
        assertEquals("STALE_REVALIDATION_REQUIRED", status);
        record.setFreshnessStatus(status);

        // Publication check must withhold stale record
        assertFalse(evidenceService.isEligibleForPublication(record),
                "Evidence in STALE_REVALIDATION_REQUIRED state must be withheld from recommendations");
    }

    @Test
    @DisplayName("Case 10: Source modification at origin triggers revalidation required rather than silent acceptance")
    void testSourceDocumentModificationTriggersRevalidationRequired() {
        byte[] originalBytes = "%PDF-1.4\nOriginal Version 1\n%%EOF".getBytes(StandardCharsets.UTF_8);
        byte[] modifiedBytes = "%PDF-1.4\nModified Version 2 With New Figures\n%%EOF".getBytes(StandardCharsets.UTF_8);

        String originalHash = computeSha256(originalBytes);
        String modifiedHash = computeSha256(modifiedBytes);

        // Server now serves modified bytes
        testServer.createContext("/dynamic-filing.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, modifiedBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(modifiedBytes);
            }
        });

        // Record expects original hash
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-changed")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .sourceUrl(testServerBaseUrl + "/dynamic-filing.pdf")
                .sourceDocumentHash(originalHash)
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .approvalMode("AUTO_ACCEPTED")
                .acceptanceDecision("ACCEPTED")
                .publicationStatus("PUBLISHED_ELIGIBLE")
                .build();

        when(evidenceRepository.findById("ev-54-changed")).thenReturn(Optional.of(record));

        // Revalidate record
        validationService.revalidateRecord("ev-54-changed");

        // The modified file has a hash mismatch against the record's expected hash!
        assertNotEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals("STAGED_INTERNAL", record.getPublicationStatus());
        assertFalse(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 11: Idempotent caching within freshness window reuses cached bytes without redundant network calls")
    void testIdempotentArtifactCachingAvoidsRedundantDownloads() throws Exception {
        byte[] fileBytes = "%PDF-1.4\nCached File\n%%EOF".getBytes(StandardCharsets.UTF_8);
        String sha = computeSha256(fileBytes);

        File cachedFile = new File(tempCacheDir.toFile(), "cached_idemp_" + sha.substring(0, 8) + ".pdf");
        java.nio.file.Files.write(cachedFile.toPath(), fileBytes);

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-cached")
                .sourceDocumentHash(sha)
                .artifactBacked(true)
                .artifactStoragePath(cachedFile.getPath())
                .acquiredMediaType("application/pdf")
                .lastAcquiredAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .nextFreshnessCheckDue(Instant.now().plus(60, ChronoUnit.DAYS)) // Still fresh!
                .freshnessStatus("FRESH")
                .build();

        OfficialSourceAcquisitionService.AcquisitionResult res = acquisitionService.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.NOT_MODIFIED, res.getStatus());
        assertEquals(sha, res.getSha256());
        assertEquals(cachedFile.getPath(), res.getStoragePath());
    }

    @Test
    @DisplayName("Case 12: End-to-end acquire action updates record, generates audit entry, and refreshes status")
    void testEndToEndAcquireActionUpdatesRecordAndGovernance() {
        byte[] pdfBytes = "%PDF-1.5\n%Real Statutory Report Data\n%%EOF".getBytes(StandardCharsets.UTF_8);
        String actualHash = computeSha256(pdfBytes);

        testServer.createContext("/e2e/report.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
            }
        });

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-54-e2e")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .insurerName("The New India Assurance Co. Ltd.")
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .metricUnit("PERCENTAGE")
                .reportingPeriod("FY 2023-24")
                .sourceDocumentTitle("Council for Insurance Ombudsmen Annual Report 2023-24")
                .sourceUrl(testServerBaseUrl + "/e2e/report.pdf")
                .sourceDocumentHash(actualHash)
                .citationTableReference("STATEMENT H5")
                .revision(1)
                .build();

        when(evidenceRepository.findById("ev-54-e2e")).thenReturn(Optional.of(record));

        // Trigger on-demand artifact acquisition
        ExternalEvidenceRecord updated = validationService.acquireArtifactForRecord("ev-54-e2e");

        assertNotNull(updated);
        assertTrue(updated.isArtifactBacked());
        assertNotNull(updated.getArtifactStoragePath());
        assertEquals(actualHash, updated.getAcquiredSha256());
        assertEquals("FRESH", updated.getFreshnessStatus());
        assertEquals("AUTO_ACCEPTED", updated.getApprovalMode());
        assertEquals("PUBLISHED_ELIGIBLE", updated.getPublicationStatus());
        assertEquals("VERIFIED_STATUTORY", updated.getVerificationStatus());

        // Check freshness summary
        when(evidenceRepository.findAll()).thenReturn(List.of(updated));
        ExternalEvidenceDto.FreshnessSummaryDto summary = validationService.getFreshnessSummary();

        assertNotNull(summary);
        assertEquals(1, summary.getTotalRecords());
        assertEquals(1, summary.getFreshCount());
        assertEquals(0, summary.getStaleRevalidationCount());
    }
}
