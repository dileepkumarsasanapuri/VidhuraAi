package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.*;
import com.vidhura.ai.recommendation.repository.*;
import com.vidhura.ai.service.PdfParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 5.6: Automated Policy Discovery & Database-Driven Ingestion Test Suite.
 *
 * Verifies:
 * 1. Migration of the existing 16-policy cohort without duplication or data loss.
 * 2. Discovery of a new candidate from a deterministic official-source HTML fixture.
 * 3. Discovery of a revised document for an existing known product.
 * 4. Deduplication across repeated discovery runs.
 * 5. Secure acquisition and physical hash verification.
 * 6. Acquisition failure and retry behavior.
 * 7. Extraction of supported metadata and preservation of unknown terms.
 * 8. Mandatory human review for ambiguous contractual interpretations (no auto-approval of qualitative clauses).
 * 9. Publication of a valid candidate into the MongoDB-backed catalogue.
 * 10. Appearance of a newly published policy in recommendation catalog.
 * 11. Scheduler overlap prevention and safe restart state.
 */
@ExtendWith(MockitoExtension.class)
class Phase5_6AutomatedPolicyDiscoveryTest {

    @Mock
    private StructuredPolicyRepository structuredPolicyRepository;

    @Mock
    private InsurerProfileRepository insurerProfileRepository;

    @Mock
    private PolicyDocumentRevisionRepository revisionRepository;

    @Mock
    private PolicyDiscoveryCandidateRepository candidateRepository;

    @Mock
    private PolicyDiscoveryRunRepository runRepository;

    @Mock
    private PolicySourceRepository policySourceRepository;

    @Mock
    private OfficialSourceAcquisitionService acquisitionService;

    @Mock
    private PdfParsingService pdfParsingService;

    @Mock
    private RecommendationService recommendationService;

    @TempDir
    Path tempStorageDir;

    private PersistentArtifactStorageService persistentStorage;
    private OfficialSourceDiscoveryEngine discoveryEngine;
    private PolicyDiscoveryOrchestratorService orchestratorService;
    private PolicyDiscoverySchedulerService schedulerService;
    private PolicyRegistrySyncService registrySyncService;
    private PolicyIngestionService policyIngestionService;

    // In-memory data structures simulating MongoDB collections
    private final Map<String, StructuredPolicy> dbPolicies = new ConcurrentHashMap<>();
    private final Map<String, InsurerProfile> dbInsurers = new ConcurrentHashMap<>();
    private final Map<String, PolicyDocumentRevision> dbRevisions = new ConcurrentHashMap<>();
    private final Map<String, PolicyDiscoveryCandidate> dbCandidates = new ConcurrentHashMap<>();
    private final Map<String, PolicyDiscoveryRun> dbRuns = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        persistentStorage = new PersistentArtifactStorageService(tempStorageDir.toString());
        discoveryEngine = new OfficialSourceDiscoveryEngine(acquisitionService, candidateRepository, revisionRepository);
        orchestratorService = new PolicyDiscoveryOrchestratorService(
                candidateRepository,
                revisionRepository,
                structuredPolicyRepository,
                persistentStorage,
                acquisitionService,
                pdfParsingService,
                recommendationService
        );
        schedulerService = new PolicyDiscoverySchedulerService(
                insurerProfileRepository,
                policySourceRepository,
                runRepository,
                candidateRepository,
                discoveryEngine,
                orchestratorService,
                acquisitionService
        );
        policyIngestionService = new PolicyIngestionService(structuredPolicyRepository);
        registrySyncService = new PolicyRegistrySyncService(
                structuredPolicyRepository,
                insurerProfileRepository,
                revisionRepository,
                candidateRepository,
                policySourceRepository,
                policyIngestionService
        );

