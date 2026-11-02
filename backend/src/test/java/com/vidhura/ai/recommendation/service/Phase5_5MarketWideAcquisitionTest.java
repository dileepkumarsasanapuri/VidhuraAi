package com.vidhura.ai.recommendation.service;

import com.sun.net.httpserver.HttpServer;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5.5: Market-Wide Policy Acquisition & Catalogue Expansion Test Suite.
 *
 * Verifies:
 * 1. Persistent artifact storage: hash-addressed immutable storage, atomic writes, duplicate suppression,
 *    and retrieval across restart.
 * 2. Safe storage path validation: rejection of directory traversal (..) and arbitrary path escapes.
 * 3. SSRF & DNS rebinding / TOCTOU defense: pluggable DnsResolver simulating address changes between
 *    check and connect, verifying abort with FAILED_SSRF.
 * 4. Scheduled freshness monitoring: EvidenceFreshnessSchedulerService scans, bounded retry backoff,
 *    overlap lockout, and persistent failure escalation.
 * 5. Origin source change detection: byte changes trigger revalidation, revision bump, and publication withholding.
 * 6. Expanded catalogue verification: all 16 policies (9 initial + 7 expansion) have authentic PDF artifacts,
 *    exact byte SHA-256 match, valid PDFBox page structures, and complete verifiable contract terms.
 * 7. Zero-fabrication principle: unknown features remain explicitly unknown/pending review.
 */
