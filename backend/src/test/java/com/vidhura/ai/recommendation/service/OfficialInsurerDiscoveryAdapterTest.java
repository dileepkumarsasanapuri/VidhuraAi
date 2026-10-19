package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test Suite for Dynamic Official Insurer Source Discovery Adapter:
 * - HTML anchor link extraction and relative URL resolution
 * - Domain allowlist and SSRF enforcement
 * - Binary PDF signature verification (%PDF-)
 * - Decoupled candidate staging and idempotency
 */
@DisplayName("OfficialInsurerDiscoveryAdapter Tests")
class OfficialInsurerDiscoveryAdapterTest {

    private PolicyDocumentFetcher documentFetcher;
    private PolicySourceRepository sourceRepository;
    private OfficialInsurerDiscoveryAdapter discoveryAdapter;

    @BeforeEach
    void setUp() {
        documentFetcher = mock(PolicyDocumentFetcher.class);
        sourceRepository = mock(PolicySourceRepository.class);
        discoveryAdapter = new OfficialInsurerDiscoveryAdapter(documentFetcher, sourceRepository);
    }

    @Test
    @DisplayName("Should extract valid policy wording PDF links from HTML page and resolve relative paths")
    void testExtractCandidateUrlsFromHtml() {
        String baseUrl = "https://www.hdfcergo.com/downloads/policy-wordings";
        String html = """
                <html>
                <body>
                    <div class="downloads-container">
                        <h2>Health Insurance Policy Wordings</h2>
                        <a href="/docs/default-source/downloads/myhealth-suraksha.pdf">My:Health Suraksha Wordings</a>
                        <a href="https://www.hdfcergo.com/docs/optima-secure.pdf">Optima Secure Wordings</a>
                        <a href="/about-us">About Us</a>
                        <a href="javascript:void(0)" class="btn">Click Here</a>
                        <a href="https://untrusted-blog.com/brochure.pdf">Third Party Blog</a>
                        <a href="/docs/critical-illness-policy-wording">Critical Illness Policy Terms</a>
                    </div>
                </body>
                </html>
                """;

        List<String> candidates = discoveryAdapter.extractCandidateUrlsFromHtml(baseUrl, html);

        assertNotNull(candidates);
        assertEquals(3, candidates.size());
        assertTrue(candidates.contains("https://www.hdfcergo.com/docs/default-source/downloads/myhealth-suraksha.pdf"));
        assertTrue(candidates.contains("https://www.hdfcergo.com/docs/optima-secure.pdf"));
        assertTrue(candidates.contains("https://www.hdfcergo.com/docs/critical-illness-policy-wording"));
        assertFalse(candidates.contains("https://www.hdfcergo.com/about-us"));
    }

    @Test
    @DisplayName("Should validate approved official domain, verify PDF magic bytes, and stage candidate")
    void testValidateAndStageCandidate_Success() {
        String candidateUrl = "https://www.starhealth.in/sites/default/files/young-star-policy-wording.pdf";
        byte[] validPdfBytes = ("%PDF-1.5\n"
                + "Star Health and Allied Insurance Co. Ltd.\n"
                + "Young Star Insurance Policy\n"
                + "IRDAI UIN: SHAHLIP21168V012021\n"
                + "Waiting period: 12 months for pre-existing disease.\n").getBytes(StandardCharsets.UTF_8);

        when(documentFetcher.isBlockedHostOrIp("www.starhealth.in")).thenReturn(false);
        when(documentFetcher.isPdfContent(any())).thenReturn(true);
        when(documentFetcher.fetchDocument(eq(candidateUrl), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(validPdfBytes)
                        .sha256Checksum("hash-young-star-123")
                        .fileSizeBytes(validPdfBytes.length)
                        .build());
        when(sourceRepository.existsBySourceUrl(candidateUrl)).thenReturn(false);

        OfficialInsurerDiscoveryAdapter.ValidationOutcome outcome =
                discoveryAdapter.validateAndStageCandidate(candidateUrl, "https://www.starhealth.in/policy-wordings");

        assertTrue(outcome.isValid());
        assertNotNull(outcome.getRegisteredSource());
        PolicySource source = outcome.getRegisteredSource();
        assertEquals("ACTIVE", source.getStatus());
        assertTrue(source.isActive());
        assertEquals("SHAHLIP21168V012021", source.getIrdaUin());
        assertEquals("Star Health and Allied Insurance Co. Ltd.", source.getProvider());
    }

    @Test
    @DisplayName("Should reject candidates from untrusted third-party domains")
    void testValidateAndStageCandidate_RejectsUntrustedDomain() {
        String candidateUrl = "https://untrusted-insurance-aggregator.xyz/fake-policy.pdf";

        OfficialInsurerDiscoveryAdapter.ValidationOutcome outcome =
                discoveryAdapter.validateAndStageCandidate(candidateUrl, "https://example.com");

        assertFalse(outcome.isValid());
        assertNotNull(outcome.getRejectionReason());
        assertTrue(outcome.getRejectionReason().contains("UNTRUSTED_DOMAIN"));
        verifyNoInteractions(documentFetcher);
    }

    @Test
    @DisplayName("Should reject candidates resolving to SSRF-blocked private IPs")
    void testValidateAndStageCandidate_RejectsSSRFPrivateIp() {
        String candidateUrl = "https://www.hdfcergo.com/internal-portal.pdf";
        when(documentFetcher.isBlockedHostOrIp("www.hdfcergo.com")).thenReturn(true);

        OfficialInsurerDiscoveryAdapter.ValidationOutcome outcome =
                discoveryAdapter.validateAndStageCandidate(candidateUrl, "https://www.hdfcergo.com");

        assertFalse(outcome.isValid());
        assertTrue(outcome.getRejectionReason().contains("SSRF_RESTRICTED_HOST"));
    }

    @Test
    @DisplayName("Should reject candidates if document does not begin with %PDF- signature")
    void testValidateAndStageCandidate_RejectsNonPdfContent() {
        String candidateUrl = "https://www.careinsurance.com/terms.html";
        byte[] htmlBytes = "<html><body>Not a PDF document</body></html>".getBytes(StandardCharsets.UTF_8);

        when(documentFetcher.isBlockedHostOrIp("www.careinsurance.com")).thenReturn(false);
        when(documentFetcher.isPdfContent(any())).thenReturn(false);
        when(documentFetcher.fetchDocument(eq(candidateUrl), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(htmlBytes)
                        .build());

        OfficialInsurerDiscoveryAdapter.ValidationOutcome outcome =
                discoveryAdapter.validateAndStageCandidate(candidateUrl, "https://www.careinsurance.com/downloads.html");

        assertFalse(outcome.isValid());
        assertTrue(outcome.getRejectionReason().contains("INVALID_DOCUMENT_SIGNATURE"));
    }

    @Test
    @DisplayName("Should reject duplicate candidates that are already registered")
    void testValidateAndStageCandidate_RejectsDuplicate() {
        String candidateUrl = "https://www.hdfcergo.com/docs/optima-secure.pdf";
        when(sourceRepository.existsBySourceUrl(candidateUrl)).thenReturn(true);

        OfficialInsurerDiscoveryAdapter.ValidationOutcome outcome =
                discoveryAdapter.validateAndStageCandidate(candidateUrl, "https://www.hdfcergo.com");

        assertFalse(outcome.isValid());
        assertTrue(outcome.getRejectionReason().contains("DUPLICATE_SOURCE"));
    }
}
