package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PolicySourceMonitor Tests")
class PolicySourceMonitorTest {

    private PolicySourceRepository sourceRepository;
    private DiscoveredDocumentRepository documentRepository;
    private PolicyDocumentFetcher documentFetcher;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private StructuredPolicyRepository structuredPolicyRepository;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicySourceMonitor sourceMonitor;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(PolicySourceRepository.class);
        documentRepository = mock(DiscoveredDocumentRepository.class);
        documentFetcher = mock(PolicyDocumentFetcher.class);
        structuredPolicyRepository = mock(StructuredPolicyRepository.class);
        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId("mock-policy-id-123");
            return p;
        });
        when(documentRepository.save(any(DiscoveredDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(structuredPolicyRepository, canonicalProductRegistry);

        when(documentFetcher.isPdfContent(any())).thenReturn(true);

        sourceMonitor = new PolicySourceMonitor(
                sourceRepository,
                documentRepository,
                documentFetcher,
                null, // PdfParsingService mock not needed when bytes parsed directly
                discoveryPipelineService,
                canonicalProductRegistry
        );
    }

    @Test
    @DisplayName("Should initialize default sources when repository is empty")
    void testInitDefaultSources() {
        when(sourceRepository.count()).thenReturn(0L);

        sourceMonitor.initDefaultSources();

        verify(sourceRepository, times(1)).saveAll(argThat(sources -> {
            List<PolicySource> list = (List<PolicySource>) sources;
            return list.size() >= 6 && list.stream().anyMatch(s -> "src-hdfc-optima".equals(s.getId()));
        }));
    }

    @Test
    @DisplayName("Should skip unchanged document when HTTP 304 Not Modified is returned")
    void testSkipUnchanged304() {
        PolicySource source = PolicySource.builder()
                .id("src-hdfc")
                .sourceName("HDFC Optima")
                .sourceUrl("https://example.com/optima.pdf")
                .lastEtag("\"etag-123\"")
                .active(true)
                .build();

        when(sourceRepository.findById("src-hdfc")).thenReturn(Optional.of(source));
        when(documentFetcher.fetchDocument(eq("https://example.com/optima.pdf"), eq("\"etag-123\""), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .etag("\"etag-123\"")
                        .build());

        String result = sourceMonitor.pollSingleSource("src-hdfc", false);

        assertEquals("SKIPPED_304_NOT_MODIFIED", result);
        assertEquals("SYNCED", source.getStatus());
        verify(sourceRepository, times(1)).save(source);
        verifyNoInteractions(documentRepository);
    }

    @Test
    @DisplayName("Should extract, stage draft with active=false, and archive document when new PDF is fetched")
    void testExtractNewDocument() {
        PolicySource source = PolicySource.builder()
                .id("src-star")
                .sourceName("Star Health Comprehensive Insurance Policy")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .canonicalProductName("Star Comprehensive Insurance Policy")
                .irdaUin("SHAHLIP21163V012021")
                .expectedRevision("V012021")
                .sourceUrl("https://example.com/star.pdf")
                .active(true)
                .build();

        byte[] dummyPdfBytes = ("%PDF-1.4\n"
                + "IRDAI UIN: SHAHLIP21163V012021\n"
                + "Policy Name: Star Comprehensive Insurance Policy\n"
                + "Waiting period for pre-existing disease is 36 months.\n"
                + "Single private AC room without any cap.\n"
                + "No copay applies for any treatment.").getBytes(StandardCharsets.UTF_8);

        String checksum = PolicyDocumentFetcher.computeSha256(dummyPdfBytes);

        when(sourceRepository.findById("src-star")).thenReturn(Optional.of(source));
        when(documentFetcher.fetchDocument(anyString(), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(dummyPdfBytes)
                        .sha256Checksum(checksum)
                        .fileSizeBytes(dummyPdfBytes.length)
                        .etag("\"etag-star-new\"")
                        .build());

        when(structuredPolicyRepository.findByPolicyCode(anyString())).thenReturn(Optional.empty());

        String result = sourceMonitor.pollSingleSource("src-star", false);

        assertTrue(result.startsWith("EXTRACTED_STAGED"));

        // Verify draft was saved to MongoDB with active = false
        ArgumentCaptor<StructuredPolicy> policyCaptor = ArgumentCaptor.forClass(StructuredPolicy.class);
        verify(structuredPolicyRepository, atLeastOnce()).save(policyCaptor.capture());
        StructuredPolicy savedDraft = policyCaptor.getValue();

        assertFalse(savedDraft.isActive(), "Newly discovered draft policy MUST be inactive until human review!");
        assertEquals("EXTRACTION_PENDING_REVIEW", savedDraft.getVerificationStatus());
        assertEquals("SHAHLIP21163V012021", savedDraft.getIrdaUin());

        // Verify DiscoveredDocument archive record was created
        ArgumentCaptor<DiscoveredDocument> docCaptor = ArgumentCaptor.forClass(DiscoveredDocument.class);
        verify(documentRepository, times(1)).save(docCaptor.capture());
        DiscoveredDocument archivedDoc = docCaptor.getValue();
        assertEquals(checksum, archivedDoc.getSha256Checksum());
        assertEquals("EXTRACTED_STAGED", archivedDoc.getExtractionStatus());
        assertEquals(savedDraft.getId(), archivedDoc.getExtractedPolicyId());
    }

    @Test
    @DisplayName("Should apply exponential backoff on consecutive failures and skip when backoff is active")
    void testBoundedRetriesAndExponentialBackoff() {
        PolicySource source = PolicySource.builder()
                .id("src-fail")
                .sourceName("Failing Source")
                .sourceUrl("https://example.com/fail.pdf")
                .active(true)
                .consecutiveFailures(0)
                .build();

        when(sourceRepository.findById("src-fail")).thenReturn(Optional.of(source));
        when(documentFetcher.fetchDocument(eq("https://example.com/fail.pdf"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FAILED)
                        .httpStatusCode(404)
                        .errorMessage("HTTP 404 Not Found")
                        .build());

        // First failure: consecutiveFailures = 1, backoff = 1h
        String r1 = sourceMonitor.pollSingleSource("src-fail", false);
        assertTrue(r1.contains("FAILED_FETCH"));
        assertTrue(r1.contains("NOT_FOUND_404"));
        assertEquals(1, source.getConsecutiveFailures());
        assertEquals(1, source.getBackoffHours());
        assertNotNull(source.getNextRetryEligibleAt());
        assertEquals("NOT_FOUND_404", source.getLastErrorCategory());

        // Immediate subsequent poll without force should be skipped due to active backoff
        String r2 = sourceMonitor.pollSingleSource("src-fail", false);
        assertTrue(r2.startsWith("SKIPPED_BACKOFF_ACTIVE"));

        // Force poll bypasses backoff and advances backoff multiplier
        String r3 = sourceMonitor.pollSingleSource("src-fail", true);
        assertTrue(r3.contains("FAILED_FETCH"));
        assertEquals(2, source.getConsecutiveFailures());
        assertEquals(2, source.getBackoffHours());

        // Force poll third time: consecutiveFailures = 3, backoff = 4h
        sourceMonitor.pollSingleSource("src-fail", true);
        assertEquals(3, source.getConsecutiveFailures());
        assertEquals(4, source.getBackoffHours());
    }

    @Test
    @DisplayName("Should categorize errors accurately into standard observability categories")
    void testErrorCategorization() {
        assertEquals("BOT_PROTECTION_403", PolicySourceMonitor.categorizeError(403, "Forbidden"));
        assertEquals("NOT_FOUND_404", PolicySourceMonitor.categorizeError(404, "Not Found"));
        assertEquals("MISDIRECTED_OR_BAD_421_400", PolicySourceMonitor.categorizeError(400, "Bad Request"));
        assertEquals("MISDIRECTED_OR_BAD_421_400", PolicySourceMonitor.categorizeError(421, "Misdirected Request"));
        assertEquals("SERVER_ERROR_5XX", PolicySourceMonitor.categorizeError(500, "Internal Server Error"));
        assertEquals("SERVER_ERROR_5XX", PolicySourceMonitor.categorizeError(503, "Service Unavailable"));
        assertEquals("SSRF_BLOCKED", PolicySourceMonitor.categorizeError(0, "SSRF blocked: target resolves to private IP 10.0.0.1"));
        assertEquals("TLS_SECURITY_ERROR", PolicySourceMonitor.categorizeError(0, "SSLHandshakeException: certificate name mismatch"));
        assertEquals("TIMEOUT_ERROR", PolicySourceMonitor.categorizeError(0, "SocketTimeoutException: Read timed out"));
        assertEquals("INVALID_DOCUMENT_SIGNATURE", PolicySourceMonitor.categorizeError(200, "Missing %PDF- header signature"));
    }

    @Test
    @DisplayName("Should reject document missing %PDF- header signature and mark UNKNOWN")
    void testRejectNonPdfSignature() {
        PolicySource source = PolicySource.builder()
                .id("src-json")
                .sourceName("JSON Stub Source")
                .sourceUrl("https://example.com/json.pdf")
                .active(true)
                .build();

        byte[] jsonBytes = "{\"error\": \"asset not found\"}".getBytes(StandardCharsets.UTF_8);

        when(sourceRepository.findById("src-json")).thenReturn(Optional.of(source));
        when(documentFetcher.isPdfContent(any())).thenReturn(false); // Fails magic bytes test!
        when(documentFetcher.fetchDocument(eq("https://example.com/json.pdf"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(jsonBytes)
                        .fileSizeBytes(jsonBytes.length)
                        .build());

        String result = sourceMonitor.pollSingleSource("src-json", false);

        assertTrue(result.contains("FAILED_INVALID_DOCUMENT_SIGNATURE"));
        assertEquals("INVALID_DOCUMENT_SIGNATURE", source.getLastErrorCategory());
        assertEquals("UNKNOWN", source.getDocumentClassification());
        assertEquals(1, source.getConsecutiveFailures());
    }

    @Test
    @DisplayName("Should reset backoff hours and failures to 0 on successful crawl")
    void testResetBackoffOnSuccess() {
        PolicySource source = PolicySource.builder()
                .id("src-recover")
                .sourceName("Recovering Source")
                .sourceUrl("https://example.com/recover.pdf")
                .active(true)
                .consecutiveFailures(3)
                .backoffHours(4)
                .build();

        when(sourceRepository.findById("src-recover")).thenReturn(Optional.of(source));
        when(documentFetcher.fetchDocument(eq("https://example.com/recover.pdf"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .build());

        String result = sourceMonitor.pollSingleSource("src-recover", true);

        assertEquals("SKIPPED_304_NOT_MODIFIED", result);
        assertEquals(0, source.getConsecutiveFailures());
        assertEquals(0, source.getBackoffHours());
        assertNull(source.getNextRetryEligibleAt());
        assertNull(source.getLastErrorCategory());
        assertNotNull(source.getLastSuccessfulCrawlAt());
    }
}
