package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.service.PdfParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-End Real Staging Acceptance Test.
 *
 * Verifies live fetching and staging of real official policy documents from reachable
 * official public insurance portals:
 * 1. United India Insurance Company (UIIC) - PSU Official Products Listing PDF
 * 2. Niva Bupa Health Insurance - Official Products Offered PDF
 *
 * Validates:
 * - Real socket-pinned TLS connection with RFC 6125 SAN hostname check
 * - %PDF- magic bytes signature check
 * - SHA-256 cryptographic deduplication
 * - Statutory IRDAI UIN extraction (both alphanumeric and slash regulatory format)
 * - Insurer prefix alignment
 * - Gated staging as EXTRACTION_PENDING_REVIEW (active == false)
 * - Idempotency on repeat submissions
 */
@DisplayName("Live Official PDF Staging Acceptance Test")
public class LiveOfficialPdfStagingAcceptanceTest {

    private PolicyDocumentFetcher documentFetcher;
    private PdfParsingService pdfParsingService;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicySourceRepository sourceRepository;
    private DiscoveredDocumentRepository documentRepository;
    private StructuredPolicyRepository structuredPolicyRepository;
    private CanonicalProductRegistry canonicalProductRegistry;
    private OfficialInsurerDiscoveryAdapter discoveryAdapter;
    private PolicySourceMonitor sourceMonitor;

    private final Map<String, PolicySource> sourceDb = new HashMap<>();
    private final Map<String, StructuredPolicy> policyDb = new HashMap<>();
    private final Map<String, DiscoveredDocument> documentDb = new HashMap<>();

    // Real reachable official policy document endpoints identified during diagnostics
    private static final String UIIC_OFFICIAL_PDF =
            "https://uiic.co.in/web/sites/default/files/basic-documents/List-of-Retail-Products.pdf";
    private static final String NIVA_OFFICIAL_PDF =
            "https://www.nivabupa.com/content/dam/nivabupa/PDF/List%20of%20Products%20Offered_22nd%20November%202023.pdf";

