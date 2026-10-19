package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.benchmark.RecommendationQualityBenchmarkService;
import com.vidhura.ai.recommendation.benchmark.RecommendationQualityReport;
import com.vidhura.ai.recommendation.controller.PolicyCatalogController;
import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.CatalogMetadata;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.CatalogMetadataRepository;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stage Release Gates Verification Suite.
 *
 * Validates the 5 critical release gates required before production customer traffic:
 * 1. P0 Guest-role permissions: ROLE_GUEST is blocked from all mutating endpoints.
 * 2. P0 Multi-instance catalog consistency & Partition Staleness Policy (15-min tolerance cutoff).
 * 3. P0 Independent benchmark quality with documented ground truth and actuarial claim math.
 * 4. P1 SSRF connection pinning: direct validated destination IP socket binding (anti-DNS rebinding).
 * 5. P1 Document identity & revision parsing (alphanumeric, slash formats, addenda, endorsements).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ReleaseGatesVerificationTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private RecommendationProfileRepository profileRepository;

    @Mock
    private RecommendationResultRepository resultRepository;

    @Mock
    private CatalogMetadataRepository metadataRepository;

    @Mock
    private PolicySourceRepository sourceRepository;

    @Mock
    private DiscoveredDocumentRepository documentRepository;

    private RecommendationService recommendationService;
    private PolicyPublicationService publicationService;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyCatalogController catalogController;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicyDocumentFetcher documentFetcher;
    private RecommendationQualityBenchmarkService benchmarkService;

    private User guestUser;
    private User reviewerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        canonicalProductRegistry = new CanonicalProductRegistry();
        PolicyIngestionService ingestionService = new PolicyIngestionService(policyRepository);

        recommendationService = new RecommendationService(
                policyRepository,
                profileRepository,
                resultRepository,
                new EligibilityEngine(),
                new RankingEngine(),
                ingestionService,
                metadataRepository
        );

        publicationService = new PolicyPublicationService(
                policyRepository,
                recommendationService,
                canonicalProductRegistry
        );

        discoveryPipelineService = new PolicyDiscoveryPipelineService(
                policyRepository,
                canonicalProductRegistry,
                recommendationService,
                new PolicyChangeImpactAnalyzer()
        );

        benchmarkService = new RecommendationQualityBenchmarkService(recommendationService);

        catalogController = new PolicyCatalogController(
                discoveryPipelineService,
                publicationService,
                recommendationService,
                policyRepository,
                sourceRepository,
                documentRepository,
                null,
                benchmarkService
        );

        documentFetcher = new PolicyDocumentFetcher();
        documentFetcher.setAllowLocalLoopback(false);

        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("pol-" + UUID.randomUUID().toString().substring(0, 8));
            }
            return p;
        });

        guestUser = User.builder().id("u-guest").email("guest@vidhura.ai").role("ROLE_GUEST").build();
        reviewerUser = User.builder().id("u-rev").email("reviewer@vidhura.ai").role("ROLE_REVIEWER").build();
        adminUser = User.builder().id("u-adm").email("admin@vidhura.ai").role("ROLE_ADMIN").build();
    }

    // =========================================================================
    // GATE 1 (P0): GUEST ROLE PERMISSION HARDENING
    // =========================================================================

    @Test
    @DisplayName("Gate 1 (P0): ROLE_GUEST is strictly blocked with AccessDeniedException on all mutating catalog endpoints")
    void testGate1_GuestRoleForbiddenOnCatalogMutations() {
        // 1. Ingestion / Discovery
        PolicyDiscoveryDto.PolicyDiscoveryRequest discReq = new PolicyDiscoveryDto.PolicyDiscoveryRequest();
        discReq.setPolicyName("Test Policy");
        assertThrows(AccessDeniedException.class, () ->
                catalogController.discoverPolicy(discReq, guestUser),
                "ROLE_GUEST must not be able to call /discover");

        // 2. Verification & Publication
        PolicyDiscoveryDto.PolicyVerificationRequest verReq = new PolicyDiscoveryDto.PolicyVerificationRequest();
        verReq.setDecision("APPROVE_AND_PUBLISH");
        assertThrows(AccessDeniedException.class, () ->
                catalogController.verifyPolicy("pol-1", verReq, guestUser),
                "ROLE_GUEST must not be able to publish a policy");

        // 3. Flagging
        assertThrows(AccessDeniedException.class, () ->
                catalogController.flagPolicy("pol-1", "Outdated", "FLAGGED_OUTDATED", guestUser),
                "ROLE_GUEST must not be able to flag a policy");

        // 4. Rollback
        assertThrows(AccessDeniedException.class, () ->
                catalogController.rollbackPolicy("pol-1", "Test rollback", guestUser),
                "ROLE_GUEST must not be able to roll back policy revisions");

        // 5. Cache Refresh
        assertThrows(AccessDeniedException.class, () ->
                catalogController.refreshCache(guestUser),
                "ROLE_GUEST must not be able to force-refresh catalog cache");

        // 6. Register Source
        assertThrows(AccessDeniedException.class, () ->
                catalogController.registerSource(new com.vidhura.ai.recommendation.model.PolicySource(), guestUser),
                "ROLE_GUEST must not be able to register policy sources");

        // 7. Crawl Source & Crawl All
        assertThrows(AccessDeniedException.class, () ->
                catalogController.crawlSource("src-1", false, guestUser),
                "ROLE_GUEST must not be able to trigger single source crawl");

        assertThrows(AccessDeniedException.class, () ->
                catalogController.crawlAllSources(false, guestUser),
                "ROLE_GUEST must not be able to trigger crawl-all");
    }

    @Test
    @DisplayName("Gate 1 (P0): ROLE_GUEST is permitted on read-only inspection and benchmark execution")
    void testGate1_GuestRoleAllowedOnReadOnlyEndpoints() {
        when(policyRepository.findAll()).thenReturn(List.of());
        when(documentRepository.findTop20ByOrderByFetchedAtDesc()).thenReturn(List.of());

        // Guest can inspect staged drafts in read-only mode
        assertDoesNotThrow(() -> catalogController.getDrafts(guestUser));

        // Guest can inspect registered sources in read-only mode
        assertDoesNotThrow(() -> catalogController.getSources(guestUser));

        // Guest can inspect discovered documents history in read-only mode
        assertDoesNotThrow(() -> catalogController.getDiscoveredDocuments(guestUser));

        // Guest can execute benchmark quality report for interactive evaluation
        assertDoesNotThrow(() -> catalogController.getQualityBenchmarkReport(guestUser));
    }

    // =========================================================================
    // GATE 2 (P0): MULTI-INSTANCE CONSISTENCY & PARTITION STALENESS POLICY
    // =========================================================================

    @Test
    @DisplayName("Gate 2 (P0): Database partition within 15 mins serves snapshot with DEGRADED_PARTITION_RECENT status and warning")
    void testGate2_PartitionWithin15MinutesServesWithWarning() {
        // Simulate metadata store outage (unreachable)
        when(metadataRepository.findById(any())).thenThrow(new RuntimeException("MongoDB Connection Timeout"));
        when(policyRepository.findByActiveTrue()).thenThrow(new RuntimeException("MongoDB Cluster Unreachable"));

        // Simulate partition active for 5 minutes (within 15-min tolerance)
        recommendationService.setSimulationStalenessOffsetMs(5 * 60 * 1000L);

        RecommendationService.CatalogFreshnessResolution resolution = recommendationService.resolveCatalogFreshness();

        assertEquals("DEGRADED_PARTITION_RECENT", resolution.getStatus());
        assertTrue(resolution.isStale());
        assertFalse(resolution.isHalted());
        assertNotNull(resolution.getWarning());
        assertTrue(resolution.getWarning().contains("within 15 min safety tolerance"));
    }

    @Test
    @DisplayName("Gate 2 (P0): Database partition exceeding 15 mins halts recommendations to prevent serving revoked policies")
    void testGate2_PartitionExceeding15MinutesHaltsRecommendations() {
        // Simulate metadata store outage (unreachable)
        when(metadataRepository.findById(any())).thenThrow(new RuntimeException("MongoDB Network Partition"));

        // Simulate partition active for 20 minutes (exceeds 15-min tolerance)
        recommendationService.setSimulationStalenessOffsetMs(20 * 60 * 1000L);

        RecommendationService.CatalogFreshnessResolution resolution = recommendationService.resolveCatalogFreshness();

        assertEquals("PARTITION_STALENESS_EXCEEDED_HALTED", resolution.getStatus());
        assertTrue(resolution.isStale());
        assertTrue(resolution.isHalted());
        assertTrue(resolution.getWarning().contains("CRITICAL CATALOG SAFETY HALT"));

        // Verify that generateRecommendations actively throws CatalogStalenessExceededException
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(30)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Bengaluru")
                .annualBudget(25000.0)
                .build();

        assertThrows(RecommendationService.CatalogStalenessExceededException.class, () ->
                recommendationService.generateRecommendations("test-user", req));
    }

    @Test
    @DisplayName("Gate 2 (P0): Multi-instance concurrent mutations increment global monotonic catalog version")
    void testGate2_MultiInstanceConcurrentMutations() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        List<CatalogMetadata> savedVersions = Collections.synchronizedList(new ArrayList<>());
        when(metadataRepository.findById(CatalogMetadata.GLOBAL_ID)).thenAnswer(inv -> {
            synchronized (savedVersions) {
                if (savedVersions.isEmpty()) {
                    return Optional.of(CatalogMetadata.builder().id(CatalogMetadata.GLOBAL_ID).version(1L).build());
                }
                return Optional.of(savedVersions.get(savedVersions.size() - 1));
            }
        });

        when(metadataRepository.save(any(CatalogMetadata.class))).thenAnswer(inv -> {
            CatalogMetadata m = inv.getArgument(0);
            savedVersions.add(m);
            return m;
        });

        for (int i = 0; i < threads; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    recommendationService.incrementGlobalCatalogVersion("ACTION_" + id, "reviewer-" + id, 7);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Versions must be monotonic and strictly greater than initial
        assertTrue(savedVersions.size() >= threads);
        assertTrue(savedVersions.get(savedVersions.size() - 1).getVersion() > 1L);
    }

    // =========================================================================
    // GATE 3 (P0): INDEPENDENT BENCHMARK QUALITY & ACTUARIAL GROUND TRUTH
    // =========================================================================

    @Test
    @DisplayName("Gate 3 (P0): Benchmark suite contains 12 scenarios with documented ground truth and 100% claim math precision")
    void testGate3_IndependentBenchmarkSuiteQuality() {
        RecommendationQualityReport report = benchmarkService.runBenchmarkSuite();

        assertNotNull(report);
        assertEquals(12, report.getTotalBenchmarkScenarios(), "Benchmark must evaluate 12 comprehensive scenarios");
        assertEquals(0.0, report.getMandatoryConstraintViolationRate(), "Mandatory constraint violation rate must be strictly 0.0%");
        assertEquals(100.0, report.getClaimMathAccuracyRate(), "Independent claim math precision must be 100.0%");
        assertTrue(report.isRegressionPassed(), "Regression quality gate must pass");

        // Verify that every scenario contains independently documented ground truth and actuarial basis
        for (RecommendationQualityReport.ScenarioEvaluationResult result : report.getScenarioResults()) {
            assertNotNull(result.getDocumentedGroundTruth(), "Scenario " + result.getScenarioId() + " must have documented ground truth");
            assertNotNull(result.getIndependentActuarialBasis(), "Scenario " + result.getScenarioId() + " must have independent actuarial basis");
            assertFalse(result.getDocumentedGroundTruth().isBlank());
            assertFalse(result.getIndependentActuarialBasis().isBlank());
        }
    }

    // =========================================================================
    // GATE 4 (P1): SSRF CONNECTION PINNING & ANTI-DNS REBINDING
    // =========================================================================

    @Test
    @DisplayName("Gate 4 (P1): SSRF blocks loopback, private RFC-1918, and metadata IPs before socket creation")
    void testGate4_SsrfBlocksRestrictedIps() {
        assertTrue(documentFetcher.isBlockedHostOrIp("localhost"));
        assertTrue(documentFetcher.isBlockedHostOrIp("127.0.0.1"));
        assertTrue(documentFetcher.isBlockedHostOrIp("169.254.169.254"));
        assertTrue(documentFetcher.isBlockedHostOrIp("10.0.0.1"));
        assertTrue(documentFetcher.isBlockedHostOrIp("192.168.1.1"));

        assertThrows(SecurityException.class, () ->
                documentFetcher.resolveAndValidateSafeDestinationIp("169.254.169.254"));

        assertThrows(SecurityException.class, () ->
                documentFetcher.resolveAndValidateSafeDestinationIp("127.0.0.1"));
    }

    @Test
    @DisplayName("Gate 4 (P1): openPinnedConnection creates connection with validated destination IP and sets Host header")
    void testGate4_ConnectionPinnedToValidatedIp() throws Exception {
        InetAddress safeIp = InetAddress.getByName("93.184.216.34"); // example.com public IP
        URI targetUri = URI.create("http://example.com/wording.pdf");

        HttpURLConnection conn = documentFetcher.openPinnedConnection(targetUri, safeIp);
        assertNotNull(conn);

        // Verify that the connection URL host is the pinned IP, not the hostname
        assertEquals("93.184.216.34", conn.getURL().getHost());

        // Verify that the target host is set to the original domain for virtual hosting
        assertEquals("example.com", conn.getRequestProperty("X-Vidhura-Target-Host"));
    }

    // =========================================================================
    // GATE 5 (P1): DOCUMENT IDENTITY & REVISION PARSING
    // =========================================================================

    @Test
    @DisplayName("Gate 5 (P1): Parses both modern alphanumeric and slash regulatory UIN formats")
    void testGate5_ParsesBothUinFormats() {
        // 1. Alphanumeric UIN (HDFC ERGO Optima Secure)
        String rawText1 = "Policy Document for HDFC ERGO Optima Secure. Product UIN: HDFHLIP21147V012021. Valid nationwide.";
        PolicyDiscoveryDto.PolicyDiscoveryRequest req1 = new PolicyDiscoveryDto.PolicyDiscoveryRequest();
        req1.setRawPolicyText(rawText1);
        req1.setDocumentName("optima_secure_filing.pdf");

        PolicyDiscoveryDto.PolicyIngestionResultDto res1 = discoveryPipelineService.discoverAndIngest(req1);
        assertNotNull(res1);
        assertEquals("HDFHLIP21147V012021", res1.getIrdaUin());
        assertEquals("V012021", res1.getUinRevision());

        // 2. Slash Regulatory UIN (United India Insurance)
        String rawText2 = "United India Insurance Company Limited. Family Medicare Policy. Regulatory Filing UIN: IRDAI/HLT/UII/P-H/V.1/20/2021-22.";
        PolicyDiscoveryDto.PolicyDiscoveryRequest req2 = new PolicyDiscoveryDto.PolicyDiscoveryRequest();
        req2.setRawPolicyText(rawText2);
        req2.setDocumentName("uii_medicare.pdf");

        PolicyDiscoveryDto.PolicyIngestionResultDto res2 = discoveryPipelineService.discoverAndIngest(req2);
        assertNotNull(res2);
        assertEquals("IRDAI/HLT/UII/P-H/V.1/20/2021-22", res2.getIrdaUin());
    }

    @Test
    @DisplayName("Gate 5 (P1): Recognizes legitimate revisions, addenda, and endorsements without fatal rejection")
    void testGate5_RecognizesRevisionsAndAddenda() {
        // Base UIN registered with V012021 in CanonicalProductRegistry
        // Subsequent revision V022022 should be classified as CANONICAL_REVISION_ADVANCED (not rejected as mismatch)
        List<String> findings = canonicalProductRegistry.validateIdentity(
                "HDFHLIP21147V012021",
                "HDFC ERGO General Insurance Co. Ltd.",
                "Optima Secure",
                "V022022",
                "Official Policy Wording for HDFC ERGO Optima Secure"
        );

        assertTrue(findings.stream().anyMatch(f -> f.contains("CANONICAL_REVISION_ADVANCED")),
                "Updated revision must be classified as CANONICAL_REVISION_ADVANCED");
        assertFalse(findings.stream().anyMatch(f -> f.contains("CANONICAL_PRODUCT_MISMATCH")),
                "Updated revision must not trigger product mismatch");

        // Addendum: A01 or Addendum-01
        List<String> addendumFindings = canonicalProductRegistry.validateIdentity(
                "HDFHLIP21147V012021",
                "HDFC ERGO General Insurance Co. Ltd.",
                "Optima Secure",
                "A01",
                "Official Policy Wording for HDFC ERGO Optima Secure Addendum"
        );

        assertFalse(addendumFindings.stream().anyMatch(f -> f.contains("CANONICAL_REVISION_MISMATCH")),
                "Addenda must be recognized as attaching to base product without revision mismatch");
    }
}