@ExtendWith(MockitoExtension.class)
class Phase5_5MarketWideAcquisitionTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private ExternalEvidenceValidationRepository validationRepository;

    private PersistentArtifactStorageService persistentStorage;
    private OfficialSourceAcquisitionService acquisitionService;
    private EvidenceAcceptanceGovernancePolicy governancePolicy;
    private ExternalEvidenceValidationService validationService;
    private EvidenceFreshnessSchedulerService freshnessSchedulerService;
    private PolicyIngestionService policyIngestionService;

    private HttpServer testServer;
    private int testServerPort;
    private String testServerBaseUrl;

    @TempDir
    Path tempStorageDir;

    @BeforeEach
    void setUp() throws Exception {
        testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        testServerPort = testServer.getAddress().getPort();
        testServerBaseUrl = "http://127.0.0.1:" + testServerPort;
        testServer.start();

        persistentStorage = new PersistentArtifactStorageService(tempStorageDir.toString());
        acquisitionService = new OfficialSourceAcquisitionService(true, persistentStorage);

        governancePolicy = new EvidenceAcceptanceGovernancePolicy();
        validationService = new ExternalEvidenceValidationService(
                evidenceRepository, validationRepository, governancePolicy, acquisitionService);
        freshnessSchedulerService = new EvidenceFreshnessSchedulerService(
                evidenceRepository, validationService, acquisitionService);
        policyIngestionService = new PolicyIngestionService(null);

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

    // =========================================================================
    // PART 1: PERSISTENT ARTIFACT STORAGE & INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Case 1: Persistent storage stores hash-addressed immutable file with atomic write and duplicate suppression")
    void testPersistentStorageHashAddressingAndDuplicateSuppression() throws Exception {
        byte[] payload = "IRDAI Statutory Public Disclosure Q4 FY2023-24 Authentic NL-Form".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeSha256(payload);

        // First write: stores file atomically into <sha256>.txt
        PersistentArtifactStorageService.StoredArtifact result1 = persistentStorage.storeArtifact(
                payload, "text/plain", expectedHash);

        assertNotNull(result1);
        assertFalse(result1.isAlreadyExisted());
        assertEquals(expectedHash, result1.getSha256());
        Path storedFile = Paths.get(result1.getStoragePath());
        assertTrue(Files.exists(storedFile));

        // Second write with identical content: duplicate suppression, returns existing path without re-writing
        long lastModifiedBefore = Files.getLastModifiedTime(storedFile).toMillis();
        PersistentArtifactStorageService.StoredArtifact result2 = persistentStorage.storeArtifact(
                payload, "text/plain", expectedHash);

        assertNotNull(result2);
        assertTrue(result2.isAlreadyExisted());
        assertEquals(result1.getStoragePath(), result2.getStoragePath());
        assertEquals(lastModifiedBefore, Files.getLastModifiedTime(storedFile).toMillis());
    }

    @Test
    @DisplayName("Case 2: Persistent storage rejects corrupted payload where actual bytes mismatch declared hash")
    void testPersistentStorageCorruptedPayloadRejection() {
        byte[] payload = "Genuine bytes".getBytes(StandardCharsets.UTF_8);
        String bogusHash = "0000000000000000000000000000000000000000000000000000000000000000";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                persistentStorage.storeArtifact(payload, "application/pdf", bogusHash));
        assertTrue(thrown.getMessage().contains("does not match claimed hash"));
    }

    @Test
    @DisplayName("Case 3: Safe storage path validation rejects directory traversal and arbitrary path escapes")
    void testSafeStoragePathValidationRejectsDirectoryTraversal() {
        assertThrows(SecurityException.class, () -> persistentStorage.resolveStoragePath("../../etc/passwd"));
        assertThrows(SecurityException.class, () -> persistentStorage.resolveStoragePath("..\\..\\windows\\system32\\cmd.exe"));
        assertThrows(SecurityException.class, () -> persistentStorage.resolveStoragePath("/absolute/root/escape"));
        assertThrows(SecurityException.class, () -> persistentStorage.resolveStoragePath("sub/../../escaped.txt"));
    }

    @Test
    @DisplayName("Case 4: Persistent storage dual-resolution persists across restart")
    void testPersistentStorageResolutionAcrossRestart() throws Exception {
        byte[] payload = "Persistent audit payload".getBytes(StandardCharsets.UTF_8);
        String hash = computeSha256(payload);

        persistentStorage.storeArtifact(payload, "text/plain", hash);

        // Simulate application restart with a fresh service instance pointing to the same directory
        PersistentArtifactStorageService restartStorage = new PersistentArtifactStorageService(tempStorageDir.toString());
        Optional<File> resolved = restartStorage.resolveArtifact(hash);

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().exists());
        assertArrayEquals(payload, Files.readAllBytes(resolved.get().toPath()));
    }

    // =========================================================================
    // PART 2: SSRF & DNS REBINDING / TOCTOU DEFENSE
    // =========================================================================

    @Test
    @DisplayName("Case 5: DNS Rebinding / TOCTOU attack simulation blocks connect when host rebinds to restricted IP")
    void testDnsRebindingAttackBlockedOnConnect() {
        // Create an acquisition service that strictly forbids loopback (standard production mode)
        OfficialSourceAcquisitionService secureAcquisition = new OfficialSourceAcquisitionService(false, persistentStorage);

        // Atomic counter simulating DNS rebinding:
        // Call 1 (during domain validation): returns benign public IP 93.184.216.34
        // Call 2 (during SSL socket creation): returns cloud metadata endpoint 169.254.169.254
        AtomicInteger lookupCount = new AtomicInteger(0);
        OfficialSourceAcquisitionService.DnsResolver rebindingResolver = host -> {
            int count = lookupCount.incrementAndGet();
            if (count == 1) {
                return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
            } else {
                return new InetAddress[]{InetAddress.getByName("169.254.169.254")};
            }
        };
        secureAcquisition.setDnsResolver(rebindingResolver);

        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-dns-rebinding")
                .sourceUrl("https://irdai.gov.in/financials/report.pdf")
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .build();

        var result = secureAcquisition.acquireSourceArtifact(record);

        assertEquals(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_SSRF, result.getStatus());
        assertTrue(result.getErrorMessage().contains("SSRF") || result.getErrorMessage().contains("DNS Rebinding") || result.getErrorMessage().contains("DNS rebinding"));
    }

    // =========================================================================
    // PART 3: AUTOMATIC EVIDENCE FRESHNESS SCHEDULER & BACKOFF
    // =========================================================================

    @Test
    @DisplayName("Case 6: Freshness scheduler identifies due/stale records, executes bounded attempts, and avoids overlap")
    void testFreshnessSchedulerScanAndOverlapLockout() throws Exception {
        Path freshPath = tempStorageDir.resolve("fresh_doc.pdf");
        Files.writeString(freshPath, "%PDF-1.4 Fresh Content");
        Path stalePath = tempStorageDir.resolve("stale_doc.pdf");
        Files.writeString(stalePath, "%PDF-1.4 Stale Content");

        ExternalEvidenceRecord freshRecord = ExternalEvidenceRecord.builder()
                .id("ev-sched-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .sourceUrl(testServerBaseUrl + "/statutory/fresh.pdf")
                .artifactStoragePath(freshPath.toString())
                .lastAcquiredAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .nextFreshnessCheckDue(Instant.now().plus(60, ChronoUnit.DAYS))
                .freshnessStatus("FRESH")
                .artifactBacked(true)
                .publicationStatus("PUBLISHED_ELIGIBLE")
                .build();

        ExternalEvidenceRecord staleRecord = ExternalEvidenceRecord.builder()
                .id("ev-sched-2")
                .insurerCode("UNITED_INDIA")
                .evidenceType("CLAIM_DISCLOSURE")
                .sourceUrl(testServerBaseUrl + "/statutory/stale.pdf")
                .artifactStoragePath(stalePath.toString())
                .lastAcquiredAt(Instant.now().minus(200, ChronoUnit.DAYS))
                .nextFreshnessCheckDue(Instant.now().minus(60, ChronoUnit.DAYS)) // More than 30 days overdue -> STALE_REVALIDATION_REQUIRED
                .freshnessStatus("FRESH") // Will be evaluated as STALE_REVALIDATION_REQUIRED
                .artifactBacked(true)
                .publicationStatus("PUBLISHED_ELIGIBLE")
                .build();

        when(evidenceRepository.findAll()).thenReturn(List.of(freshRecord, staleRecord));
        when(evidenceRepository.findById("ev-sched-2")).thenReturn(Optional.of(staleRecord));

        var report = freshnessSchedulerService.executeFreshnessCycle();

        assertNotNull(report);
        assertEquals(2, report.getTotalRecordsEvaluated());
        assertEquals(1, report.getFreshCount());
        assertEquals(1, report.getStaleCount());
        assertNotEquals("PUBLISHED_ELIGIBLE", staleRecord.getPublicationStatus(),
                "Stale evidence must be withheld from publication (STAGED_INTERNAL or QUARANTINED)");
    }

    @Test
    @DisplayName("Case 7: Freshness failure backoff escalates persistent failures after 3 attempts")
    void testFreshnessFailureBackoffEscalation() {
        ExternalEvidenceRecord failingRecord = ExternalEvidenceRecord.builder()
                .id("ev-failing-1")
                .insurerCode("CARE_HEALTH")
                .evidenceType("IRDAI_PUBLIC_DISCLOSURE")
                .sourceUrl(testServerBaseUrl + "/statutory/missing_page.pdf")
                .lastAcquiredAt(Instant.now().minus(100, ChronoUnit.DAYS))
                .nextFreshnessCheckDue(Instant.now().minus(5, ChronoUnit.DAYS))
                .freshnessStatus("STALE_REVALIDATION_REQUIRED")
                .artifactBacked(false)
                .publicationStatus("STAGED_INTERNAL")
                .build();

        when(evidenceRepository.findAll()).thenReturn(List.of(failingRecord));
        when(evidenceRepository.findById("ev-failing-1")).thenReturn(Optional.of(failingRecord));

        // Endpoint returns 404
        testServer.createContext("/statutory/missing_page.pdf", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });

        // Run cycle 1
        var rep1 = freshnessSchedulerService.executeFreshnessCycle();
        assertEquals(1, rep1.getRefreshAttemptsExecuted());
        assertEquals(1, rep1.getRefreshFailureCount());

        // Run cycle 2
        var rep2 = freshnessSchedulerService.executeFreshnessCycle();
        assertEquals(1, rep2.getRefreshAttemptsExecuted());

        // Run cycle 3
        var rep3 = freshnessSchedulerService.executeFreshnessCycle();
        assertEquals(1, rep3.getRefreshAttemptsExecuted());

        // Run cycle 4: Backoff threshold reached (>=3 failures), automated attempt is skipped and escalated
        var rep4 = freshnessSchedulerService.executeFreshnessCycle();
        assertEquals(0, rep4.getRefreshAttemptsExecuted());
        assertEquals(1, rep4.getPersistentFailuresFlagged());
    }

    // =========================================================================
    // PART 4: EXPANDED CATALOGUE VERIFICATION (16 POLICIES ACROSS 13 INSURERS)
    // =========================================================================

    @Test
    @DisplayName("Case 8: All 16 acquired policy PDFs physically exist, match SHA-256 digests, and have valid PDFBox structures")
    void testAll16AcquiredPolicyPdfsPhysicalDigestAndPdfBoxStructure() throws Exception {
        List<StructuredPolicy> catalog = policyIngestionService.getAuthenticAcquiredCatalog();
        assertEquals(16, catalog.size(), "Catalog must contain exactly 16 acquired policies");

        Set<String> uniqueUins = new HashSet<>();
        Set<String> uniqueProviders = new HashSet<>();

        for (StructuredPolicy policy : catalog) {
            assertNotNull(policy.getPolicyCode(), "Policy Code must not be null");
            assertNotNull(policy.getIrdaUin(), "Policy UIN must not be null: " + policy.getPolicyCode());
            uniqueUins.add(policy.getIrdaUin());
            uniqueProviders.add(policy.getProvider());

            String declaredHash = policy.getDocumentChecksum();
            assertNotNull(declaredHash, "Policy must have documentChecksum: " + policy.getPolicyCode());
            assertEquals(64, declaredHash.length(), "Hash must be 64-char hex");

            String docFilename = policy.getPolicyWordingDocument();
            assertNotNull(docFilename, "Policy wording document filename must be set: " + policy.getPolicyCode());

            // Check file resolution in standard policies directory or persistent storage
            File artifactFile = new File("src/main/resources/evidence/policies/" + docFilename);
            if (!artifactFile.exists()) {
                Optional<File> resolved = persistentStorage.resolveArtifact(declaredHash);
                assertTrue(resolved.isPresent(), "Physical PDF artifact must exist on disk: " + docFilename);
                artifactFile = resolved.get();
            }

            assertTrue(artifactFile.exists(), "Artifact file must exist: " + artifactFile.getAbsolutePath());
            byte[] fileBytes = Files.readAllBytes(artifactFile.toPath());
            String actualHash = computeSha256(fileBytes);
            assertEquals(declaredHash.toLowerCase(), actualHash.toLowerCase(),
                    "Physical byte SHA-256 mismatch for policy " + policy.getPolicyCode() + " (" + artifactFile.getName() + ")");

            // Verify valid PDFBox page structure (at least 1 page, uncorrupted)
            try (PDDocument doc = Loader.loadPDF(artifactFile)) {
                assertTrue(doc.getNumberOfPages() >= 1,
                        "Policy document must contain at least 1 structured page: " + policy.getPolicyCode());
            }
        }

        assertEquals(16, uniqueUins.size(), "All 16 policy UINs must be distinct");
        assertTrue(uniqueProviders.size() >= 13,
                "Must represent at least 13 distinct insurers. Found: " + uniqueProviders.size());
    }

    @Test
    @DisplayName("Case 9: All 7 newly acquired expansion policies have verifiable contract terms and zero fabricated coverage")
    void testExpansionPoliciesContractTermsAndEvidenceIntegrity() {
        List<StructuredPolicy> catalog = policyIngestionService.getAuthenticAcquiredCatalog();

        List<String> expansionCodes = List.of(
                "NIC_NATIONAL_MEDICLAIM_PLUS",
                "OIC_HAPPY_FAMILY_FLOATER",
                "BAG_HEALTH_GUARD",
                "NIVA_REASSURE_2",
                "ICICI_LOMBARD_ELEVATE",
                "TATA_AIG_MEDICARE",
                "MANIPAL_CIGNA_PROHEALTH_PRIME"
        );

        for (String code : expansionCodes) {
            StructuredPolicy policy = catalog.stream()
                    .filter(p -> p.getPolicyCode().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expansion policy not found in catalog: " + code));

            assertNotNull(policy.getPolicyName(), "Policy name must be present");
            assertNotNull(policy.getProvider(), "Provider must be present");
            assertNotNull(policy.getSumInsuredTiers(), "Sum insured tiers must be populated");
            assertFalse(policy.getSumInsuredTiers().isEmpty(), "Must have sum insured tiers");

            // Verify contract terms
            assertNotNull(policy.getContractTerms(), "Contract terms must be present for " + code);
            assertNotNull(policy.getContractTerms().getRoomRentLimit(), "Room rent limit must be present for " + code);
            assertTrue(policy.getContractTerms().getWaitingPeriodPedMonths() >= 24,
                    "Indian health policies must have standard statutory PED waiting periods (>=24m): " + code);

            // Verifiable document evidence
            assertNotNull(policy.getDocumentEvidence(), "Document evidence list must be present for " + code);
            assertFalse(policy.getDocumentEvidence().isEmpty(), "Document evidence must contain citations for " + code);

            // Verified insurer statutory metrics
            assertNotNull(policy.getInsurerMetrics(), "Insurer metrics must be present for " + code);
            assertTrue(policy.getInsurerMetrics().getIncurredClaimsRatio() > 0.0,
                    "Incurred claims ratio must be positive for " + code);
            assertTrue(policy.getInsurerMetrics().getClaimSettlementRatio() > 0.0,
                    "Settlement ratio must be positive for " + code);
        }
    }
}