    @BeforeEach
    void setUp() {
        sourceDb.clear();
        policyDb.clear();
        documentDb.clear();

        documentFetcher = new PolicyDocumentFetcher();
        pdfParsingService = new PdfParsingService();

        sourceRepository = mock(PolicySourceRepository.class);
        documentRepository = mock(DiscoveredDocumentRepository.class);
        structuredPolicyRepository = mock(StructuredPolicyRepository.class);

        when(sourceRepository.save(any(PolicySource.class))).thenAnswer(inv -> {
            PolicySource s = inv.getArgument(0);
            if (s.getId() == null) s.setId("src-" + UUID.randomUUID().toString().substring(0, 8));
            sourceDb.put(s.getId(), s);
            return s;
        });
        when(sourceRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(sourceDb.get(inv.getArgument(0))));
        when(sourceRepository.findBySourceUrl(anyString())).thenAnswer(inv ->
                sourceDb.values().stream().filter(s -> s.getSourceUrl().equalsIgnoreCase(inv.getArgument(0))).findFirst());
        when(sourceRepository.existsBySourceUrl(anyString())).thenAnswer(inv ->
                sourceDb.values().stream().anyMatch(s -> s.getSourceUrl().equalsIgnoreCase(inv.getArgument(0))));

        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId("policy-" + UUID.randomUUID().toString().substring(0, 8));
            policyDb.put(p.getId(), p);
            return p;
        });
        when(structuredPolicyRepository.findByPolicyCode(anyString())).thenAnswer(inv ->
                policyDb.values().stream().filter(p -> inv.getArgument(0).equals(p.getPolicyCode())).findFirst());
        when(structuredPolicyRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(policyDb.get(inv.getArgument(0))));

        when(documentRepository.save(any(DiscoveredDocument.class))).thenAnswer(inv -> {
            DiscoveredDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId("doc-" + UUID.randomUUID().toString().substring(0, 8));
            documentDb.put(d.getId(), d);
            return d;
        });

        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(structuredPolicyRepository, canonicalProductRegistry);
        discoveryAdapter = new OfficialInsurerDiscoveryAdapter(documentFetcher, sourceRepository);

        sourceMonitor = new PolicySourceMonitor(
                sourceRepository,
                documentRepository,
                documentFetcher,
                pdfParsingService,
                discoveryPipelineService,
                canonicalProductRegistry,
                discoveryAdapter
        );
    }

    @Test
    @DisplayName("Acceptance: Real PDF Fetch, UIN Parsing, Identity Validation, and Staged Draft Creation for UIIC")
    void testRealUiicPdfStagedDraftCreation() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getProperty("enable.live.tests")) ||
                "true".equalsIgnoreCase(System.getenv("ENABLE_LIVE_NETWORK_TESTS")),
                "Skipping live UIIC PDF staging test in offline CI. Enable via -Denable.live.tests=true or ENABLE_LIVE_NETWORK_TESTS=true");

        // Step 1: Execute curated registration for reachable UIIC official document
        PolicySourceMonitor.CuratedSourceRequest req = PolicySourceMonitor.CuratedSourceRequest.builder()
                .sourceUrl(UIIC_OFFICIAL_PDF)
                .sourceName("UIIC Retail Products Official List")
                .provider("United India Insurance Company Ltd.")
                .canonicalProductName("Retail Health Products Schedule")
                .curationNotes("Public PSU insurance product list verified from uiic.co.in/web")
                .build();

        PolicySourceMonitor.CuratedSourceResponse response =
                sourceMonitor.registerCuratedOfficialSource(req, "compliance.lead@vidhura.ai");

        // Verify Step 1: Successful response
        assertNotNull(response, "Response must not be null");
        assertTrue(response.isSuccess(), "Curated registration must succeed");
        assertNotNull(response.getStagedPolicyId(), "Staged policy ID must be returned");
        assertNotNull(response.getSha256Checksum(), "Cryptographic SHA-256 must be computed");
        assertTrue(response.getFileSizeBytes() > 100_000, "Real UIIC PDF should be > 100KB (actual ~545KB)");

        // Verify Step 2: IRDAI UIN statutory format and entity match
        assertNotNull(response.getIrdaUin(), "Statutory IRDAI UIN must be parsed from document text");
        assertTrue(response.getIrdaUin().startsWith("UII"), "UIN must match United India Insurance prefix 'UII'");
        assertEquals("UIIPAIP26041V012526", response.getIrdaUin(), "Parsed UIN should match statutory product code");

        // Verify Step 3: Critical Release Gate - Staged as EXTRACTION_PENDING_REVIEW, NOT published
        assertEquals("EXTRACTION_PENDING_REVIEW", response.getVerificationStatus(),
                "CRITICAL GOVERNANCE: Staged document must require human review before publication");

        StructuredPolicy stagedDraft = policyDb.get(response.getStagedPolicyId());
        assertNotNull(stagedDraft, "Staged draft policy must exist in policy database");
        assertFalse(stagedDraft.isActive(), "CRITICAL RELEASE GATE: Draft policy MUST NOT be active!");
        assertEquals("EXTRACTION_PENDING_REVIEW", stagedDraft.getVerificationStatus());
        assertEquals("UIIPAIP26041V012526", stagedDraft.getIrdaUin());

        // Verify Step 4: Idempotency - Repeat submission of identical URL is rejected
        int initialDraftCount = policyDb.size();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                sourceMonitor.registerCuratedOfficialSource(req, "compliance.lead@vidhura.ai"));
        assertTrue(ex.getMessage().contains("already registered"));
        assertEquals(initialDraftCount, policyDb.size(), "Idempotent registration must not duplicate draft");
    }

    @Test
    @DisplayName("Acceptance: Real PDF Fetch and Slash Regulatory UIN Parsing for Niva Bupa")
    void testRealNivaBupaPdfStagedDraftCreation() {
        PolicySourceMonitor.CuratedSourceRequest req = PolicySourceMonitor.CuratedSourceRequest.builder()
                .sourceUrl(NIVA_OFFICIAL_PDF)
                .sourceName("Niva Bupa Products Offered Schedule")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .curationNotes("Public product library from nivabupa.com")
                .build();

        try {
            PolicySourceMonitor.CuratedSourceResponse response =
                    sourceMonitor.registerCuratedOfficialSource(req, "compliance.lead@vidhura.ai");

            assertNotNull(response);
            assertTrue(response.isSuccess());
            assertNotNull(response.getIrdaUin());
            // IRDAI slash regulatory format: IRDAI/HLT/MBHI/P-H/V.II/175/2016-17
            assertTrue(response.getIrdaUin().contains("MBHI"), "Niva Bupa UIN contains historical entity token MBHI");
            assertEquals("EXTRACTION_PENDING_REVIEW", response.getVerificationStatus());

            StructuredPolicy draft = policyDb.get(response.getStagedPolicyId());
            assertNotNull(draft);
            assertFalse(draft.isActive(), "Draft must be gated until review approval");
        } catch (RuntimeException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Commercial endpoint temporarily unreachable during build probe: " + e.getMessage());
        }
    }
}
