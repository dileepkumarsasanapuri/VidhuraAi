package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression Test Suite for Policy Discovery and Idempotent Crawl Flow:
 * - Genuinely new official policy URL discovery & staging
 * - Unchanged document skipping via SHA-256 and HTTP 304
 * - Changed document content creating new revisions
 * - Duplicate candidate rejection
 * - Untrusted domain & SSRF-blocked URL rejection
 * - Failed extraction error containment and observability
 * - Empty discovery idempotency
 */
@DisplayName("Policy Discovery and Crawl Regression Tests")
class PolicyDiscoveryCrawlTest {

    private PolicySourceRepository sourceRepository;
    private DiscoveredDocumentRepository documentRepository;
    private PolicyDocumentFetcher documentFetcher;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private StructuredPolicyRepository structuredPolicyRepository;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicySourceMonitor sourceMonitor;

    private List<PolicySource> storedSources;
    private List<StructuredPolicy> storedPolicies;
    private List<DiscoveredDocument> storedDocuments;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(PolicySourceRepository.class);
        documentRepository = mock(DiscoveredDocumentRepository.class);
        documentFetcher = mock(PolicyDocumentFetcher.class);
        structuredPolicyRepository = mock(StructuredPolicyRepository.class);

        storedSources = new ArrayList<>();
        storedPolicies = new ArrayList<>();
        storedDocuments = new ArrayList<>();

