package com.vidhura.ai.recommendation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PolicyDocumentFetcher Unit Tests")
class PolicyDocumentFetcherTest {

    private PolicyDocumentFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new PolicyDocumentFetcher();
    }

    @Test
    @DisplayName("Should detect valid PDF magic bytes (%PDF-)")
    void testIsPdfContent() {
        byte[] validPdf = "%PDF-1.7\nSample content".getBytes(StandardCharsets.US_ASCII);
        assertTrue(fetcher.isPdfContent(validPdf));

        byte[] htmlContent = "<html><body>Not a PDF</body></html>".getBytes(StandardCharsets.US_ASCII);
        assertFalse(fetcher.isPdfContent(htmlContent));

        byte[] empty = new byte[0];
        assertFalse(fetcher.isPdfContent(empty));
    }

    @Test
    @DisplayName("Should compute consistent SHA-256 checksums")
    void testComputeSha256() {
        byte[] sample = "Vidhura AI Health Insurance Verification 2026".getBytes(StandardCharsets.UTF_8);
        String hash1 = PolicyDocumentFetcher.computeSha256(sample);
        String hash2 = PolicyDocumentFetcher.computeSha256(sample);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should return FAILED status for blank or malformed URL")
    void testInvalidUrl() {
        PolicyDocumentFetcher.FetchResult res1 = fetcher.fetchDocument(null, null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, res1.getStatus());
        assertEquals(400, res1.getHttpStatusCode());

        PolicyDocumentFetcher.FetchResult res2 = fetcher.fetchDocument("   ", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, res2.getStatus());
    }

    @Test
    @DisplayName("Should block SSRF attempts targeting localhost, private networks, and cloud metadata")
    void testBlockSsrfLoopbackAndMetadataIp() {
        // Loopback / localhost
        PolicyDocumentFetcher.FetchResult r1 = fetcher.fetchDocument("http://127.0.0.1/secret.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r1.getStatus());
        assertEquals(403, r1.getHttpStatusCode());
        assertTrue(r1.getErrorMessage().contains("private or restricted internal network"));

        PolicyDocumentFetcher.FetchResult r2 = fetcher.fetchDocument("http://localhost:8080/test.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r2.getStatus());
        assertEquals(403, r2.getHttpStatusCode());

        // Cloud metadata address (AWS / GCP / Azure)
        PolicyDocumentFetcher.FetchResult r3 = fetcher.fetchDocument("http://169.254.169.254/latest/meta-data", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r3.getStatus());
        assertEquals(403, r3.getHttpStatusCode());

        // Private RFC 1918 addresses
        PolicyDocumentFetcher.FetchResult r4 = fetcher.fetchDocument("http://192.168.1.50/policy.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r4.getStatus());
        assertEquals(403, r4.getHttpStatusCode());

        PolicyDocumentFetcher.FetchResult r5 = fetcher.fetchDocument("http://10.0.0.1/policy.pdf", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r5.getStatus());
        assertEquals(403, r5.getHttpStatusCode());
    }

    @Test
    @DisplayName("Should block non-HTTP/HTTPS protocols (e.g. file://, gopher://)")
    void testBlockInvalidProtocols() {
        PolicyDocumentFetcher.FetchResult r1 = fetcher.fetchDocument("file:///etc/passwd", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r1.getStatus());
        assertEquals(400, r1.getHttpStatusCode());
        assertTrue(r1.getErrorMessage().contains("only HTTP and HTTPS are permitted"));

        PolicyDocumentFetcher.FetchResult r2 = fetcher.fetchDocument("gopher://evil.com/123", null, null);
        assertEquals(PolicyDocumentFetcher.FetchStatus.FAILED, r2.getStatus());
        assertEquals(400, r2.getHttpStatusCode());
    }
}