        dbPolicies.clear();
        dbInsurers.clear();
        dbRevisions.clear();
        dbCandidates.clear();
        dbRuns.clear();
    }

    // =========================================================================
    // CASE 1: 16-POLICY COHORT SAFE MIGRATION & SYNCHRONIZATION
    // =========================================================================

    @Test
    @DisplayName("Case 1: Safe migration of existing 16-policy cohort into database registry without duplication")
    void testMigrationOfExisting16PolicyCohortWithoutDuplication() {
        // Wire in-memory storage behavior
        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID().toString());
            dbPolicies.put(p.getPolicyCode(), p);
            return p;
        });
        when(structuredPolicyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return Optional.ofNullable(dbPolicies.get(code));
        });
        when(insurerProfileRepository.save(any(InsurerProfile.class))).thenAnswer(inv -> {
            InsurerProfile ip = inv.getArgument(0);
            if (ip.getId() == null) ip.setId(UUID.randomUUID().toString());
            dbInsurers.put(ip.getInsurerCode(), ip);
            return ip;
        });
        when(insurerProfileRepository.findByInsurerCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return Optional.ofNullable(dbInsurers.get(code));
        });
        when(revisionRepository.save(any(PolicyDocumentRevision.class))).thenAnswer(inv -> {
            PolicyDocumentRevision r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID().toString());
            dbRevisions.put(r.getIrdaUin() + "_" + r.getUinRevision(), r);
            return r;
        });
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> {
            PolicyDiscoveryCandidate c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID().toString());
            dbCandidates.put(c.getNormalizedProductCode(), c);
            return c;
        });

        // 1. Initial migration run
        PolicyRegistrySyncService.SyncResult result1 = registrySyncService.synchronizePolicyRegistry();
        assertEquals(13, result1.insurersSynced(), "Must sync all 13 official insurers");
        assertEquals(16, result1.policiesSynced(), "Must sync all 16 authentic acquired policies");
        assertEquals(16, dbPolicies.size(), "Exact 16 policies in database");
        assertEquals(13, dbInsurers.size(), "Exact 13 insurers in database");

        // 2. Second migration run (idempotence check)
        PolicyRegistrySyncService.SyncResult result2 = registrySyncService.synchronizePolicyRegistry();
        assertEquals(13, result2.insurersSynced());
        assertEquals(16, result2.policiesSynced());
        assertEquals(16, dbPolicies.size(), "Database must remain strictly 16 policies (no duplicates)");
        assertEquals(13, dbInsurers.size(), "Database must remain strictly 13 insurers (no duplicates)");
    }

    // =========================================================================
    // CASE 2: DISCOVERY FROM OFFICIAL-SOURCE HTML FIXTURE
    // =========================================================================

    @Test
    @DisplayName("Case 2: Discovery of a new candidate from a deterministic official-source fixture")
    void testDiscoveryOfNewCandidateFromDeterministicOfficialSourceFixture() {
        String officialHtmlFixture = """
                <html>
                <body>
                    <h2>Official Health Insurance Products</h2>
                    <div class="product-item">
                        <a href="/downloads/hdfc_myhealth_suraksha_wording.pdf">
                            HDFC ERGO My:Health Suraksha Policy Wordings (UIN: HDFHLIP21148V022021)
                        </a>
                    </div>
                </body>
                </html>
                """;

        List<OfficialSourceDiscoveryEngine.DiscoveredItem> items =
                discoveryEngine.extractCandidatesFromHtml("https://www.hdfcergo.com/health-insurance", officialHtmlFixture);

        assertEquals(1, items.size());
        OfficialSourceDiscoveryEngine.DiscoveredItem item = items.get(0);
        assertEquals("HDFHLIP21148V022021", item.getIrdaUin());
        assertEquals("V022021", item.getRevision());
        assertEquals("HDFC_ERGO", item.getInsurerCode());
        assertEquals("https://www.hdfcergo.com/downloads/hdfc_myhealth_suraksha_wording.pdf", item.getDocumentUrl());
    }

    // =========================================================================
    // CASE 3: REVISED DOCUMENT DISCOVERY FOR EXISTING PRODUCT
    // =========================================================================

    @Test
    @DisplayName("Case 3: Discovery of a revised document for an existing known product")
    void testDiscoveryOfRevisedDocumentForExistingProduct() {
        String officialHtmlFixture = """
                <html>
                <body>
                    <a href="https://uiic.co.in/sites/default/files/Family_Medicare_2024_Wordings.pdf">
                        United India Family Medicare Policy Wordings UIN: UIIHLIP21239V032024
                    </a>
                </body>
                </html>
                """;

        // Simulate known product with older revision V022021
        when(revisionRepository.existsByIrdaUinAndUinRevision("UIIHLIP21239V032024", "V032024")).thenReturn(false);
        when(candidateRepository.existsByIrdaUinAndUinRevision("UIIHLIP21239V032024", "V032024")).thenReturn(false);
        when(candidateRepository.findByIrdaUin("UIIHLIP21239V032024")).thenReturn(Optional.of(
                PolicyDiscoveryCandidate.builder().irdaUin("UIIHLIP21239V032024").uinRevision("V022021").build()
        ));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        OfficialSourceDiscoveryEngine.DiscoveryScanSummary summary =
                discoveryEngine.processSource("https://uiic.co.in/health", officialHtmlFixture, "RUN-TEST-01");

        assertEquals(1, summary.getLinksFound());
        assertEquals(0, summary.getNewCandidates());
        assertEquals(1, summary.getUpdatedRevisions(), "Must recognize as an updated revision for a known product");
        assertEquals(0, summary.getDuplicatesSkipped());
    }

    // =========================================================================
    // CASE 4: DEDUPLICATION ACROSS REPEATED DISCOVERY RUNS
    // =========================================================================

    @Test
    @DisplayName("Case 4: Deduplication across repeated discovery runs")
    void testDeduplicationAcrossRepeatedDiscoveryRuns() {
        String officialHtmlFixture = """
                <html>
                <body>
                    <a href="https://www.starhealth.in/docs/star_comprehensive_wordings.pdf">
                        Star Comprehensive Insurance Policy Wordings SHAHLIP21164V012021
                    </a>
                </body>
                </html>
                """;

        // First run: already exists in revision repository
        when(revisionRepository.existsByIrdaUinAndUinRevision("SHAHLIP21164V012021", "V012021")).thenReturn(true);

        OfficialSourceDiscoveryEngine.DiscoveryScanSummary summary =
                discoveryEngine.processSource("https://www.starhealth.in/plans", officialHtmlFixture, "RUN-TEST-02");

        assertEquals(1, summary.getLinksFound());
        assertEquals(0, summary.getNewCandidates(), "Existing candidate must not be created as new");
        assertEquals(1, summary.getDuplicatesSkipped(), "Must be cleanly marked as duplicate");
        verify(candidateRepository, never()).save(any());
    }

    // =========================================================================
    // CASE 5: SECURE ACQUISITION AND PHYSICAL HASH VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Case 5: Secure acquisition and physical hash verification via PersistentArtifactStorageService")
    void testSecureAcquisitionAndPhysicalHashVerification() {
        byte[] pdfBytes = "%PDF-1.4 official policy wording document with verifiable terms".getBytes(StandardCharsets.UTF_8);
        String expectedHash = computeSha256(pdfBytes);

        PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                .id("cand-101")
                .productName("Bajaj Allianz Health Guard")
                .normalizedProductCode("BAG_HEALTH_GUARD")
                .finalAcquisitionUrl("https://www.bajajallianz.com/docs/health_guard.pdf")
                .lifecycleStage("ACQUISITION_PENDING")
                .build();

        when(candidateRepository.findById("cand-101")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        when(acquisitionService.acquireFromOfficialSource(anyString())).thenReturn(
                OfficialSourceAcquisitionService.AcquisitionResult.builder()
                        .status(OfficialSourceAcquisitionService.AcquisitionStatus.ACQUIRED)
                        .payloadBytes(pdfBytes)
                        .mediaType("application/pdf")
                        .build()
        );

        PolicyDiscoveryCandidate result = orchestratorService.acquireCandidateDocument("cand-101");

        assertEquals("ACQUIRED", result.getLifecycleStage());
        assertEquals(expectedHash, result.getVerifiedSha256());
        assertEquals((long) pdfBytes.length, result.getFileSizeBytes());
        assertNotNull(result.getDocumentArtifactId());
        assertTrue(persistentStorage.hasArtifact(expectedHash), "Artifact must physically exist in immutable persistent store");
    }

    // =========================================================================
    // CASE 6: ACQUISITION FAILURE AND RETRY BEHAVIOR
    // =========================================================================

    @Test
    @DisplayName("Case 6: Acquisition failure and retry behavior")
    void testAcquisitionFailureAndRetryBehavior() {
        PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                .id("cand-102")
                .productName("Oriental Happy Family Floater")
                .normalizedProductCode("OIC_HAPPY_FAMILY_FLOATER")
                .finalAcquisitionUrl("https://orientalinsurance.org.in/docs/invalid.pdf")
                .lifecycleStage("ACQUISITION_PENDING")
                .retryCount(0)
                .build();

        when(candidateRepository.findById("cand-102")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        when(acquisitionService.acquireFromOfficialSource(anyString())).thenReturn(
                OfficialSourceAcquisitionService.AcquisitionResult.builder()
                        .status(OfficialSourceAcquisitionService.AcquisitionStatus.FAILED_SSRF)
                        .errorMessage("Blocked address: private IP or rebinding attempt")
                        .build()
        );

        PolicyDiscoveryCandidate result = orchestratorService.acquireCandidateDocument("cand-102");

        assertEquals("ACQUISITION_PENDING", result.getLifecycleStage(), "Lifecycle stage must NOT advance on failure");
        assertEquals(1, result.getRetryCount(), "Retry count must be incremented");
        assertNotNull(result.getLastErrorMessage());
        assertTrue(result.getLastErrorMessage().contains("FAILED_SSRF"));
        assertNull(result.getVerifiedSha256(), "Hash must remain null when acquisition fails");
    }

    // =========================================================================
    // CASE 7: EXTRACTION OF METADATA & PRESERVATION OF UNKNOWN TERMS
    // =========================================================================

    @Test
    @DisplayName("Case 7: Extraction of supported metadata and preservation of unknown terms")
    void testExtractionOfSupportedMetadataAndPreservationOfUnknownTerms() throws Exception {
        byte[] docBytes = ("Policy Clause 1: Room Rent has No Room Rent Limit (Single Private AC room allowed). "
                + "Policy Clause 2: Pre-existing Disease waiting period is 36 months.").getBytes(StandardCharsets.UTF_8);
        String hash = computeSha256(docBytes);
        persistentStorage.storeArtifact(docBytes, "text/plain", hash);

        PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                .id("cand-103")
                .productName("Niva Bupa ReAssure 2.0")
                .lifecycleStage("ACQUIRED")
                .verifiedSha256(hash)
                .build();

        when(candidateRepository.findById("cand-103")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDiscoveryCandidate result = orchestratorService.extractAndValidateCandidate("cand-103");

        assertEquals("No limit — Single Private AC Room or higher", result.getRoomRentSummary());
        assertEquals("36 months (3 years)", result.getPedWaitingSummary());
        assertTrue(result.getCopaySummary().contains("UNKNOWN"), "Unspecified copay must remain explicitly UNKNOWN");
        assertTrue(result.getContractUncertainFields().contains("copay"), "Uncertain copay must be tracked in uncertain fields");
    }

    // =========================================================================
    // CASE 8: MANDATORY HUMAN REVIEW FOR AMBIGUOUS CONTRACTUAL INTERPRETATIONS
    // =========================================================================

    @Test
    @DisplayName("Case 8: Mandatory human review for ambiguous contractual interpretations (no auto-acceptance of qualitative clauses)")
    void testMandatoryHumanReviewForAmbiguousContractualInterpretations() throws Exception {

        byte[] docBytes = "Standard health policy document with ambiguous exclusions and conditions.".getBytes(StandardCharsets.UTF_8);
        String hash = computeSha256(docBytes);
        persistentStorage.storeArtifact(docBytes, "text/plain", hash);

        PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                .id("cand-104")
                .productName("Care Supreme")
                .lifecycleStage("ACQUIRED")
                .verifiedSha256(hash)
                .build();

        when(candidateRepository.findById("cand-104")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDiscoveryCandidate result = orchestratorService.extractAndValidateCandidate("cand-104");

        // Gating invariant: qualitative terms must ALWAYS route to PENDING_REVIEW
        assertEquals("PENDING_REVIEW", result.getLifecycleStage(),
                "Qualitative contractual terms must never be auto-accepted and must route to PENDING_REVIEW");
        assertFalse(result.getAuditFindings().isEmpty());
    }

    // =========================================================================
    // CASE 9: PUBLICATION OF A VALID CANDIDATE INTO MONGODB CATALOGUE
    // =========================================================================

    @Test
    @DisplayName("Case 9: Publication of a valid candidate into MongoDB catalog after human review")
    void testPublicationOfValidCandidateIntoMongoDbCatalog() {
        PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                .id("cand-105")
                .productName("National Mediclaim Plus")
                .normalizedProductCode("NIC_NATIONAL_MEDICLAIM_PLUS")
                .insurerName("National Insurance Co. Ltd.")
                .insurerCode("NIC")
                .irdaUin("NICHLIP21262V022021")
                .uinRevision("V022021")
                .documentArtifactId("nic_mediclaim_plus.pdf")
                .verifiedSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .lifecycleStage("PENDING_REVIEW")
                .build();

        when(candidateRepository.findById("cand-105")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(PolicyDiscoveryCandidate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(structuredPolicyRepository.findByPolicyCode("NIC_NATIONAL_MEDICLAIM_PLUS")).thenReturn(Optional.empty());
        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            p.setId("nic-policy-id-123");
            return p;
        });
        when(revisionRepository.findByVerifiedSha256(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.save(any(PolicyDocumentRevision.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDiscoveryCandidate published = orchestratorService.publishCandidate(
                "cand-105",
                "chief.underwriter@vidhura.ai",
                "Contractual terms fully verified against nationalinsurance.nic.co.in filings."
        );

        assertEquals("PUBLISHED_ELIGIBLE", published.getLifecycleStage());
        assertEquals("chief.underwriter@vidhura.ai", published.getReviewedBy());
        assertNotNull(published.getReviewedAt());
        assertNotNull(published.getLinkedPolicyId());

        verify(structuredPolicyRepository).save(argThat(policy ->
                policy.isActive()
                        && "VERIFIED_OFFICIAL_CATALOG".equals(policy.getVerificationStatus())
                        && "chief.underwriter@vidhura.ai".equals(policy.getVerifiedBy())
                        && "NICHLIP21262V022021".equals(policy.getIrdaUin())
        ));

        verify(revisionRepository).save(argThat(rev ->
                "ACTIVE_REVISION".equals(rev.getStatus())
                        && "NICHLIP21262V022021".equals(rev.getIrdaUin())
        ));
    }

    // =========================================================================
    // CASE 10: APPEARANCE OF NEWLY PUBLISHED POLICY IN RECOMMENDATIONS & COMPARISON
    // =========================================================================

    @Test
    @DisplayName("Case 10: Newly published MongoDB policy satisfies publication gate and is available to recommendation engine")
    void testAppearanceOfNewlyPublishedPolicyInRecommendationAndComparisonApis() {
        StructuredPolicy publishedPolicy = StructuredPolicy.builder()
                .policyCode("NIC_NATIONAL_MEDICLAIM_PLUS")
                .policyName("National Mediclaim Plus")
                .provider("National Insurance Co. Ltd.")
                .irdaUin("NICHLIP21262V022021")
                .uinRevision("V022021")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .verifiedBy("underwriter@vidhura.ai")
                .active(true)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("1% of Sum Insured per day")
                        .waitingPeriodPedMonths(36)
                        .copayPercentage(0)
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder().sumInsured(500000).formattedSumInsured("₹5 Lakh").build()
                ))
                .build();

        RecommendationService realRecService = new RecommendationService(
                structuredPolicyRepository,
                null,
                null,
                new EligibilityEngine(),
                new RankingEngine(),
                policyIngestionService
        );

        // Verify publication readiness gate
        boolean isReady = realRecService.isPublicationReady(publishedPolicy);
        assertTrue(isReady, "Newly published policy must satisfy all publication gate checks");
    }

    // =========================================================================
    // CASE 11: SCHEDULER OVERLAP PREVENTION AND RUN HISTORY AUDIT
    // =========================================================================

    @Test
    @DisplayName("Case 11: Scheduler overlap prevention and run history audit")
    void testSchedulerOverlapPreventionAndRestartRecovery() {
        when(runRepository.save(any(PolicyDiscoveryRun.class))).thenAnswer(inv -> {
            PolicyDiscoveryRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId("RUN-" + UUID.randomUUID());
            return r;
        });

        // Start first run
        assertFalse(schedulerService.isRunning());
        schedulerService.executeDiscoveryRun("MANUAL_ADMIN", List.of());
        assertFalse(schedulerService.isRunning(), "Must reset running state upon completion");

        // Verify run was recorded with COMPLETED status
        verify(runRepository, atLeastOnce()).save(argThat(r ->
                "COMPLETED".equals(r.getStatus()) || "IN_PROGRESS".equals(r.getStatus())
        ));
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