        when(sourceRepository.findAll()).thenAnswer(inv -> new ArrayList<>(storedSources));
        when(sourceRepository.findByActiveTrue()).thenAnswer(inv ->
                storedSources.stream().filter(PolicySource::isActive).toList());
        when(sourceRepository.existsBySourceUrl(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return storedSources.stream().anyMatch(s -> url.equalsIgnoreCase(s.getSourceUrl()));
        });
        when(sourceRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return storedSources.stream().filter(s -> id.equals(s.getId())).findFirst();
        });
        when(sourceRepository.save(any(PolicySource.class))).thenAnswer(inv -> {
            PolicySource s = inv.getArgument(0);
            storedSources.removeIf(existing -> existing.getId() != null && existing.getId().equals(s.getId()));
            storedSources.add(s);
            return s;
        });

        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId("policy-" + UUID.randomUUID().toString().substring(0, 8));
            storedPolicies.removeIf(existing -> existing.getPolicyCode() != null && existing.getPolicyCode().equals(p.getPolicyCode()));
            storedPolicies.add(p);
            return p;
        });
        when(structuredPolicyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return storedPolicies.stream().filter(p -> code.equals(p.getPolicyCode())).findFirst();
        });
        when(structuredPolicyRepository.findAll()).thenAnswer(inv -> new ArrayList<>(storedPolicies));

        when(documentRepository.save(any(DiscoveredDocument.class))).thenAnswer(inv -> {
            DiscoveredDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId("doc-" + UUID.randomUUID().toString().substring(0, 8));
            storedDocuments.add(d);
            return d;
        });

        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(structuredPolicyRepository, canonicalProductRegistry);

        sourceMonitor = new PolicySourceMonitor(
                sourceRepository,
                documentRepository,
                documentFetcher,
                null,
                discoveryPipelineService,
                canonicalProductRegistry
        );
    }

    @Test
    @DisplayName("Stage 1 & 2: Discover and stage genuinely new policy when available, then remain idempotent on second crawl")
    void testDiscoverAndStageGenuinelyNewPolicyAndIdempotency() {
        // Arrange: Start with 1 registered source
        PolicySource initialSource = PolicySource.builder()
                .id("src-seed-1")
                .sourceName("Existing Star Health")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .canonicalProductName("Star Comprehensive Insurance Policy")
                .irdaUin("SHAHLIP21163V012021")
                .sourceUrl("https://www.starhealth.in/sites/default/files/star-comprehensive.pdf")
                .active(true)
                .lastSha256Checksum("seed-hash-111")
                .build();
        storedSources.add(initialSource);

        // When fetching the seed source, it returns 304 NOT_MODIFIED (unchanged)
        when(documentFetcher.fetchDocument(eq(initialSource.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .sha256Checksum("seed-hash-111")
                        .build());

        // For newly discovered candidate (e.g. Tata AIG Medicare)
        String newPdfText = "Tata AIG General Insurance Co. Ltd.\n"
                + "Product Name: Medicare\n"
                + "IRDAI UIN: TATHLIP21169V012021\n"
                + "Single private AC room without restriction.\n"
                + "Waiting period for pre-existing diseases: 36 months.\n"
                + "Co-payment: 0%.\n";
        byte[] newPdfBytes = newPdfText.getBytes(StandardCharsets.UTF_8);
        String newPdfChecksum = PolicyDocumentFetcher.computeSha256(newPdfBytes);

        when(documentFetcher.fetchDocument(contains("tataaig.com"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(newPdfBytes)
                        .sha256Checksum(newPdfChecksum)
                        .fileSizeBytes(newPdfBytes.length)
                        .etag("\"etag-tata-1\"")
                        .build());

        // Act 1: First Two-Stage Crawl with Discovery Enabled
        PolicySourceMonitor.CrawlSummary crawl1 = sourceMonitor.executeTwoStageDiscoveryAndCrawl(true, false);

        // Assert 1: New official sources discovered and registered
        assertTrue(crawl1.getCandidateSourcesRegistered() > 0, "Should register new official candidate sources");
        assertEquals(1, crawl1.getNewDocumentsExtracted(), "Should extract exactly 1 new document");
        assertEquals(1, crawl1.getDraftsCreated(), "Should create 1 staged draft");

        // Verify draft policy is staged with active=false (human review gate)
        Optional<StructuredPolicy> tataDraft = storedPolicies.stream()
                .filter(p -> "TATHLIP21169V012021".equals(p.getIrdaUin()))
                .findFirst();
        assertTrue(tataDraft.isPresent(), "Newly discovered policy draft must be created in repository");
        assertFalse(tataDraft.get().isActive(), "Newly discovered draft policy must be inactive until reviewer approves");
        assertEquals("EXTRACTION_PENDING_REVIEW", tataDraft.get().getVerificationStatus());

        // Act 2: Second Crawl immediately following (both seed and tataaig now have stored hashes)
        when(documentFetcher.fetchDocument(contains("tataaig.com"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .sha256Checksum(newPdfChecksum)
                        .build());

        PolicySourceMonitor.CrawlSummary crawl2 = sourceMonitor.executeTwoStageDiscoveryAndCrawl(true, false);

        // Assert 2: Second crawl must be completely idempotent
        assertEquals(0, crawl2.getCandidateSourcesRegistered(), "No duplicate sources should be registered on repeated crawl");
        assertEquals(0, crawl2.getNewDocumentsExtracted(), "No new extractions on unchanged documents");
        assertTrue(crawl2.getUnchangedDocumentsSkipped() >= 2, "Unchanged documents must be skipped via deduplication");
    }

    @Test
    @DisplayName("Should reject candidate sources from untrusted domains or SSRF-blocked private IPs")
    void testRejectUntrustedDomainAndBlockedUrl() {
        when(documentFetcher.isBlockedHostOrIp("169.254.169.254")).thenReturn(true);
        when(documentFetcher.isBlockedHostOrIp("localhost")).thenReturn(true);

        List<String> candidateUrls = List.of(
                "http://malicious-insurance-aggregator.xyz/fake-policy.pdf",
                "https://untrusted-blog.com/policy-wordings.pdf",
                "http://169.254.169.254/latest/meta-data/",
                "ftp://www.starhealth.in/unsupported-protocol.pdf"
        );

        PolicySourceMonitor.DiscoveryReport report = sourceMonitor.discoverNewCandidateSources(candidateUrls);

        // Untrusted / malformed / unsupported URLs must be rejected
        assertTrue(report.getCandidateSourcesRejected() >= 4);
        Map<String, Integer> reasons = report.getRejectionReasons();
        assertTrue(reasons.keySet().stream().anyMatch(k -> k.contains("UNTRUSTED_DOMAIN") || k.contains("SSRF") || k.contains("UNSUPPORTED_SCHEME")));
    }

    @Test
    @DisplayName("Should detect modified document content and stage as new revision")
    void testChangedDocumentContentTriggersNewRevision() {
        // Arrange existing policy in repo
        StructuredPolicy existingPolicy = StructuredPolicy.builder()
                .policyCode("HDFC-OPTIMA-RESTORE-V01")
                .policyName("Optima Restore")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21235V012021")
                .uinRevision("V012021")
                .active(true)
                .verificationStatus("VERIFIED")
                .build();
        storedPolicies.add(existingPolicy);

        PolicySource source = PolicySource.builder()
                .id("src-hdfc")
                .sourceName("HDFC Optima Source")
                .sourceUrl("https://www.hdfcergo.com/optima-restore.pdf")
                .lastSha256Checksum("old-checksum-abc")
                .irdaUin("HDFHLIP21235V012021")
                .active(true)
                .build();
        storedSources.add(source);

        // Insurer publishes updated policy wording with new revision text
        String updatedPdfText = "HDFC ERGO General Insurance Co. Ltd.\n"
                + "Optima Restore Health Insurance Policy\n"
                + "IRDAI UIN: HDFHLIP21235V022026\n" // New revision code
                + "Room Rent: No Room Rent Sub-limit\n"
                + "Pre-existing Disease Waiting Period: 24 months (reduced from 36)\n";
        byte[] updatedPdfBytes = updatedPdfText.getBytes(StandardCharsets.UTF_8);
        String updatedChecksum = PolicyDocumentFetcher.computeSha256(updatedPdfBytes);

        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(updatedPdfBytes)
                        .sha256Checksum(updatedChecksum)
                        .fileSizeBytes(updatedPdfBytes.length)
                        .build());

        // Act
        String result = sourceMonitor.pollSingleSource("src-hdfc", false);

        // Assert
        assertTrue(result.startsWith("EXTRACTED_STAGED"));

        // Verify the existing verified policy was NOT overwritten in-place
        StructuredPolicy v1 = storedPolicies.stream()
                .filter(p -> "HDFHLIP21235V012021".equals(p.getIrdaUin()))
                .findFirst()
                .orElse(null);
        assertNotNull(v1);
        assertTrue(v1.isActive(), "Existing active policy must remain active until new revision is approved");

        // Verify new revision draft is staged
        StructuredPolicy v2Draft = storedPolicies.stream()
                .filter(p -> "HDFHLIP21235V022026".equals(p.getIrdaUin()))
                .findFirst()
                .orElse(null);
        assertNotNull(v2Draft, "New revision draft must be created in staged status");
        assertFalse(v2Draft.isActive(), "New revision must require reviewer verification");
        assertEquals("EXTRACTION_PENDING_REVIEW", v2Draft.getVerificationStatus());
    }

    @Test
    @DisplayName("Should gracefully handle extraction/parsing failures and report in observability metrics")
    void testFailedExtractionHandling() {
        PolicySource brokenSource = PolicySource.builder()
                .id("src-broken")
                .sourceName("Corrupted PDF Source")
                .sourceUrl("https://www.hdfcergo.com/corrupted.pdf")
                .active(true)
                .build();
        storedSources.add(brokenSource);

        when(documentFetcher.fetchDocument(eq(brokenSource.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FAILED)
                        .httpStatusCode(502)
                        .errorMessage("HTTP 502 Bad Gateway from upstream CDN")
                        .build());

        PolicySourceMonitor.CrawlSummary summary = sourceMonitor.executeTwoStageDiscoveryAndCrawl(false, false);

        assertEquals(1, summary.getFailedSources());
        assertNotNull(summary.getFailureDetails());
        assertTrue(summary.getFailureDetails().stream().anyMatch(f -> f.contains("src-broken") && f.contains("502")),
                "Failure details must record failed source id and error message");
    }

    @Test
    @DisplayName("Should dynamically discover new policy URL from an official page, stage it, and remain idempotent")
    void testDynamicPageDiscoveredUrlSafelyStagedAndIdempotent() {
        String pageDiscoveredUrl = "https://www.icicilombard.com/docs/new-elevate-plus.pdf";
        byte[] pdfBytes = ("%PDF-1.4\n"
                + "ICICI Lombard General Insurance Co. Ltd.\n"
                + "Elevate Plus Health Insurance\n"
                + "IRDAI UIN: ICIHLIP22001V012025\n"
                + "Pre-existing disease waiting period: 24 months.\n"
                + "No room rent sublimit.\n").getBytes(StandardCharsets.UTF_8);
        String pdfChecksum = PolicyDocumentFetcher.computeSha256(pdfBytes);

        when(documentFetcher.isBlockedHostOrIp("www.icicilombard.com")).thenReturn(false);
        when(documentFetcher.isPdfContent(any())).thenReturn(true);
        when(documentFetcher.fetchDocument(eq(pageDiscoveredUrl), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(pdfBytes)
                        .sha256Checksum(pdfChecksum)
                        .fileSizeBytes(pdfBytes.length)
                        .build());

        // Discover and register from dynamic URL list (e.g. extracted from page)
        PolicySourceMonitor.DiscoveryReport disc = sourceMonitor.discoverNewCandidateSources(List.of(pageDiscoveredUrl));
        assertTrue(disc.getCandidateSourcesRegistered() >= 1, "Should register dynamically discovered source");

        // Crawl and ingest
        PolicySourceMonitor.CrawlSummary crawl = sourceMonitor.executeTwoStageDiscoveryAndCrawl(false, false);
        assertTrue(crawl.getNewDocumentsExtracted() >= 1, "Should extract newly discovered document");
        assertTrue(crawl.getDraftsCreated() >= 1, "Should create draft policy");

        // Verify draft staged with active=false
        StructuredPolicy staged = storedPolicies.stream()
                .filter(p -> "ICIHLIP22001V012025".equals(p.getIrdaUin()))
                .findFirst()
                .orElse(null);
        assertNotNull(staged);
        assertFalse(staged.isActive(), "Dynamically discovered policy draft must be gated inactive");
        assertEquals("EXTRACTION_PENDING_REVIEW", staged.getVerificationStatus());

        // Repeated crawl immediately following must be idempotent (0 new extractions)
        when(documentFetcher.fetchDocument(eq(pageDiscoveredUrl), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .sha256Checksum(pdfChecksum)
                        .build());

        PolicySourceMonitor.CrawlSummary repeatedCrawl = sourceMonitor.executeTwoStageDiscoveryAndCrawl(false, false);
        assertEquals(0, repeatedCrawl.getNewDocumentsExtracted(), "Repeated crawl must produce 0 duplicate extractions");
        assertTrue(repeatedCrawl.getUnchangedDocumentsSkipped() >= 1, "Must skip unchanged document via conditional request");
    }
}
